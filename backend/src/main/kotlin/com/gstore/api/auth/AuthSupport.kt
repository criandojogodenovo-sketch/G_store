package com.gstore.api.auth

import com.gstore.api.services.AppwriteService
import com.gstore.api.repositories.UserRepository

/**
 * Autenticação do backend G Store.
 *
 * O cliente Android envia `Authorization: Bearer <jwt-do-appwrite>`.
 * O JWT é validado contra o Appwrite (sem duplicar o sistema de auth),
 * e o perfil local (com role) é carregado/criado no Neon.
 */
class AuthSupport(
    val appwrite: AppwriteService,
    val users: UserRepository,
    val adminEmails: Set<String>,
    val authDisabled: Boolean,
) {
    data class Principal(val profile: com.gstore.api.models.UserProfile)

    /**
     * Resolve o usuário autenticado da requisição (ou null).
     * Se AUTH_DISABLED=true (apenas desenvolvimento local), devolve um usuário
     * admin sintético — NUNCA habilitar em produção.
     */
    fun resolveOrNull(): com.gstore.api.models.UserProfile? {
        if (authDisabled) {
            // Persiste o usuário de desenvolvimento no Neon para satisfazer
            // as chaves estrangeiras (games.developer_id etc.).
            return users.upsertFromAppwrite(
                appwriteUserId = "dev-local",
                email = "dev@localhost",
                displayName = "Desenvolvedor Local",
                avatarUrl = null,
                adminEmails = adminEmails,
            )
        }
        return null
    }
}
