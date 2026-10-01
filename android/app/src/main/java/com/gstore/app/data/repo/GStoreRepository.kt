package com.gstore.app.data.repo

import android.content.Context
import com.gstore.app.BuildConfig
import com.gstore.app.data.local.SessionStore
import com.gstore.app.data.remote.ApiClient
import com.gstore.app.data.remote.CategoryDto
import com.gstore.app.data.remote.CreateGameBody
import com.gstore.app.data.remote.GameDto
import com.gstore.app.data.remote.GameVersionDto
import com.gstore.app.data.remote.GStoreApi
import com.gstore.app.data.remote.Role
import com.gstore.app.data.remote.UpdateGameBody
import com.gstore.app.data.remote.UpdateProfileBody
import com.gstore.app.data.remote.UserProfileDto
import io.appwrite.Client
import io.appwrite.ID
import io.appwrite.services.Account
import kotlinx.serialization.json.Json
import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.asRequestBody
import okio.BufferedSink
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Repositório central do app: descoberta de jogos (API Ktor) e autenticação
 * (Appwrite). A API valida o JWT emitido pelo Appwrite — não existe um
 * segundo sistema de login.
 *
 * O GitHub token e a API key do Appwrite ficam SOMENTE no backend.
 */
class GStoreRepository private constructor(
    private val context: Context,
    private val sessionStore: SessionStore,
) {

    /* --------------------------- Appwrite --------------------------- */

    private fun appwriteClient(): Client {
        val projectId = BuildConfig.APPWRITE_PROJECT_ID
        if (projectId.isBlank()) {
            throw IllegalStateException(
                "Appwrite não configurado: defina APPWRITE_PROJECT_ID em gradle.properties ou nos secrets do CI.",
            )
        }
        return Client(context)
            .setEndpoint(BuildConfig.APPWRITE_ENDPOINT)
            .setProject(projectId)
    }

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        isLenient = true
    }

    /** Registro no Appwrite (usuário criado lá), login e sync do perfil. */
    suspend fun register(name: String, email: String, password: String): Result<UserProfileDto> = runCatching {
        val account = Account(appwriteClient())
        account.create(userId = ID.unique(), email = email, password = password, name = name)
        login(email, password).getOrThrow()
    }

    /** Login no Appwrite e sincronização do perfil com a API. */
    suspend fun login(email: String, password: String): Result<UserProfileDto> = runCatching {
        val account = Account(appwriteClient())
        account.createEmailPasswordSession(email, password)
        val jwt = account.createJWT().jwt
        sessionStore.save(jwt = jwt, userId = null, email = email, displayName = null)
        syncProfile(jwt)
    }

    suspend fun logout(): Result<Unit> = runCatching {
        runCatching { Account(appwriteClient()).deleteSession("current") }
        sessionStore.clearAll()
    }

    /** JWT válido (renova a cada chamada — token do Appwrite dura ~15 min). */
    suspend fun freshJwt(): String? = runCatching {
        if (BuildConfig.APPWRITE_PROJECT_ID.isBlank()) return@runCatching null
        val session = sessionStore.current()
        val jwt = Account(appwriteClient()).createJWT().jwt
        sessionStore.save(
            jwt = jwt,
            userId = session.appwriteUserId,
            email = session.email,
            displayName = session.displayName,
        )
        jwt
    }.getOrNull()

    suspend fun syncProfile(jwt: String): UserProfileDto {
        val profile = authenticatedApi().syncSession().data
        sessionStore.save(
            jwt = jwt,
            userId = profile.appwriteUserId,
            email = profile.email,
            displayName = profile.displayName,
        )
        return profile
    }

    suspend fun currentProfile(): UserProfileDto? {
        val jwt = freshJwt() ?: return null
        return runCatching { authenticatedApi().profile().data }.getOrNull()
    }

    suspend fun becomeDeveloper(): Result<UserProfileDto> = runCatching {
        authenticatedApi().becomeDeveloper().data
    }

    suspend fun updateProfile(displayName: String?): Result<UserProfileDto> = runCatching {
        authenticatedApi().updateProfile(UpdateProfileBody(displayName = displayName)).data
    }

    /* --------------------------- Catálogo --------------------------- */

    private val publicApi: GStoreApi get() = ApiClient.api

    private suspend fun authenticatedApi(): GStoreApi {
        val session = sessionStore.current()
        return ApiClient.authenticatedApi { session.jwt }
    }

    suspend fun listGames(
        search: String? = null,
        category: String? = null,
        sort: String? = null,
        limit: Int = 50,
    ): List<GameDto> =
        publicApi.listGames(search = search, category = category, sort = sort, limit = limit).games

    suspend fun getGame(slug: String): GameDto = publicApi.getGame(slug).data

    suspend fun listCategories(): List<CategoryDto> = publicApi.listCategories().categories

    suspend fun listVersions(gameId: String): List<GameVersionDto> =
        publicApi.listVersions(gameId).versions

    /** Estatísticas de download do jogo (dono/admin). */
    suspend fun downloadStats(gameId: String): com.gstore.app.data.remote.DownloadStatsResponse =
        authenticatedApi().downloadStats(gameId)

    /* ----------------------- Developer / publicação ----------------------- */

    suspend fun createGame(name: String, description: String?, category: String?): Result<GameDto> = runCatching {
        authenticatedApi().createGame(CreateGameBody(name = name, description = description, category = category)).data
    }

    suspend fun updateGame(
        gameId: String,
        name: String?,
        description: String?,
        category: String?,
        status: String?,
    ): Result<GameDto> = runCatching {
        authenticatedApi().updateGame(
            gameId,
            UpdateGameBody(name = name, description = description, category = category, status = status),
        ).data
    }

    /**
     * Publica uma versão: envia o APK por multipart (streaming com progresso)
     * para a API, que sobe para o GitHub Releases e registra tudo no Neon.
     */
    suspend fun publishVersion(
        gameId: String,
        version: String,
        versionCode: Int?,
        releaseNotes: String?,
        apk: File,
        icon: File?,
        screenshots: List<File>,
        onProgress: (bytesSent: Long, totalBytes: Long) -> Unit = { _, _ -> },
    ): Result<Pair<GameDto, GameVersionDto>> = runCatching {
        val jwt = freshJwt() ?: error("not_authenticated")

        val multipart = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("version", version)
        versionCode?.let { multipart.addFormDataPart("version_code", it.toString()) }
        releaseNotes?.takeIf { it.isNotBlank() }?.let { multipart.addFormDataPart("release_notes", it) }

        multipart.addFormDataPart(
            "apk",
            apk.name,
            ProgressRequestBody(apk, "application/vnd.android.package-archive".toMediaType(), onProgress),
        )
        icon?.let { multipart.addFormDataPart("icon", it.name, it.asRequestBody("image/png".toMediaType())) }
        screenshots.forEachIndexed { index, shot ->
            multipart.addFormDataPart(
                "screenshots",
                "screen-${index + 1}.png",
                shot.asRequestBody("image/png".toMediaType()),
            )
        }

        val request = Request.Builder()
            .url(BuildConfig.API_BASE_URL.trimEnd('/') + "/api/games/$gameId/versions")
            .header("Authorization", "Bearer $jwt")
            .post(multipart.build())
            .build()

        val http = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(600, TimeUnit.SECONDS)
            .readTimeout(600, TimeUnit.SECONDS)
            .build()

        http.newCall(request).execute().use { response ->
            val bodyStr = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                error("publish_failed (${response.code}): ${parseError(bodyStr)}")
            }
            val parsed = json.decodeFromString(
                com.gstore.app.data.remote.PublishDataResponse.serializer(),
                bodyStr,
            )
            parsed.data.game to parsed.data.version
        }
    }

    private fun parseError(body: String): String =
        runCatching {
            val err = json.decodeFromString(com.gstore.app.data.remote.ErrorResponse.serializer(), body)
            err.message ?: err.error
        }.getOrDefault("erro desconhecido")

    companion object {
        @Volatile private var instance: GStoreRepository? = null

        fun get(context: Context, sessionStore: SessionStore): GStoreRepository =
            instance ?: synchronized(this) {
                instance ?: GStoreRepository(context.applicationContext, sessionStore).also { instance = it }
            }
    }
}

/** RequestBody com progresso real de upload (streaming em blocos de 64 KB). */
private class ProgressRequestBody(
    private val file: File,
    private val mediaType: MediaType,
    private val onProgress: (Long, Long) -> Unit,
) : RequestBody() {
    override fun contentType(): MediaType? = mediaType
    override fun contentLength(): Long = file.length()

    override fun writeTo(sink: BufferedSink) {
        val total = file.length()
        var written = 0L
        file.inputStream().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val read = input.read(buffer)
                if (read <= 0) break
                sink.write(buffer, 0, read)
                written += read
                onProgress(written, total)
            }
        }
    }
}
