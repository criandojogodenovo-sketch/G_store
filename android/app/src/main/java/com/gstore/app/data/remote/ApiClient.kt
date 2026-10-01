package com.gstore.app.data.remote

import com.gstore.app.BuildConfig
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Response
import retrofit2.Response as RetrofitResponse
import retrofit2.Retrofit
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Headers
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.QueryMap
import retrofit2.http.Query
import java.util.concurrent.TimeUnit
import android.util.Base64

/**
 * Cliente HTTP da G Store para o Neon.
 *
 * Dois serviços, ambos PÚBLICOS (sem chaves secretas):
 *  - NeonAuthApi — login/registo/logout/sessão (Better Auth hospedado)
 *  - NeonDataApi — catálogo, biblioteca, reviews… (PostgREST sobre o
 *    Postgres, protegido por RLS)
 *
 * Autorização: a Data API exige um JWT em TODOS os pedidos.
 *  - Utilizador com sessão → JWT de 15 min (header `set-auth-jwt`)
 *  - Sem sessão → token anónimo público de 1 h (`/token/anonymous`)
 *
 * Os interceptores leem as variáveis voláteis abaixo, mantidas atualizadas
 * pelo GStoreRepository (que as refresca antes de expirar). Desta forma
 * nenhum interceptor precisa de bloquear uma thread à espera de I/O.
 */
object ApiClient {

    /** Cookie de sessão atual (token.assinatura) — atualizado no login/logout. */
    @Volatile var sessionCookie: String? = null

    /** JWT corrente para a Data API (do utilizador ou anónimo). */
    @Volatile var dataToken: String? = null

    val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        isLenient = true
        explicitNulls = false
    }

    /** Origin de confiança registada no Neon Auth (ver ConfigCheck). */
    private val origin: String get() = BuildConfig.NEON_AUTH_ORIGIN

    /* ---------------------- OkHttp ---------------------- */

    private fun baseClient(): OkHttpClient.Builder = OkHttpClient.Builder()
        // Rede móvel lenta: limites generosos + reenvio automático de
        // ligações interrompidas (retryOnConnectionFailure já vem ligado).
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .callTimeout(60, TimeUnit.SECONDS)

    /* ---------------------- Neon Auth API ---------------------- */

    private val authOkHttp: OkHttpClient by lazy {
        baseClient()
            .addInterceptor { chain ->
                val req = chain.request().newBuilder()
                    .header("Origin", origin)
                    .header("Accept", "application/json")
                    .build()
                chain.proceed(req)
            }
            .addInterceptor { chain ->
                val cookie = sessionCookie
                val req = if (cookie.isNullOrBlank()) {
                    chain.request()
                } else {
                    chain.request().newBuilder()
                        .header("Cookie", cookie)
                        .build()
                }
                chain.proceed(req)
            }
            .build()
    }

    private val authRetrofit: Retrofit by lazy {
        Retrofit.Builder()
            .baseUrl(BuildConfig.NEON_AUTH_URL.trimEnd('/') + "/")
            .client(authOkHttp)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
    }

    val authApi: NeonAuthApi by lazy { authRetrofit.create(NeonAuthApi::class.java) }

    /* ---------------------- Neon Data API ---------------------- */

    private val dataOkHttp: OkHttpClient by lazy {
        baseClient()
            .addInterceptor { chain ->
                val token = dataToken
                val req = if (token.isNullOrBlank()) {
                    chain.request()
                } else {
                    chain.request().newBuilder()
                        .header("Authorization", "Bearer $token")
                        .build()
                }
                chain.proceed(req)
            }
            .build()
    }

    private val dataRetrofit: Retrofit by lazy {
        Retrofit.Builder()
            .baseUrl(BuildConfig.NEON_DATA_API_URL.trimEnd('/') + "/")
            .client(dataOkHttp)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
    }

    val dataApi: NeonDataApi by lazy { dataRetrofit.create(NeonDataApi::class.java) }

    /* ---------------------- Utilitários ---------------------- */

    /** Extrai o valor do cookie `__Secure-neon-auth.session_token` da resposta. */
    fun extractSessionCookie(response: RetrofitResponse<*>): String? {
        val setCookie = response.headers().values("Set-Cookie")
        for (header in setCookie) {
            val name = "__Secure-neon-auth.session_token="
            val idx = header.indexOf(name)
            if (idx >= 0) {
                val rest = header.substring(idx + name.length)
                return rest.substringBefore(';').trim().ifBlank { null }
            }
        }
        return null
    }

    /** Lê o claim `exp` de um JWT (payload base64url) sem validar assinatura. */
    fun jwtExpiry(token: String): Long? = runCatching {
        val parts = token.split(".")
        if (parts.size < 2) return@runCatching null
        val payload = String(Base64.decode(parts[1], Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP))
        val obj = json.parseToJsonElement(payload) as? kotlinx.serialization.json.JsonObject
            ?: return@runCatching null
        (obj["exp"] as? kotlinx.serialization.json.JsonPrimitive)?.content?.toLongOrNull()
    }.getOrNull()
}

/* ════════════════════ Interfaces Retrofit ════════════════════ */

interface NeonAuthApi {

    @POST("sign-up/email")
    suspend fun signUp(@Body body: SignUpBody): RetrofitResponse<AuthResponse>

    @POST("sign-in/email")
    suspend fun signIn(@Body body: SignInBody): RetrofitResponse<AuthResponse>

    @POST("sign-out")
    suspend fun signOut(@Body body: EmptyBody = EmptyBody()): RetrofitResponse<EmptyBody>

    /** Devolve a sessão atual; o JWT vem no header `set-auth-jwt`. */
    @GET("get-session")
    suspend fun getSession(): RetrofitResponse<SessionResponse?>

    /** Token público para ler o catálogo sem login (role anonymous, 1 h). */
    @GET("token/anonymous")
    suspend fun anonymousToken(): AnonymousTokenResponse

    @POST("update-user")
    suspend fun updateUser(@Body body: UpdateUserBody): RetrofitResponse<AuthUser>
}

interface NeonDataApi {

    /* ---------- Catálogo (leitura pública) ---------- */

    @Headers("Prefer: return=representation")
    @GET("games")
    suspend fun listGames(@QueryMap query: Map<String, String>): List<GameRow>

    @Headers("Prefer: return=representation")
    @GET("games")
    suspend fun getGame(@QueryMap query: Map<String, String>): List<GameRow>

    @GET("v_categories")
    suspend fun listCategories(@QueryMap query: Map<String, String> = emptyMap()): List<CategoryRow>

    @GET("game_versions")
    suspend fun listVersionsForGame(@QueryMap query: Map<String, String>): List<GameVersionRow>

    /* ---------- Perfis ---------- */

    @GET("profiles")
    suspend fun listProfiles(@QueryMap query: Map<String, String>): List<ProfileRow>

    @Headers("Prefer: return=representation")
    @POST("profiles")
    suspend fun insertProfile(@Body body: UpsertProfileBody): List<ProfileRow>

    @Headers("Prefer: return=representation")
    @PATCH("profiles")
    suspend fun updateProfileRow(@QueryMap query: Map<String, String>, @Body body: UpdateProfileRowBody): List<ProfileRow>

    /* ---------- Biblioteca ---------- */

    @Headers("Prefer: return=representation")
    @GET("library")
    suspend fun listLibrary(@QueryMap query: Map<String, String>): List<LibraryRow>

    @Headers("Prefer: return=representation, resolution=merge-duplicates")
    @POST("library")
    suspend fun insertLibrary(@Body body: Map<String, String>): List<LibraryRow>

    @DELETE("library")
    suspend fun deleteLibrary(@QueryMap query: Map<String, String>): RetrofitResponse<Unit>

    /* ---------- Favoritos ---------- */

    @Headers("Prefer: return=representation")
    @GET("favorites")
    suspend fun listFavorites(@QueryMap query: Map<String, String>): List<FavoriteRow>

    @Headers("Prefer: return=representation, resolution=merge-duplicates")
    @POST("favorites")
    suspend fun insertFavorite(@Body body: Map<String, String>): List<FavoriteRow>

    @DELETE("favorites")
    suspend fun deleteFavorite(@QueryMap query: Map<String, String>): RetrofitResponse<Unit>

    /* ---------- Reviews ---------- */

    @Headers("Prefer: return=representation")
    @GET("reviews")
    suspend fun listReviews(@QueryMap query: Map<String, String>): List<ReviewRow>

    @Headers("Prefer: return=representation")
    @POST("reviews")
    suspend fun insertReview(@Body body: UpsertReviewBody): List<ReviewRow>

    @Headers("Prefer: return=representation")
    @PATCH("reviews")
    suspend fun updateReview(@QueryMap query: Map<String, String>, @Body body: UpsertReviewBody): List<ReviewRow>

    @DELETE("reviews")
    suspend fun deleteReview(@QueryMap query: Map<String, String>): RetrofitResponse<Unit>

    /* ---------- RPC ---------- */

    @POST("rpc/register_download")
    suspend fun registerDownload(@Body body: RegisterDownloadBody): RetrofitResponse<Unit>

    /* ---------- Admin (games/versões) ---------- */

    @Headers("Prefer: return=representation")
    @POST("games")
    suspend fun insertGame(@Body body: CreateGameBody): List<GameRow>

    @Headers("Prefer: return=representation")
    @PATCH("games")
    suspend fun updateGame(@QueryMap query: Map<String, String>, @Body body: UpdateGameBody): List<GameRow>

    @Headers("Prefer: return=representation")
    @POST("game_versions")
    suspend fun insertVersion(@Body body: CreateVersionBody): List<GameVersionRow>

    @POST("game_categories")
    suspend fun insertGameCategory(@Body body: GameCategoryLinkBody): List<GameCategoryLinkBody>

    @DELETE("game_categories")
    suspend fun deleteGameCategory(@QueryMap query: Map<String, String>): RetrofitResponse<Unit>

    @GET("game_downloads")
    suspend fun listDownloads(@QueryMap query: Map<String, String>): List<Map<String, String?>>
}
