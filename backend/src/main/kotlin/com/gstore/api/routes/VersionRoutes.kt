package com.gstore.api.routes

import com.gstore.api.services.PublishService
import com.gstore.api.util.Slug
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.MultiPartData
import io.ktor.http.content.PartData
import io.ktor.http.content.forEachPart
import io.ktor.server.application.call
import io.ktor.server.request.contentType
import io.ktor.server.request.receiveMultipart
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import io.ktor.utils.io.jvm.javaio.toInputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.UUID

/**
 * POST /api/games/{id}/versions — publica uma nova versão do jogo.
 *
 * Espera multipart/form-data com:
 *   - version       (texto, obrigatório) — ex: 1.2.0
 *   - version_code  (texto, opcional)    — ex: 12
 *   - release_notes (texto, opcional)
 *   - publish       (texto, opcional, "true" por padrão)
 *   - apk           (arquivo, obrigatório, até MAX_UPLOAD_MB)
 *   - icon          (arquivo, opcional — imagem)
 *   - screenshots   (arquivos, opcionais — imagens)
 *
 * O APK é gravado em arquivo temporário (streaming, sem carregar na memória),
 * enviado ao GitHub Releases em streaming e apagado ao final.
 */
fun Route.versionRoutes() {
    route("/api/games/{id}/versions") {
        post {
            val user = call.requireDeveloper() ?: return@post
            val id = uuidOrNull(call.parameters["id"]) ?: return@post call.respondError(HttpStatusCode.BadRequest, "invalid_id")
            val game = call.module.gameRepository.findById(id)
                ?: return@post call.respondError(HttpStatusCode.NotFound, "game_not_found")
            if (call.module.config.authDisabled.not() && user.role != com.gstore.api.models.Role.ADMIN && game.developerId != user.id) {
                return@post call.respondError(HttpStatusCode.Forbidden, "forbidden", "Somente o proprietário pode publicar versões")
            }
            if (!call.request.contentType().match(io.ktor.http.ContentType.MultiPart.FormData)) {
                return@post call.respondError(HttpStatusCode.BadRequest, "invalid_content_type", "Use multipart/form-data")
            }
            if (!call.module.githubService.configured) {
                return@post call.respondError(
                    HttpStatusCode.ServiceUnavailable,
                    "github_not_configured",
                    "GITHUB_TOKEN não configurado no backend (variável de ambiente)",
                )
            }

            val maxBytes = call.module.config.maxUploadMb * 1024L * 1024L
            val tempDir = Files.createTempDirectory("gstore-upload")
            var apkFile: PublishService.UploadedFile? = null
            var iconFile: PublishService.UploadedFile? = null
            val screenshots = mutableListOf<PublishService.UploadedFile>()
            var version: String? = null
            var versionCode: Long? = null
            var releaseNotes: String? = null
            var publish = true

            val multipart: MultiPartData = call.receiveMultipart(formFieldLimit = 2L * 1024L * 1024L)
            try {
                multipart.forEachPart { part ->
                    try {
                        when (part) {
                            is PartData.FormItem -> {
                                when (part.name) {
                                    "version" -> version = part.value.trim()
                                    "version_code" -> versionCode = part.value.trim().toLongOrNull()
                                    "release_notes" -> releaseNotes = part.value.trim().takeIf { it.isNotEmpty() }
                                    "publish" -> publish = part.value.trim().lowercase() != "false"
                                }
                            }
                            is PartData.FileItem -> {
                                val originalName = part.originalFileName ?: "arquivo.bin"
                                val lower = originalName.lowercase()
                                val target: Path
                                val isApk = lower.endsWith(".apk") && part.name == "apk"
                                val isIcon = part.name == "icon"
                                val isShot = part.name == "screenshots"
                                if (!isApk && !isIcon && !isShot) return@forEachPart
                                target = Files.createTempFile(tempDir, "part-", "-$originalName")
                                // Streaming: copia o canal do multipart direto para o disco
                                // (o APK nunca é carregado inteiro na memória).
                                val channel = part.provider()
                                withContext(Dispatchers.IO) {
                                    channel.toInputStream().use { input ->
                                        Files.copy(input, target, StandardCopyOption.REPLACE_EXISTING)
                                    }
                                }
                                val size = Files.size(target)
                                if (isApk) {
                                    if (size > maxBytes) error("apk_too_large")
                                    if (!lower.endsWith(".apk")) error("apk_invalid_extension")
                                    apkFile = PublishService.UploadedFile(target, originalName, "application/vnd.android.package-archive", size)
                                } else if (isIcon) {
                                    if (size > 10 * 1024L * 1024L) error("icon_too_large")
                                    iconFile = PublishService.UploadedFile(target, originalName, guessImageType(originalName), size)
                                } else {
                                    if (size > 10 * 1024L * 1024L) error("screenshot_too_large")
                                    if (screenshots.size >= 8) error("too_many_screenshots")
                                    screenshots.add(PublishService.UploadedFile(target, originalName, guessImageType(originalName), size))
                                }
                            }
                            else -> Unit
                        }
                    } finally {
                        part.dispose()
                    }
                }

                val v = version
                val apk = apkFile
                if (v.isNullOrBlank()) {
                    return@post call.respondError(HttpStatusCode.BadRequest, "invalid_version", "Campo 'version' é obrigatório")
                }
                if (apk == null) {
                    return@post call.respondError(HttpStatusCode.BadRequest, "invalid_apk", "Envie o arquivo .apk no campo 'apk'")
                }

                val result = call.module.publishService.publishVersion(
                    PublishService.PublishVersionInput(
                        gameId = id,
                        developerId = UUID.fromString(user.id),
                        version = v,
                        versionCode = versionCode,
                        releaseNotes = releaseNotes,
                        apk = apk,
                        icon = iconFile,
                        screenshots = screenshots,
                        publish = publish,
                    ),
                )
                call.respondData(
                    PublishEnvelope(ok = true, game = result.first, version = result.second),
                    HttpStatusCode.Created,
                )
            } catch (e: IllegalStateException) {
                call.respondError(HttpStatusCode.BadRequest, e.message ?: "invalid_upload")
            } catch (e: Exception) {
                if (e is com.gstore.api.services.GitHubService.GitHubException) {
                    call.respondError(HttpStatusCode.BadGateway, "github_error", "Falha ao interagir com o GitHub Releases (${e.statusCode})")
                } else {
                    throw e
                }
            } finally {
                // Limpeza dos temporários (APK nunca persiste no servidor).
                Files.walk(tempDir).sorted(java.util.Comparator.reverseOrder()).forEach { Files.deleteIfExists(it) }
            }
        }
    }
}

private fun guessImageType(name: String): String {
    val ext = name.substringAfterLast('.', "").lowercase()
    return when (ext) {
        "png" -> "image/png"
        "jpg", "jpeg" -> "image/jpeg"
        "webp" -> "image/webp"
        else -> "application/octet-stream"
    }
}

@kotlinx.serialization.Serializable
data class PublishEnvelope(val ok: Boolean, val game: com.gstore.api.models.Game, val version: com.gstore.api.models.GameVersion)
