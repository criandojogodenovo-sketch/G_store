package com.gstore.api.routes

import com.gstore.api.models.CreateGameRequest
import com.gstore.api.models.GameStatus
import com.gstore.api.models.UpdateGameRequest
import com.gstore.api.repositories.GameRepository
import com.gstore.api.repositories.NotFoundException
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.request.receive
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import io.ktor.server.routing.route
import java.util.UUID

/**
 * Rotas públicas + de developer para jogos.
 *
 * Públicas (somente leitura, status published):
 *   GET /api/games
 *   GET /api/games/{slug}
 *   GET /api/games/{id}/versions
 *
 * Developer (proprietário) / Admin:
 *   POST   /api/games
 *   PUT    /api/games/{id}
 *   DELETE /api/games/{id}
 *   POST   /api/games/{id}/versions   (multipart, definido em VersionRoutes)
 *   GET    /api/games/{id}/downloads  (estatísticas, definido em VersionRoutes)
 */
fun Route.gameRoutes() {
    route("/api/games") {
        // GET /api/games — listagem pública com busca/filtro/ordenação
        get {
            val q = call.request.queryParameters
            val filters = GameRepository.GameFilters(
                publishedOnly = call.request.queryParameters["all"] != "1",
                search = q["q"],
                category = q["category"],
                limit = q["limit"]?.toIntOrNull() ?: 50,
                offset = q["offset"]?.toIntOrNull() ?: 0,
                sort = when (q["sort"]) {
                    "downloads" -> GameRepository.GameFilters.Sort.MOST_DOWNLOADED
                    "name" -> GameRepository.GameFilters.Sort.NAME
                    else -> GameRepository.GameFilters.Sort.NEWEST
                },
            )
            val games = call.module.gameRepository.listGames(filters)
            call.respondText(
                kotlinx.serialization.json.Json { encodeDefaults = true }
                    .encodeToString(GameListEnvelope.serializer(), GameListEnvelope(count = games.size, games = games)),
                io.ktor.http.ContentType.Application.Json,
            )
        }

        // GET /api/games/{slug} — detalhes de um jogo
        get("/{slug}") {
            val slug = call.parameters["slug"] ?: return@get call.respondError(HttpStatusCode.BadRequest, "invalid_slug")
            val game = call.module.gameRepository.findBySlug(slug, publishedOnly = true)
                ?: return@get call.respondError(HttpStatusCode.NotFound, "game_not_found")
            call.respondData(game)
        }

        // GET /api/games/{id}/versions — histórico de versões
        get("/{id}/versions") {
            val id = uuidOrNull(call.parameters["id"]) ?: return@get call.respondError(HttpStatusCode.BadRequest, "invalid_id")
            val game = call.module.gameRepository.findById(id)
                ?: return@get call.respondError(HttpStatusCode.NotFound, "game_not_found")
            val versions = call.module.gameRepository.listVersions(id)
            call.respondData(VersionListEnvelope(gameId = game.id, count = versions.size, versions = versions))
        }

        // GET /api/games/{id}/downloads — estatísticas de download (developer/admin)
        get("/{id}/downloads") {
            val id = uuidOrNull(call.parameters["id"]) ?: return@get call.respondError(HttpStatusCode.BadRequest, "invalid_id")
            val game = call.module.gameRepository.findById(id)
                ?: return@get call.respondError(HttpStatusCode.NotFound, "game_not_found")
            // Autorização: developer dono ou admin
            val user = call.requireDeveloper() ?: return@get
            if (user.role != com.gstore.api.models.Role.ADMIN && game.developerId != user.id) {
                return@get call.respondError(HttpStatusCode.Forbidden, "forbidden", "Somente o proprietário pode ver as estatísticas")
            }
            val stats = call.module.downloadRepository.statsForGame(id)
            call.respondData(
                DownloadStatsEnvelope(
                    gameId = game.id,
                    counter = game.downloads,
                    stats = stats,
                ),
            )
        }

        // POST /api/games — cria jogo (developer/admin)
        post {
            val user = call.requireDeveloper() ?: return@post
            val body = try {
                call.receive<CreateGameRequest>()
            } catch (e: Exception) {
                return@post call.respondError(HttpStatusCode.BadRequest, "invalid_body", "JSON inválido ou ausente")
            }
            if (body.name.isBlank()) {
                return@post call.respondError(HttpStatusCode.BadRequest, "invalid_name", "Nome obrigatório")
            }
            val game = call.module.publishService.createGame(
                name = body.name.trim(),
                description = body.description?.trim(),
                shortDescription = body.shortDescription?.trim(),
                developerId = UUID.fromString(user.id),
                developerDisplayName = user.displayName,
                categoryValue = body.category?.trim(),
                iconUrl = body.iconUrl,
                screenshots = body.screenshots,
                status = if (user.role == com.gstore.api.models.Role.ADMIN) body.status else GameStatus.DRAFT,
            )
            call.respondData(game, HttpStatusCode.Created)
        }

        // PUT /api/games/{id} — atualiza jogo (dono ou admin)
        put("/{id}") {
            val id = uuidOrNull(call.parameters["id"]) ?: return@put call.respondError(HttpStatusCode.BadRequest, "invalid_id")
            val user = call.requireGameOwnerOrAdmin(id.toString()) ?: return@put
            val body = try {
                call.receive<UpdateGameRequest>()
            } catch (e: Exception) {
                return@put call.respondError(HttpStatusCode.BadRequest, "invalid_body", "JSON inválido ou ausente")
            }
            try {
                val game = call.module.gameRepository.update(
                    id = id,
                    name = body.name?.trim()?.takeIf { it.isNotEmpty() },
                    description = body.description,
                    shortDescription = body.shortDescription,
                    developerName = body.developerName,
                    category = body.category?.let { cat ->
                        call.module.categoryRepository.findByNameOrSlugOrNull(cat)?.name ?: cat
                    },
                    iconUrl = body.iconUrl,
                    screenshots = body.screenshots,
                    status = body.status?.takeIf { user.role == com.gstore.api.models.Role.ADMIN || body.status != GameStatus.PUBLISHED || gameHasVersions(call, id) },
                )
                call.respondData(game)
            } catch (e: NotFoundException) {
                call.respondError(HttpStatusCode.NotFound, "game_not_found")
            }
        }

        // DELETE /api/games/{id} — remove jogo (dono ou admin)
        delete("/{id}") {
            val id = uuidOrNull(call.parameters["id"]) ?: return@delete call.respondError(HttpStatusCode.BadRequest, "invalid_id")
            call.requireGameOwnerOrAdmin(id.toString()) ?: return@delete
            val removed = call.module.gameRepository.delete(id)
            if (removed) call.respondData(DeletedEnvelope(ok = true, deleted = true))
            else call.respondError(HttpStatusCode.NotFound, "game_not_found")
        }
    }
}

private suspend fun gameHasVersions(call: io.ktor.server.application.ApplicationCall, id: UUID): Boolean =
    call.module.gameRepository.listVersions(id).isNotEmpty()

@kotlinx.serialization.Serializable
data class GameListEnvelope(val ok: Boolean = true, val count: Int, val games: List<com.gstore.api.models.Game>)

@kotlinx.serialization.Serializable
data class VersionListEnvelope(val ok: Boolean = true, val gameId: String, val count: Int, val versions: List<com.gstore.api.models.GameVersion>)

@kotlinx.serialization.Serializable
data class DownloadStatsEnvelope(val ok: Boolean = true, val gameId: String, val counter: Long, val stats: com.gstore.api.repositories.DownloadRepository.GameDownloadStats)

@kotlinx.serialization.Serializable
data class DeletedEnvelope(val ok: Boolean, val deleted: Boolean)
