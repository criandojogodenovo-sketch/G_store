package com.gstore.api.services

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap

/**
 * Integração com o Appwrite.
 *
 * Arquitetura de autenticação (sem duplicar o Appwrite):
 * - O app Android faz login/registro DIRETAMENTE no Appwrite (SDK oficial).
 * - O Appwrite emite um JWT curto (account.createJWT()).
 * - Este backend valida o JWT chamando o endpoint /account do Appwrite com o
 *   header X-Appwrite-JWT — nenhuma segunda base de usuários é criada.
 * - O perfil local (Neon) guarda apenas role/metadados, mapeado por
 *   appwrite_user_id.
 *
 * A API key do Appwrite (segredo) NUNCA é enviada ao cliente Android.
 */
class AppwriteService(
    private val endpoint: String?,
    private val projectId: String?,
    @Suppress("unused") private val apiKey: String?, // reservado p/ operações admin futuras
) {
    data class AppwriteUser(
        val id: String,
        val email: String? = null,
        val name: String? = null,
    )

    private val json = Json { ignoreUnknownKeys = true }

    private val client: HttpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10))
        .build()

    // Cache curto de validações para evitar chamadas ao Appwrite em cada request.
    private val cache = ConcurrentHashMap<String, CachedValidation>()
    private val cacheTtlMs = 60_000L

    private data class CachedValidation(val user: AppwriteUser, val at: Long)

    val configured: Boolean
        get() = !endpoint.isNullOrBlank() && !projectId.isNullOrBlank()

    /**
     * Valida um JWT do Appwrite. Retorna o usuário ou null se inválido/expirado.
     */
    fun validateJwt(jwt: String): AppwriteUser? {
        if (!configured) return null
        val cached = cache[jwt]
        if (cached != null && System.currentTimeMillis() - cached.at < cacheTtlMs) {
            return cached.user
        }
        return try {
            val request = HttpRequest.newBuilder()
                .uri(URI.create("$endpoint/account"))
                .header("X-Appwrite-JWT", jwt)
                .header("X-Appwrite-Project", projectId!!)
                .timeout(Duration.ofSeconds(10))
                .GET()
                .build()
            val response = client.send(request, HttpResponse.BodyHandlers.ofString())
            if (response.statusCode() !in 200..299) return null
            val obj: JsonObject = json.parseToJsonElement(response.body()).jsonObject
            val id = obj["\$id"]?.jsonPrimitive?.content ?: return null
            val user = AppwriteUser(
                id = id,
                email = obj["email"]?.takeIf { it.toString() != "null" }?.jsonPrimitive?.content,
                name = obj["name"]?.takeIf { it.toString() != "null" }?.jsonPrimitive?.content,
            )
            cache[jwt] = CachedValidation(user, System.currentTimeMillis())
            if (cache.size > 1000) cache.clear() // segurança de memória
            user
        } catch (e: Exception) {
            null
        }
    }
}
