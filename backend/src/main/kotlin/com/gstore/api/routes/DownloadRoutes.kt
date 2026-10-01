package com.gstore.api.routes

import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.response.header
import io.ktor.server.response.respondBytesWriter
import io.ktor.server.response.respondRedirect
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.utils.io.writeFully
import java.util.UUID

/**
 * GET /api/games/{slug}/download — distribuição do APK.
 *
 * Estratégia (a mesma do Worker legado, preservada):
 * - Localiza a versão mais recente do jogo publicado;
 * - Incrementa o contador e registra o download;
 * - REDIRECIONA (302) o cliente para o asset no GitHub Releases — o APK
 *   NUNCA passa pela API (sem armazenamento, sem buffer de memória).
 *
 * Em repositório privado (GITHUB_RELEASES_PRIVATE=true), a API faz stream
 * do asset usando o token server-side (o cliente nunca vê o token).
 */
fun Route.downloadRoutes() {
    get("/api/games/{slug}/download") {
        val slug = call.parameters["slug"] ?: return@get call.respondError(HttpStatusCode.BadRequest, "invalid_slug")
        val game = call.module.gameRepository.findBySlug(slug, publishedOnly = true)
            ?: return@get call.respondError(HttpStatusCode.NotFound, "game_not_found")

        val latest = call.module.gameRepository.findLatestVersion(UUID.fromString(game.id))
            ?: return@get call.respondError(HttpStatusCode.NotFound, "download_not_found")

        // Registro do download (contador legado + tabela analítica).
        val userId = call.currentUserOrNull()?.id?.let { uuidOrNull(it) }
        try {
            call.module.gameRepository.incrementDownloads(UUID.fromString(game.id))
            call.module.downloadRepository.record(UUID.fromString(game.id), UUID.fromString(latest.id), userId)
        } catch (e: Exception) {
            // Falha de analytics não deve impedir o download.
        }

        // Modo repositório privado: streaming server-side com o token (que nunca vaza).
        if (call.module.config.githubReleasesPrivate && latest.apkAssetId != null && call.module.githubService.configured) {
            val fileName = latest.apkFileName ?: "${game.slug}-${latest.version}.apk"
            call.response.header(HttpHeaders.ContentDisposition, "attachment; filename=\"$fileName\"")
            call.respondBytesWriter(contentType = ContentType.parse("application/vnd.android.package-archive")) {
                call.module.githubService.openAssetStream(latest.apkAssetId!!).use { input ->
                    // Streaming em blocos de 256 KB — o APK nunca fica inteiro na memória.
                    val buffer = ByteArray(256 * 1024)
                    while (true) {
                        val read = input.read(buffer)
                        if (read <= 0) break
                        writeFully(buffer, 0, read)
                    }
                }
            }
            return@get
        }

        // Modo padrão (repositório público): redirect 302 direto ao asset.
        call.response.header("X-G-Store-Version", latest.version)
        call.respondRedirect(latest.apkUrl, permanent = false)
    }
}
