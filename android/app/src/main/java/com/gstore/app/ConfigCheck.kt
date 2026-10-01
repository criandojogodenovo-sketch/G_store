package com.gstore.app

/**
 * Verificação da configuração pública do Appwrite no arranque do app.
 *
 * O app Android só conhece dois valores PÚBLICOS do Appwrite:
 *  - APPWRITE_ENDPOINT  (ex.: https://fra.cloud.appwrite.io/v1)
 *  - APPWRITE_PROJECT_ID (ID do projeto no Appwrite)
 *
 * A APPWRITE_API_KEY é SEGREDO e fica SOMENTE no backend — se fosse
 * embutida no APK, qualquer pessoa poderia extraí-la e administrar
 * o projeto. O app autentica-se com o SDK oficial (e-mail/senha/JWT),
 * nunca com a API key.
 */
object ConfigCheck {

    /**
     * Devolve a lista de valores em falta/inválidos.
     * Lista vazia = configuração OK.
     */
    fun missingValues(endpoint: String, projectId: String): List<String> {
        val missing = mutableListOf<String>()
        if (endpoint.isBlank() || !endpoint.startsWith("https://")) {
            missing += "APPWRITE_ENDPOINT"
        }
        if (projectId.isBlank()) {
            missing += "APPWRITE_PROJECT_ID"
        }
        return missing
    }

    /** Versão que lê os valores gerados no build (BuildConfig). */
    fun fromBuild(): List<String> =
        missingValues(BuildConfig.APPWRITE_ENDPOINT, BuildConfig.APPWRITE_PROJECT_ID)
}
