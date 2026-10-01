package com.gstore.app.data.remote

import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import com.gstore.app.BuildConfig
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query
import java.util.concurrent.TimeUnit

/**
 * Cliente HTTP da G Store.
 *
 * Segurança: o app NUNCA recebe o GitHub token nem a API key do Appwrite —
 * tudo isso fica no backend. Aqui só configuramos a URL pública da API.
 */
object ApiClient {

    val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        isLenient = true
    }

    private val okHttp: OkHttpClient by lazy {
        val builder = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(300, TimeUnit.SECONDS) // upload de APK pode demorar
        if (BuildConfig.DEBUG) {
            val logging = HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BASIC
            }
            builder.addInterceptor(logging)
        }
        builder.build()
    }

    /** Interceptor que anexa o JWT do Appwrite às chamadas autenticadas. */
    fun authenticatedClient(jwtProvider: () -> String?): OkHttpClient =
        okHttp.newBuilder()
            .addInterceptor { chain ->
                val jwt = jwtProvider()
                val request = if (jwt.isNullOrBlank()) {
                    chain.request()
                } else {
                    chain.request().newBuilder()
                        .header("Authorization", "Bearer $jwt")
                        .build()
                }
                chain.proceed(request)
            }
            .build()

    val api: GStoreApi by lazy {
        Retrofit.Builder()
            .baseUrl(BuildConfig.API_BASE_URL.trimEnd('/') + "/")
            .client(okHttp)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(GStoreApi::class.java)
    }

    /** API autenticada (usada por developer/admin e perfil). */
    fun authenticatedApi(jwtProvider: () -> String?): GStoreApi =
        Retrofit.Builder()
            .baseUrl(BuildConfig.API_BASE_URL.trimEnd('/') + "/")
            .client(authenticatedClient(jwtProvider))
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(GStoreApi::class.java)
}

interface GStoreApi {

    @GET("api/health")
    suspend fun health(): retrofit2.Response<Map<String, kotlinx.serialization.json.JsonElement>>

    @GET("api/games")
    suspend fun listGames(
        @Query("q") search: String? = null,
        @Query("category") category: String? = null,
        @Query("sort") sort: String? = null,
        @Query("limit") limit: Int = 50,
        @Query("offset") offset: Int = 0,
    ): GameListResponse

    @GET("api/games/{slug}")
    suspend fun getGame(@Path("slug") slug: String): GameDataResponse

    @GET("api/games/{id}/versions")
    suspend fun listVersions(@Path("id") gameId: String): VersionListResponse

    @GET("api/games/{id}/downloads")
    suspend fun downloadStats(@Path("id") gameId: String): DownloadStatsResponse

    @GET("api/categories")
    suspend fun listCategories(): CategoryListResponse

    @GET("api/categories/{slug}")
    suspend fun getCategory(@Path("slug") slug: String): CategoryDetailResponse

    /* ---------- Autenticadas (Bearer JWT via OkHttp interceptor) ---------- */

    @POST("api/auth/sync")
    suspend fun syncSession(): ProfileDataResponse

    @GET("api/profile")
    suspend fun profile(): ProfileDataResponse

    @POST("api/profile/become-developer")
    suspend fun becomeDeveloper(): ProfileDataResponse

    @PUT("api/profile")
    suspend fun updateProfile(@Body body: UpdateProfileBody): ProfileDataResponse

    @POST("api/games")
    suspend fun createGame(@Body body: CreateGameBody): GameDataResponse

    @PUT("api/games/{id}")
    suspend fun updateGame(@Path("id") gameId: String, @Body body: UpdateGameBody): GameDataResponse

    @GET("api/games")
    suspend fun myGames(
        @Query("all") all: Int = 1,
        @Query("limit") limit: Int = 100,
    ): GameListResponse
}

@Serializable
data class CreateGameBody(
    val name: String,
    val description: String? = null,
    @SerialName("short_description") val shortDescription: String? = null,
    val category: String? = null,
    val status: String = "draft",
)

@Serializable
data class UpdateGameBody(
    val name: String? = null,
    val description: String? = null,
    @SerialName("short_description") val shortDescription: String? = null,
    val category: String? = null,
    val status: String? = null,
    @SerialName("icon_url") val iconUrl: String? = null,
)

@Serializable
data class UpdateProfileBody(
    @SerialName("display_name") val displayName: String? = null,
    @SerialName("avatar_url") val avatarUrl: String? = null,
)
