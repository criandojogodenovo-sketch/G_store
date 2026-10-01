package com.gstore.api

import com.gstore.api.config.AppConfig
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.request.forms.MultiPartFormDataContent
import io.ktor.client.request.forms.formData
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.headers
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Assumptions.assumeTrue
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * Testes de integração da API contra o banco real (Neon) e, quando
 * GITHUB_TEST_TOKEN está presente, contra o GitHub real (com limpeza).
 *
 * No CI (sem DATABASE_URL) estes testes são PULADOS automaticamente.
 * O modo AUTH_DISABLED=true é usado para exercitar os fluxos protegidos.
 */
class IntegrationApiTest {

    private val databaseUrl = System.getenv("DATABASE_URL")?.takeIf { it.startsWith("postgres") }
    private val githubToken = System.getenv("GITHUB_TEST_TOKEN")?.takeIf { it.isNotBlank() }
    private val githubRepo = System.getenv("GITHUB_TEST_REPO")?.takeIf { it.isNotBlank() }
        ?: "criandojogodenovo-sketch/G_store"

    private fun json() = Json { ignoreUnknownKeys = true }

    private fun newConfig(authDisabled: Boolean = true): AppConfig {
        assumeTrue(databaseUrl != null, "DATABASE_URL ausente — teste de integração pulado")
        return AppConfig.fromEnv(
            mapOf(
                "DATABASE_URL" to databaseUrl!!,
                "AUTH_DISABLED" to authDisabled.toString(),
                "ADMIN_EMAILS" to "dev@localhost",
                "GITHUB_TOKEN" to (githubToken ?: ""),
                "GITHUB_RELEASES_REPO" to githubRepo,
            ),
        )
    }

    private fun runApp(block: suspend (AppModule, HttpClient) -> Unit) {
        val module = AppModule(newConfig())
        try {
            testApplication {
                application { configureApp(module) }
                // Cliente de teste sem follow-redirects: o redirect 302 do download
                // aponta para o GitHub (rede externa não é acessível pelo engine de teste).
                val client = createClient { followRedirects = false }
                block(module, client)
            }
        } finally {
            module.close()
        }
    }

    private suspend fun dataOf(res: HttpResponse) =
        json().parseToJsonElement(res.bodyAsText()).jsonObject["data"]?.jsonObject
            ?: fail("campo data ausente em: ${res.bodyAsText().take(300)}")

    /* ------------------------------ Testes ------------------------------ */

    @Test
    fun `health check com verificacao de banco`() = runApp { _, client ->
        val res = client.get("http://localhost/api/health?deep=1")
        assertEquals(HttpStatusCode.OK, res.status)
        assertTrue(res.bodyAsText().contains("\"ok\":true"))
        assertTrue(res.bodyAsText().contains("\"database\":\"ok\""), "banco deve responder: ${res.bodyAsText()}")
    }

    @Test
    fun `health basa compativel com legado`() = runApp { _, client ->
        val res = client.get("http://localhost/api/health")
        assertEquals(HttpStatusCode.OK, res.status)
        assertTrue(res.bodyAsText().contains("\"service\":\"g-store-api\""))
    }

    @Test
    fun `listagem de jogos publicados funciona com dados do seed`() = runApp { _, client ->
        val res = client.get("http://localhost/api/games")
        assertEquals(HttpStatusCode.OK, res.status)
        val obj = json().parseToJsonElement(res.bodyAsText()).jsonObject
        assertEquals("true", obj["ok"]?.jsonPrimitive?.content)
        val games = obj["games"]?.jsonArray ?: fail("campo games ausente")
        assertTrue(games.isNotEmpty(), "deve existir seed de jogos")
    }

    @Test
    fun `busca de jogo por slug funciona e 404 para inexistente`() = runApp { _, client ->
        val res = client.get("http://localhost/api/games/space-runner")
        assertEquals(HttpStatusCode.OK, res.status)
        assertTrue(res.bodyAsText().contains("space-runner"))

        val res404 = client.get("http://localhost/api/games/slug-que-nao-existe-xyz")
        assertEquals(HttpStatusCode.NotFound, res404.status)
        assertTrue(res404.bodyAsText().contains("game_not_found"))
    }

    @Test
    fun `listagem e detalhe de categorias`() = runApp { _, client ->
        val res = client.get("http://localhost/api/categories")
        assertEquals(HttpStatusCode.OK, res.status)
        assertTrue(res.bodyAsText().contains("arcade"))

        val resCat = client.get("http://localhost/api/categories/arcade")
        assertEquals(HttpStatusCode.OK, resCat.status)
        assertTrue(resCat.bodyAsText().contains("Arcade"))

        val res404 = client.get("http://localhost/api/categories/inexistente-xyz")
        assertEquals(HttpStatusCode.NotFound, res404.status)
    }

    @Test
    fun `criacao de jogo sem autenticacao retorna 401`() {
        assumeTrue(databaseUrl != null, "DATABASE_URL ausente")
        val module = AppModule(newConfig(authDisabled = false))
        try {
            testApplication {
                application { configureApp(module) }
                val res = client.post("http://localhost/api/games") {
                    contentType(ContentType.Application.Json)
                    setBody("""{"name":"Jogo Teste"}""")
                }
                assertEquals(HttpStatusCode.Unauthorized, res.status, "sem token deve ser 401")
            }
        } finally {
            module.close()
        }
    }

    @Test
    fun `fluxo completo de developer - criar jogo e publicar versao`() = runApp { module, client ->
        assumeTrue(githubToken != null, "GITHUB_TEST_TOKEN ausente — publicação real pulada")
        val unique = UUID.randomUUID().toString().take(8)

        // 1. Cria jogo (draft)
        val createRes = client.post("http://localhost/api/games") {
            contentType(ContentType.Application.Json)
            setBody("""{"name":"Jogo Teste $unique","description":"Descrição teste","category":"Arcade","status":"draft"}""")
        }
        assertEquals(HttpStatusCode.Created, createRes.status, createRes.bodyAsText())
        val game = dataOf(createRes)
        val gameId = game["id"]!!.jsonPrimitive.content
        val slug = game["slug"]!!.jsonPrimitive.content

        try {
            // 2. Publica versão com APK fake (integração real com GitHub Releases)
            val apkBytes = ByteArray(128 * 1024) { (it % 251).toByte() }
            val publishRes = client.post("http://localhost/api/games/$gameId/versions") {
                setBody(
                    MultiPartFormDataContent(
                        formData {
                            append("version", "1.0.0-test")
                            append("version_code", "1")
                            append("release_notes", "Versão de teste automatizado")
                            append(
                                "apk",
                                apkBytes,
                                io.ktor.http.Headers.build {
                                    append(HttpHeaders.ContentType, "application/vnd.android.package-archive")
                                    append(HttpHeaders.ContentDisposition, "filename=\"jogo-teste.apk\"")
                                },
                            )
                        },
                    ),
                )
            }
            assertEquals(HttpStatusCode.Created, publishRes.status, publishRes.bodyAsText())
            val version = dataOf(publishRes)["version"]!!.jsonObject
            assertEquals("1.0.0-test", version["version"]!!.jsonPrimitive.content)
            assertEquals(apkBytes.size.toLong(), version["apkSizeBytes"]!!.jsonPrimitive.content.toLong())
            assertTrue(version["apkAssetId"]!!.jsonPrimitive.content.toLong() > 0)

            // 3. Jogo aparece na listagem/busca pública
            val listRes = client.get("http://localhost/api/games?q=${"Jogo Teste $unique"}")
            assertEquals(HttpStatusCode.OK, listRes.status)
            assertTrue(listRes.bodyAsText().contains(gameId), "jogo deve aparecer na busca")

            // 4. Versões listadas
            val verRes = client.get("http://localhost/api/games/$gameId/versions")
            assertEquals(HttpStatusCode.OK, verRes.status)
            assertTrue(verRes.bodyAsText().contains("1.0.0-test"))

            // 5. Download redireciona (302) para o asset do GitHub Releases
            val dlRes = client.get("http://localhost/api/games/$slug/download")
            assertEquals(HttpStatusCode.Found, dlRes.status, "esperado 302")
            assertTrue(
                dlRes.headers["Location"]?.contains("releases/download") == true,
                "location deve apontar para asset: ${dlRes.headers["Location"]}",
            )

            // 6. Estatísticas de download (dono) — 1 download contabilizado
            val statsRes = client.get("http://localhost/api/games/$gameId/downloads")
            assertEquals(HttpStatusCode.OK, statsRes.status, statsRes.bodyAsText())
            assertTrue(statsRes.bodyAsText().contains("\"counter\":1"), "contador deve ser 1")
        } finally {
            // Limpeza: remove release do GitHub e o jogo do banco
            try {
                module.githubService.getReleaseByTag("game-$slug-v1.0.0-test")?.let {
                    module.githubService.deleteRelease(it.id)
                }
            } catch (e: Exception) {
                println("[cleanup] falha ao remover release: ${e.message}")
            }
            try {
                module.gameRepository.delete(UUID.fromString(gameId))
            } catch (e: Exception) {
                println("[cleanup] falha ao remover jogo: ${e.message}")
            }
        }
    }

    @Test
    fun `multipart sem apk retorna 400`() = runApp { module, client ->
        assumeTrue(githubToken != null, "GITHUB_TEST_TOKEN ausente")
        val unique = UUID.randomUUID().toString().take(8)
        val createRes = client.post("http://localhost/api/games") {
            contentType(ContentType.Application.Json)
            setBody("""{"name":"Sem APK $unique"}""")
        }
        val gameId = dataOf(createRes)["id"]!!.jsonPrimitive.content
        try {
            val res = client.post("http://localhost/api/games/$gameId/versions") {
                setBody(
                    MultiPartFormDataContent(
                        formData {
                            append("version", "0.0.1")
                        },
                    ),
                )
            }
            assertEquals(HttpStatusCode.BadRequest, res.status)
            assertTrue(res.bodyAsText().contains("invalid_apk"))
        } finally {
            module.gameRepository.delete(UUID.fromString(gameId))
        }
    }

    @Test
    fun `json invalido retorna 400`() = runApp { _, client ->
        val res = client.post("http://localhost/api/games") {
            contentType(ContentType.Application.Json)
            setBody("""{"nome": }""")
        }
        assertEquals(HttpStatusCode.BadRequest, res.status)
    }

    @Test
    fun `rota inexistente retorna 404`() = runApp { _, client ->
        val res = client.get("http://localhost/api/rota-inexistente")
        assertEquals(HttpStatusCode.NotFound, res.status)
        assertTrue(res.bodyAsText().contains("not_found"))
    }
}
