package com.gstore.api.routes

import com.gstore.api.models.UpdateProfileRequest
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.request.receive
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.put

/**
 * Autenticação e perfil — integrados ao Appwrite SEM duplicá-lo.
 *
 * O registro/login acontecem no app Android diretamente contra o Appwrite
 * (SDK oficial). O backend apenas:
 *   - valida o JWT do Appwrite a cada requisição (Authorization: Bearer);
 *   - sincroniza/cria o perfil local (Neon) com o papel (role) correto.
 *
 *   POST /api/auth/sync        — valida JWT + cria/atualiza perfil local
 *   GET  /api/profile          — perfil do usuário autenticado
 *   PUT  /api/profile          — atualiza displayName/avatar
 *   POST /api/profile/become-developer — auto-elevação para DEVELOPER
 */
fun Route.authRoutes() {
    post("/api/auth/sync") {
        val user = call.requireUser() ?: return@post
        call.respondData(user)
    }

    get("/api/profile") {
        val user = call.requireUser() ?: return@get
        call.respondData(user)
    }

    put("/api/profile") {
        val user = call.requireUser() ?: return@put
        val body = try {
            call.receive<UpdateProfileRequest>()
        } catch (e: Exception) {
            return@put call.respondError(HttpStatusCode.BadRequest, "invalid_body")
        }
        val updated = call.module.userRepository.updateProfile(
            id = java.util.UUID.fromString(user.id),
            displayName = body.displayName?.trim()?.takeIf { it.isNotEmpty() },
            avatarUrl = body.avatarUrl,
        )
        call.respondData(updated)
    }

    post("/api/profile/become-developer") {
        val user = call.requireUser() ?: return@post
        val updated = call.module.userRepository.becomeDeveloper(java.util.UUID.fromString(user.id))
        call.respondData(updated)
    }
}
