package com.gstore.api

import com.gstore.api.services.GitHubService
import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Testes do GitHubService contra um servidor GitHub falso (JDK HttpServer).
 * Valida: busca por tag, criação de release, upload de asset em streaming
 * e exclusão de release.
 */
class GitHubServiceTest {

    private lateinit var server: HttpServer
    private var port: Int = 0
    private val releases = mutableMapOf<Long, FakeRelease>()
    private var nextReleaseId = 100L
    private var uploadedBytes = 0L
    private var uploadedContentType: String? = null

    data class FakeRelease(val id: Long, val tagName: String, val name: String)

    @BeforeTest
    fun setUp() {
        uploadedBytes = 0
        uploadedContentType = null
        server = HttpServer.create(InetSocketAddress(0), 0)

        server.createContext("/repos/fake-owner/fake-repo/releases/tags") { exchange ->
            val tag = exchange.requestURI.path.substringAfterLast("/")
            val release = releases.values.firstOrNull { it.tagName == tag }
            respondJson(exchange, if (release == null) 404 else 200, if (release == null) {
                """{"message":"Not Found"}"""
            } else {
                releaseJson(release)
            })
        }

        server.createContext("/repos/fake-owner/fake-repo/releases") { exchange ->
            val path = exchange.requestURI.path
            when {
                path == "/repos/fake-owner/fake-repo/releases" && exchange.requestMethod == "POST" -> {
                    val body = exchange.requestBody.readAllBytes().decodeToString()
                    val tag = Regex("\"tag_name\":\"([^\"]+)\"").find(body)?.groupValues?.get(1) ?: "v0"
                    val name = Regex("\"name\":\"([^\"]+)\"").find(body)?.groupValues?.get(1) ?: tag
                    val release = FakeRelease(nextReleaseId++, tag, name)
                    releases[release.id] = release
                    respondJson(exchange, 201, releaseJson(release))
                }
                path.matches(Regex("/repos/fake-owner/fake-repo/releases/\\d+/assets")) && exchange.requestMethod == "PUT" -> {
                    val releaseId = path.substringAfter("releases/").substringBefore("/assets").toLongOrNull()
                    if (!releases.containsKey(releaseId)) {
                        respondJson(exchange, 404, """{"message":"release not found"}""")
                        return@createContext
                    }
                    // Upload de asset (PUT) — consome o corpo por streaming
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        val read = exchange.requestBody.read(buffer)
                        if (read <= 0) break
                        uploadedBytes += read
                    }
                    uploadedContentType = exchange.requestHeaders.getFirst("Content-Type")
                    val name = exchange.requestURI.query.substringAfter("name=")
                    respondJson(
                        exchange,
                        201,
                        """{"id":777,"name":"$name","size":$uploadedBytes,"browser_download_url":"http://dl.example/$name"}""",
                    )
                }
                path.matches(Regex("/repos/fake-owner/fake-repo/releases/\\d+")) && exchange.requestMethod == "DELETE" -> {
                    val id = path.substringAfterLast("/").toLongOrNull()
                    releases.remove(id)
                    respondJson(exchange, 204, "")
                }
                else -> respondJson(exchange, 405, """{"message":"method"}""")
            }
        }

        server.start()
        port = server.address.port
    }

    @AfterTest
    fun tearDown() {
        server.stop(0)
    }

    private fun releaseJson(release: FakeRelease) =
        """{"id":${release.id},"tag_name":"${release.tagName}","name":"${release.name}",""" +
            """"html_url":"http://example/release/${release.id}",""" +
            """"upload_url":"http://localhost:$port/repos/fake-owner/fake-repo/releases/${release.id}/assets{?name,label}"}"""

    private fun respondJson(exchange: com.sun.net.httpserver.HttpExchange, status: Int, body: String) {
        val bytes = body.toByteArray()
        if (status == 204) {
            exchange.sendResponseHeaders(204, -1)
        } else {
            exchange.responseHeaders.add("Content-Type", "application/json")
            exchange.sendResponseHeaders(status, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
    }

    private fun newService() = GitHubService(
        token = "token-teste-fake",
        repository = "fake-owner/fake-repo",
        apiBaseUrl = "http://localhost:$port",
        uploadsBaseUrl = "http://localhost:$port",
    )

    @Test
    fun `release inexistente retorna null`() {
        assertNull(newService().getReleaseByTag("nao-existe"))
    }

    @Test
    fun `cria release, faz upload em streaming e recupera por tag`() {
        val service = newService()

        assertNull(service.getReleaseByTag("v9.9.9-test"))

        val release = service.createRelease("v9.9.9-test", "Release Teste", "notas")
        assertEquals("v9.9.9-test", release.tagName)
        assertTrue(release.id > 0)

        // Arquivo de 1 MB para validar streaming (não caberia em um único chunk)
        val apk = Files.createTempFile("test", ".apk")
        val payload = ByteArray(1024 * 1024) { (it % 251).toByte() }
        Files.write(apk, payload)

        val asset = service.uploadAsset(release, "jogo-9.9.9.apk", "application/vnd.android.package-archive", apk)
        assertEquals(777L, asset.id)
        assertEquals(payload.size.toLong(), uploadedBytes, "bytes recebidos = bytes do arquivo")
        assertEquals("application/vnd.android.package-archive", uploadedContentType)
        assertEquals(payload.size.toLong(), asset.size)
        assertEquals("http://dl.example/jogo-9.9.9.apk", asset.browserDownloadUrl)

        val found = service.getReleaseByTag("v9.9.9-test")
        assertNotNull(found)
        assertEquals(release.id, found.id)

        Files.deleteIfExists(apk)
    }

    @Test
    fun `upload com erro HTTP lança GitHubException`() {
        val service = newService()
        val release = service.createRelease("v-error", "x", "y")
        val file = Files.createTempFile("t", ".apk")
        Files.write(file, byteArrayOf(1, 2, 3))
        // Contexto de assets exige PUT; sobrescrevemos: usar release inexistente provoca 201...
        // Para testar erro, apontamos para um serviço com repositório inválido.
        val badService = GitHubService(
            token = null,
            repository = "fake-owner/fake-repo",
            apiBaseUrl = "http://localhost:${port + 1}", // porta sem servidor
            uploadsBaseUrl = "http://localhost:$port",
        )
        assertFailsWith<Exception> { badService.getReleaseByTag("x") }
        assertFailsWith<java.io.IOException> { service.uploadAsset(release.copy(id = 999999), "a.apk", "app", file) }
        Files.deleteIfExists(file)
    }
}
