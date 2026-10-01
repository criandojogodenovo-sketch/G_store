package com.gstore.api.services

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import java.io.IOException
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Path
import java.time.Duration

/**
 * Camada de integração com a API do GitHub (Releases).
 *
 * Responsabilidades:
 * - Criar/localizar releases por tag;
 * - Fazer upload de assets (APK, ícone, screenshots) em STREAMING a partir de
 *   arquivos temporários — o binário nunca é carregado inteiro na memória;
 * - Fornecer a URL pública de download do asset.
 *
 * O token do GitHub vem exclusivamente de variável de ambiente (GITHUB_TOKEN)
 * e NUNCA é registrado em logs, retornado pela API ou enviado ao app Android.
 */
class GitHubService(
    private val token: String?,
    private val repository: String, // "owner/repo"
    private val apiBaseUrl: String = "https://api.github.com",
    private val uploadsBaseUrl: String = "https://uploads.github.com",
) {
    data class ReleaseInfo(
        val id: Long,
        val tagName: String,
        val name: String?,
        val htmlUrl: String?,
        val uploadUrl: String,
    )

    data class AssetInfo(
        val id: Long,
        val name: String,
        val size: Long,
        val browserDownloadUrl: String,
    )

    class GitHubException(val statusCode: Int, message: String) : IOException("github_http_$statusCode: $message")

    val configured: Boolean get() = !token.isNullOrBlank()

    private val client: HttpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(15))
        .followRedirects(HttpClient.Redirect.NORMAL)
        .build()

    private fun apiBase() = "$apiBaseUrl/repos/$repository"

    private fun newBuilder(): HttpRequest.Builder {
        val b = HttpRequest.newBuilder()
            .timeout(Duration.ofSeconds(60))
            .header("Accept", "application/vnd.github+json")
            .header("X-GitHub-Api-Version", "2022-11-28")
            .header("User-Agent", "g-store-api")
        if (!token.isNullOrBlank()) {
            b.header("Authorization", "Bearer $token")
        }
        return b
    }

    /** Busca release por tag; retorna null se não existir (404). */
    fun getReleaseByTag(tag: String): ReleaseInfo? {
        val request = newBuilder()
            .uri(URI.create("${apiBase()}/releases/tags/${urlEncode(tag)}"))
            .GET()
            .build()
        val response = client.send(request, HttpResponse.BodyHandlers.ofString())
        if (response.statusCode() == 404) return null
        if (response.statusCode() !in 200..299) throw GitHubException(response.statusCode(), shortError(response.body()))
        val obj = JsonHolder.json.parseToJsonElement(response.body()).jsonObjectSafe()
        return ReleaseInfo(
            id = obj.number("id"),
            tagName = obj.string("tag_name") ?: tag,
            name = obj.string("name"),
            htmlUrl = obj.string("html_url"),
            uploadUrl = obj.string("upload_url") ?: "$uploadsBaseUrl/repos/$repository/releases/${obj.number("id")}/assets",
        )
    }

    /** Cria uma release (a tag é criada no branch padrão caso não exista). */
    fun createRelease(tag: String, name: String, body: String): ReleaseInfo {
        val payload = JsonHolder.json.encodeToString(
            SerializableRelease(tag, name, body),
        )
        val request = newBuilder()
            .uri(URI.create("${apiBase()}/releases"))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(payload))
            .build()
        val response = client.send(request, HttpResponse.BodyHandlers.ofString())
        if (response.statusCode() !in 200..299) {
            throw GitHubException(response.statusCode(), shortError(response.body()))
        }
        val obj = JsonHolder.json.parseToJsonElement(response.body()).jsonObjectSafe()
        return ReleaseInfo(
            id = obj.number("id"),
            tagName = obj.string("tag_name") ?: tag,
            name = obj.string("name") ?: name,
            htmlUrl = obj.string("html_url"),
            uploadUrl = obj.string("upload_url") ?: "$uploadsBaseUrl/repos/$repository/releases/${obj.number("id")}/assets",
        )
    }

    fun getOrCreateRelease(tag: String, name: String, body: String): ReleaseInfo =
        getReleaseByTag(tag) ?: createRelease(tag, name, body)

    /**
     * Envia um asset para a release em STREAMING (a partir do arquivo em disco).
     * O corpo da requisição lê o arquivo por stream — sem carregar na memória.
     */
    fun uploadAsset(release: ReleaseInfo, fileName: String, contentType: String, file: Path): AssetInfo {
        val size = java.nio.file.Files.size(file)
        val url = "$uploadsBaseUrl/repos/$repository/releases/${release.id}/assets?name=${urlEncode(fileName)}"
        val request = newBuilder()
            .uri(URI.create(url))
            .header("Content-Type", contentType)
            // Content-Length é definido automaticamente pelo BodyPublishers.ofFile
            .PUT(HttpRequest.BodyPublishers.ofFile(file))
            .build()
        val response = client.send(request, HttpResponse.BodyHandlers.ofString())
        if (response.statusCode() !in 200..299) {
            throw GitHubException(response.statusCode(), shortError(response.body()))
        }
        val obj = JsonHolder.json.parseToJsonElement(response.body()).jsonObjectSafe()
        return AssetInfo(
            id = obj.number("id"),
            name = obj.string("name") ?: fileName,
            size = obj.number("size"),
            browserDownloadUrl = obj.string("browser_download_url")
                ?: "$apiBaseUrl/$repository/releases/download/${release.tagName}/$fileName",
        )
    }

    /** Deleta uma release (usado apenas por testes de integração para limpeza). */
    fun deleteRelease(releaseId: Long) {
        val request = newBuilder()
            .uri(URI.create("${apiBase()}/releases/$releaseId"))
            .method("DELETE", HttpRequest.BodyPublishers.noBody())
            .build()
        val response = client.send(request, HttpResponse.BodyHandlers.ofString())
        if (response.statusCode() !in 200..299 && response.statusCode() != 404) {
            throw GitHubException(response.statusCode(), shortError(response.body()))
        }
    }

    /** Abre um stream do asset usando a API (necessário para repositório privado). */
    fun openAssetStream(assetId: Long): java.io.InputStream {
        val request = newBuilder()
            .uri(URI.create("${apiBase()}/releases/assets/$assetId"))
            .header("Accept", "application/octet-stream")
            .GET()
            .build()
        val response = client.send(request, HttpResponse.BodyHandlers.ofInputStream())
        if (response.statusCode() !in 200..299) {
            response.body().use { it.close() }
            throw GitHubException(response.statusCode(), "asset_download_failed")
        }
        return response.body()
    }

    private fun shortError(body: String): String =
        try {
            val obj = JsonHolder.json.parseToJsonElement(body).jsonObjectSafe()
            obj.string("message")?.take(120) ?: "unknown_error"
        } catch (e: Exception) {
            "http_error"
        }

    private fun urlEncode(value: String): String =
        java.net.URLEncoder.encode(value, Charsets.UTF_8)
}

private object JsonHolder {
    val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
}

@kotlinx.serialization.Serializable
private data class SerializableRelease(
    val tag_name: String,
    val name: String,
    val body: String,
)

private fun kotlinx.serialization.json.JsonElement.jsonObjectSafe(): kotlinx.serialization.json.JsonObject =
    this as kotlinx.serialization.json.JsonObject

private fun kotlinx.serialization.json.JsonObject.string(key: String): String? =
    (this[key] as? kotlinx.serialization.json.JsonPrimitive)?.let {
        if (it.isString || it.content != "null") it.content else null
    }

private fun kotlinx.serialization.json.JsonObject.number(key: String): Long =
    (this[key] as? kotlinx.serialization.json.JsonPrimitive)?.content?.toLongOrNull() ?: 0L
