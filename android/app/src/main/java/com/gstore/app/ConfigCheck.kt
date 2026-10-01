package com.gstore.app

/**
 * Verificação da configuração pública do Neon no arranque do app.
 *
 * O app Android só conhece três valores PÚBLICOS:
 *  - NEON_AUTH_URL     (ex.: https://ep-xxx.neonauth.<região>.aws.neon.tech/neondb/auth)
 *  - NEON_DATA_API_URL (ex.: https://ep-xxx.apirest.<região>.aws.neon.tech/neondb/rest/v1)
 *  - NEON_AUTH_ORIGIN  (origem de confiança registrada no Neon Auth)
 *
 * Não existe nenhuma chave secreta no app: toda a autorização é feita por
 * RLS no Postgres — cada token (anónimo ou autenticado) só acede ao que as
 * políticas permitem, mesmo que alguém extraia os endpoints do APK.
 */
object ConfigCheck {

    /**
     * Devolve a lista de valores em falta/inválidos.
     * Lista vazia = configuração OK.
     */
    fun missingValues(authUrl: String, dataApiUrl: String, origin: String): List<String> {
        val missing = mutableListOf<String>()
        if (authUrl.isBlank() || !authUrl.startsWith("https://")) {
            missing += "NEON_AUTH_URL"
        }
        if (dataApiUrl.isBlank() || !dataApiUrl.startsWith("https://")) {
            missing += "NEON_DATA_API_URL"
        }
        if (origin.isBlank() || !origin.startsWith("https://")) {
            missing += "NEON_AUTH_ORIGIN"
        }
        return missing
    }

    /** Versão que lê os valores gerados no build (BuildConfig). */
    fun fromBuild(): List<String> =
        missingValues(BuildConfig.NEON_AUTH_URL, BuildConfig.NEON_DATA_API_URL, BuildConfig.NEON_AUTH_ORIGIN)
}
