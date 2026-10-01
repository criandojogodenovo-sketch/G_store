package com.gstore.api.config

/**
 * Configuração da aplicação carregada exclusivamente de variáveis de ambiente.
 *
 * REGRA DE SEGURANÇA: nenhuma credencial pode ficar em código ou em arquivo
 * versionado. Tudo aqui vem do ambiente (env vars / secrets).
 */
data class AppConfig(
    val port: Int,
    val databaseUrl: String,
    // Appwrite (autenticação)
    val appwriteEndpoint: String?,
    val appwriteProjectId: String?,
    val appwriteApiKey: String?,
    // GitHub (hospedagem de APKs via Releases)
    val githubToken: String?,
    val githubReleasesRepo: String,
    val githubReleasesPrivate: Boolean,
    // Operacional
    val adminEmails: Set<String>,
    val maxUploadMb: Long,
    val authDisabled: Boolean,
) {
    companion object {
        fun fromEnv(env: Map<String, String> = System.getenv()): AppConfig {
            val databaseUrl = env["DATABASE_URL"]?.trim().orEmpty()
            require(databaseUrl.startsWith("postgres")) {
                "DATABASE_URL não configurada (use variável de ambiente, nunca arquivo versionado)"
            }
            return AppConfig(
                port = env["PORT"]?.toIntOrNull() ?: 8080,
                databaseUrl = databaseUrl,
                appwriteEndpoint = env["APPWRITE_ENDPOINT"]?.trim()?.takeIf { it.isNotEmpty() },
                appwriteProjectId = env["APPWRITE_PROJECT_ID"]?.trim()?.takeIf { it.isNotEmpty() },
                appwriteApiKey = env["APPWRITE_API_KEY"]?.trim()?.takeIf { it.isNotEmpty() },
                githubToken = env["GITHUB_TOKEN"]?.trim()?.takeIf { it.isNotEmpty() },
                githubReleasesRepo = env["GITHUB_RELEASES_REPO"]?.trim()?.takeIf { it.isNotEmpty() }
                    ?: "criandojogodenovo-sketch/G_store",
                githubReleasesPrivate = env["GITHUB_RELEASES_PRIVATE"] == "true",
                adminEmails = env["ADMIN_EMAILS"]?.split(',')?.map { it.trim().lowercase() }?.filter { it.isNotEmpty() }?.toSet()
                    ?: emptySet(),
                maxUploadMb = env["MAX_UPLOAD_MB"]?.toLongOrNull() ?: 200L,
                authDisabled = env["AUTH_DISABLED"] == "true",
            )
        }
    }
}
