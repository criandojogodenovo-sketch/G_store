package com.gstore.api.routes

import com.gstore.api.AppModule
import com.gstore.api.auth.AuthSupport
import com.gstore.api.models.ApiError
import com.gstore.api.models.Role
import com.gstore.api.models.UserProfile
import com.gstore.api.repositories.NotFoundException
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.application
import io.ktor.server.request.header
import io.ktor.server.response.respond
import io.ktor.util.AttributeKey
import kotlinx.serialization.Serializable

/**
 * Helpers compartilhados das rotas.
 */

val moduleKey = AttributeKey<AppModule>("gstore-module")

val ApplicationCall.module: AppModule
    get() = application.attributes.get(moduleKey)
        ?: attributes.get(moduleKey)
        ?: error("AppModule não configurado")

/** Envelope de resposta padrão { ok: true, data: ... }. */
@Serializable
data class Envelope<T>(val ok: Boolean = true, val data: T)

suspend inline fun <reified T> ApplicationCall.respondData(data: T, status: HttpStatusCode = HttpStatusCode.OK) {
    respond(status, Envelope(ok = true, data = data))
}

suspend fun ApplicationCall.respondError(status: HttpStatusCode, error: String, message: String? = null) {
    respond(status, ApiError(error = error, message = message))
}

/* ---------------------- Autenticação/autorização ---------------------- */

/** Usuário autenticado (ou null) — valida JWT do Appwrite e carrega o perfil local. */
fun ApplicationCall.currentUserOrNull(): UserProfile? {
    val module = this.module
    val auth = module.authSupport

    // Modo de desenvolvimento local (AUTH_DISABLED=true): usuário admin sintético.
    // NUNCA habilitar em produção.
    if (module.config.authDisabled) {
        return auth.resolveOrNull()
    }

    val header = request.header("Authorization") ?: return null
    if (!header.startsWith("Bearer ", ignoreCase = true)) return null
    val jwt = header.removePrefix("Bearer ").trim()
    if (jwt.isEmpty()) return null
    val appwriteUser = auth.appwrite.validateJwt(jwt) ?: return null
    return auth.users.upsertFromAppwrite(
        appwriteUserId = appwriteUser.id,
        email = appwriteUser.email,
        displayName = appwriteUser.name,
        avatarUrl = null,
        adminEmails = auth.adminEmails,
    )
}

/** Exige autenticação; responde 401 automaticamente caso inválida. */
suspend fun ApplicationCall.requireUser(): UserProfile? {
    val user = currentUserOrNull()
    if (user == null) respondError(HttpStatusCode.Unauthorized, "unauthorized", "Envie Authorization: Bearer <jwt do Appwrite>")
    return user
}

/** Exige DEVELOPER ou ADMIN. */
suspend fun ApplicationCall.requireDeveloper(): UserProfile? {
    val user = requireUser() ?: return null
    if (user.role !in setOf(Role.DEVELOPER, Role.ADMIN)) {
        respondError(HttpStatusCode.Forbidden, "forbidden", "Requer papel DEVELOPER")
        return null
    }
    return user
}

/** Exige ADMIN. */
suspend fun ApplicationCall.requireAdmin(): UserProfile? {
    val user = requireUser() ?: return null
    if (user.role != Role.ADMIN) {
        respondError(HttpStatusCode.Forbidden, "forbidden", "Requer papel ADMIN")
        return null
    }
    return user
}

/** Proprietário do jogo ou ADMIN. */
suspend fun ApplicationCall.requireGameOwnerOrAdmin(gameId: String): UserProfile? {
    val user = requireDeveloper() ?: return null
    val id = uuidOrNull(gameId) ?: run {
        respondError(HttpStatusCode.BadRequest, "invalid_id", "ID inválido")
        return null
    }
    val game = module.gameRepository.findById(id) ?: run {
        respondError(HttpStatusCode.NotFound, "game_not_found")
        return null
    }
    if (user.role != Role.ADMIN && game.developerId != user.id) {
        respondError(HttpStatusCode.Forbidden, "forbidden", "Somente o proprietário ou um admin pode alterar este jogo")
        return null
    }
    return user
}

fun uuidOrNull(value: String?): java.util.UUID? =
    value?.let {
        try {
            java.util.UUID.fromString(it)
        } catch (e: IllegalArgumentException) {
            null
        }
    }
