package com.gstore.api.routes

import com.gstore.api.models.Role
import com.gstore.api.models.UpdateRoleRequest
import com.gstore.api.repositories.GameRepository
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.request.receive
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.put
import io.ktor.server.routing.route
import java.util.UUID

/**
 * Rotas administrativas (papel ADMIN).
 *
 *   GET /api/admin/games?status=draft  — todos os jogos, qualquer status
 *   GET /api/admin/users               — lista perfis
 *   PUT /api/admin/users/{id}/role     — altera papel de um usuário
 *
 * Base para o gerenciamento administrativo futuro.
 */
fun Route.adminRoutes() {
    route("/api/admin") {
        get("/games") {
            call.requireAdmin() ?: return@get
            val statusParam = call.request.queryParameters["status"]
            val games = call.module.gameRepository.listGames(
                GameRepository.GameFilters(
                    publishedOnly = false,
                    status = statusParam?.let { com.gstore.api.models.GameStatus.from(it) },
                    limit = 100,
                ),
            )
            call.respondData(GameListEnvelope(count = games.size, games = games))
        }

        get("/users") {
            call.requireAdmin() ?: return@get
            val users = call.module.userRepository.listAll(limit = 200)
            call.respondData(UserListEnvelope(count = users.size, users = users))
        }

        put("/users/{id}/role") {
            call.requireAdmin() ?: return@put
            val id = uuidOrNull(call.parameters["id"]) ?: return@put call.respondError(HttpStatusCode.BadRequest, "invalid_id")
            val body = try {
                call.receive<UpdateRoleRequest>()
            } catch (e: Exception) {
                return@put call.respondError(HttpStatusCode.BadRequest, "invalid_body")
            }
            try {
                val updated = call.module.userRepository.updateRole(id, body.role)
                call.respondData(updated)
            } catch (e: com.gstore.api.repositories.NotFoundException) {
                call.respondError(HttpStatusCode.NotFound, "user_not_found")
            }
        }
    }
}

@kotlinx.serialization.Serializable
data class UserListEnvelope(val ok: Boolean = true, val count: Int, val users: List<com.gstore.api.models.UserProfile>)
