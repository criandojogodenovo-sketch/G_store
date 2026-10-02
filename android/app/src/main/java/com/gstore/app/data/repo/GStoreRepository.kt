package com.gstore.app.data.repo

import android.content.Context
import com.gstore.app.BuildConfig
import com.gstore.app.ConfigCheck
import com.gstore.app.data.local.SessionStore
import com.gstore.app.data.remote.ApiClient
import com.gstore.app.data.remote.ApiException
import com.gstore.app.data.remote.AuthErrorBody
import com.gstore.app.data.remote.AuthUser
import com.gstore.app.data.remote.CreateGameBody
import com.gstore.app.data.remote.CreateVersionBody
import com.gstore.app.data.remote.GameCategoryLinkBody
import com.gstore.app.data.remote.GameRow
import com.gstore.app.data.remote.GameVersionRow
import com.gstore.app.data.remote.NotAuthenticatedException
import com.gstore.app.data.remote.RegisterDownloadBody
import com.gstore.app.data.remote.Role
import com.gstore.app.data.remote.SignInBody
import com.gstore.app.data.remote.SignUpBody
import com.gstore.app.data.remote.UpdateGameBody
import com.gstore.app.data.remote.UpdateUserBody
import com.gstore.app.data.remote.UpsertProfileBody
import com.gstore.app.data.remote.UpsertReviewBody
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.TimeUnit

/**
 * Repositório central do app: autenticação (Neon Auth) + dados (Neon
 * Data API com RLS). Não existe servidor próprio — o app fala
 * diretamente com o Neon, e o Postgres decide o que cada token pode fazer.
 */
class GStoreRepository private constructor(
    private val context: Context,
    private val sessionStore: SessionStore,
) {

    private val json get() = ApiClient.json
    private val authApi get() = ApiClient.authApi
    private val dataApi get() = ApiClient.dataApi
    private val cache = CatalogCache(context)

    private val jwtMutex = Mutex()
    private val anonMutex = Mutex()

    /* ═══════════════════ Autenticação (Neon Auth) ═══════════════════ */

    /** Registo + login automático + criação do perfil local. */
    suspend fun register(name: String, email: String, password: String): Result<ProfileDto> = runCatching {
        checkConfig()
        val resp = authApi.signUp(SignUpBody(name = name, email = email, password = password))
        if (!resp.isSuccessful) {
            val err = parseAuthError(resp.errorBody()?.string())
            throw ApiException(resp.code(), err?.code, err?.message ?: "Falha no registo")
        }
        val body = resp.body() ?: throw ApiException(resp.code(), null, "Resposta vazia do Neon Auth")
        val user = body.user ?: throw ApiException(resp.code(), null, "Registo sem utilizador")
        adoptSession(resp.headers().values("Set-Cookie"), user)
        ensureProfile(user, freshSession = true)
    }

    /** Login por e-mail/senha (Better Auth). */
    suspend fun login(email: String, password: String): Result<ProfileDto> = runCatching {
        checkConfig()
        val resp = authApi.signIn(SignInBody(email = email, password = password))
        if (!resp.isSuccessful) {
            val err = parseAuthError(resp.errorBody()?.string())
            throw ApiException(resp.code(), err?.code, err?.message ?: "Falha no login")
        }
        val body = resp.body() ?: throw ApiException(resp.code(), null, "Resposta vazia do Neon Auth")
        val user = body.user ?: throw ApiException(resp.code(), null, "Login sem utilizador")
        adoptSession(resp.headers().values("Set-Cookie"), user)
        ensureProfile(user, freshSession = true)
    }

    /** Termina a sessão no servidor e limpa o dispositivo. */
    suspend fun logout(): Result<Unit> = runCatching {
        ensureAuthCookie()
        runCatching { authApi.signOut() }
        ApiClient.sessionCookie = null
        ApiClient.dataToken = null
        sessionStore.clearAll()
    }

    /** Utilizador autenticado atual (null se não houver sessão válida). */
    suspend fun currentUser(): AuthUser? {
        ensureAuthCookie()
        if (ApiClient.sessionCookie.isNullOrBlank()) return null
        return runCatching {
            val resp = authApi.getSession()
            resp.body()?.user
        }.getOrNull()
    }

    /** Altera o nome de exibição (Neon Auth + perfil local). */
    suspend fun updateDisplayName(displayName: String): Result<ProfileDto> = runCatching {
        ensureAuthCookie()
        authApi.updateUser(UpdateUserBody(name = displayName))
        val uid = requireUserId()
        ensureDataToken()
        runCatching {
            dataApi.updateProfileRow(
                mapOf("id" to "eq.$uid"),
                com.gstore.app.data.remote.UpdateProfileRowBody(displayName = displayName),
            )
        }
        currentProfile() ?: throw ApiException(500, null, "Perfil não encontrado após renomear")
    }

    /* ---------------- Tokens para a Data API ---------------- */

    /** Restaura o cookie guardado (após reinício do app, por exemplo). */
    private suspend fun ensureAuthCookie() {
        if (ApiClient.sessionCookie == null) {
            ApiClient.sessionCookie = sessionStore.current().cookie
        }
    }

    /**
     * Garante que o ApiClient tem um JWT válido para a Data API:
     * do utilizador (15 min, renovável via get-session) ou anónimo (1 h).
     *
     * @param freshSession true logo a seguir a um sign-in/sign-up: o
     *   get-session é repetido com pausas crescentes porque o Neon Auth
     *   (beta) pode demorar um instante a tornar a sessão nova visível —
     *   sem isto o login caía para o token anónimo e falhava com 403.
     */
    private suspend fun ensureDataToken(freshSession: Boolean = false): String? {
        if (ApiClient.sessionCookie == null) {
            ensureAuthCookie()
        }
        val token = if (ApiClient.sessionCookie.isNullOrBlank()) {
            anonymousTokenOrNull()
        } else {
            userJwtOrNull(freshSession) ?: anonymousTokenOrNull()
        }
        ApiClient.dataToken = token
        return token
    }

    private suspend fun userJwtOrNull(freshSession: Boolean = false): String? {
        val cached = sessionStore.jwt()
        val now = System.currentTimeMillis()
        if (!cached.token.isNullOrBlank() && cached.expiresAt != null && cached.expiresAt > now + 60_000) {
            return cached.token
        }
        return refreshUserJwtOrNull(freshSession)
    }

    private suspend fun refreshUserJwtOrNull(freshSession: Boolean = false): String? = jwtMutex.withLock {
        // Outra coroutine pode ter renovado entretanto.
        val cached = sessionStore.jwt()
        val now = System.currentTimeMillis()
        if (!cached.token.isNullOrBlank() && cached.expiresAt != null && cached.expiresAt > now + 60_000) {
            return@withLock cached.token
        }
        // Logo após o login a sessão pode não estar visível no serviço de
        // auth (beta), e pedidos repetidos demais podem levar a 429: por
        // isso repetimos com pausas crescentes (250ms → 8s no total).
        val tentativas = if (freshSession) 6 else 2
        for (i in 0 until tentativas) {
            if (i > 0) kotlinx.coroutines.delay(250L * (1L shl (i - 1))) // 250ms, 500ms, 1s, 2s, 4s
            val resp = runCatching { authApi.getSession() }.getOrNull()
                ?: continue // erro de rede: tenta outra vez
            if (!resp.isSuccessful) {
                // 429 (limites do serviço beta): esperar e repetir; outros
                // códigos (ex.: cookie inválido) não valem a pena insistir.
                if (resp.code() == 429 && i < tentativas - 1) continue
                return@withLock null
            }
            val jwt = resp.headers().values("set-auth-jwt").firstOrNull()
            if (!jwt.isNullOrBlank()) {
                val exp = ApiClient.jwtExpiry(jwt) ?: (System.currentTimeMillis() / 1000 + 840)
                sessionStore.saveJwt(jwt, exp)
                return@withLock jwt
            }
            // 200 sem sessão: o serviço ainda não vê a sessão nova — nova tentativa.
        }
        null
    }

    /** Token anónimo público (leitura do catálogo sem login). */
    private suspend fun anonymousTokenOrNull(): String? = anonMutex.withLock {
        val cached = sessionStore.anonymousToken()
        val now = System.currentTimeMillis()
        if (!cached.token.isNullOrBlank() && cached.expiresAt != null && cached.expiresAt > now + 60_000) {
            return@withLock cached.token
        }
        val resp = runCatching { authApi.anonymousToken() }.getOrNull() ?: return@withLock null
        sessionStore.saveAnonymousToken(resp.token, resp.expiresAt)
        resp.token
    }

    private suspend fun adoptSession(setCookies: List<String>, user: AuthUser) {
        val cookie = parseSessionCookie(setCookies)
        if (cookie != null) {
            ApiClient.sessionCookie = cookie
            sessionStore.save(cookie = cookie)
        }
        sessionStore.clearJwt()
        ApiClient.dataToken = null
        sessionStore.save(
            userId = user.id,
            email = user.email,
            displayName = user.name,
            avatarUrl = user.image,
        )
    }

    private fun parseSessionCookie(setCookies: List<String>): String? {
        val name = "__Secure-neon-auth.session_token="
        for (header in setCookies) {
            val idx = header.indexOf(name)
            if (idx >= 0) {
                val value = header.substring(idx + name.length).substringBefore(';').trim()
                if (value.isNotBlank()) return value
            }
        }
        return null
    }

    private suspend fun requireUserId(): String {
        ensureAuthCookie()
        val session = sessionStore.current()
        if (!session.cookie.isNullOrBlank()) {
            session.userId?.let { return it }
            currentUser()?.let { return it.id }
        }
        throw NotAuthenticatedException()
    }

    /* ---------------- Perfil (tabela profiles) ---------------- */

    /**
     * Garante que o utilizador autenticado tem linha em `profiles`.
     *
     * Logo após o sign-in/sign-up exigimos o JWT DO UTILIZADOR: se o serviço
     * (beta) ainda não o devolveu, falhamos com mensagem clara em vez de
     * cair para o token anónimo (que dava um 403 confuso em /profiles).
     */
    private suspend fun ensureProfile(user: AuthUser, freshSession: Boolean = false): ProfileDto {
        val uid = user.id
        ensureDataToken(freshSession)
        if (freshSession && sessionStore.jwt().token.isNullOrBlank()) {
            throw ApiException(
                503, "SESSION_NOT_READY",
                "Login aceite, mas o Neon Auth demorou a confirmar a sessão (serviço em beta). " +
                    "Toque em Entrar novamente — a sessão já ficou gravada.",
            )
        }
        val existing = dataApi.listProfiles(mapOf("id" to "eq.$uid", "select" to "*"))
        if (existing.isNotEmpty()) {
            val row = existing.first()
            sessionStore.save(displayName = row.displayName, avatarUrl = row.avatarUrl)
            return row.toDto(email = user.email)
        }
        val created = try {
            dataApi.insertProfile(
                UpsertProfileBody(
                    id = uid,
                    displayName = user.name ?: (user.email?.substringBefore('@') ?: "Jogador"),
                    avatarUrl = user.image,
                    role = "user",
                ),
            ).first()
        } catch (e: retrofit2.HttpException) {
            // Corrida de criação dupla: a linha já existe — releitura.
            if (e.code() == 409) {
                dataApi.listProfiles(mapOf("id" to "eq.$uid")).firstOrNull()
                    ?: throw e
            } else {
                throw e
            }
        }
        sessionStore.save(displayName = created.displayName, avatarUrl = created.avatarUrl)
        return created.toDto(email = user.email)
    }

    /** Perfil do utilizador atual (null se não autenticado). */
    suspend fun currentProfile(): ProfileDto? {
        ensureAuthCookie()
        val session = sessionStore.current()
        if (session.cookie.isNullOrBlank()) return null
        val uid = session.userId ?: currentUser()?.id ?: return null
        ensureDataToken()
        val rows = runCatching { dataApi.listProfiles(mapOf("id" to "eq.$uid")) }.getOrNull() ?: return null
        return rows.firstOrNull()?.toDto(email = session.email)
    }

    suspend fun isAdmin(): Boolean = currentProfile()?.role == Role.ADMIN

    /* ═══════════════════ Catálogo (leitura pública) ═══════════════════ */

    /**
     * Carrega o catálogo completo (publicado; admin vê também rascunhos)
     * com versões e categorias embutidas. Grava em cache local para uso
     * offline e devolve o resultado já mapeado.
     */
    suspend fun fetchCatalog(includeDrafts: Boolean = false): Pair<List<GameDto>, List<CategoryDto>> {
        checkConfig()
        ensureDataToken()
        val filters = mutableMapOf(
            "select" to "*,game_versions(*),game_categories(categories(*))",
            "order" to "created_at.desc",
            "limit" to "500",
        )
        if (!includeDrafts) filters["status"] = "eq.published"
        val games = dataApi.listGames(filters).map { it.toDto() }
        val categories = dataApi.listCategories(mapOf("order" to "sort_order.asc")).map { it.toDto() }

        cache.save(games, categories)
        return games to categories
    }

    /** Catálogo em cache (última leitura bem-sucedida; null se nunca houve). */
    fun cachedCatalog(): Pair<List<GameDto>, List<CategoryDto>>? = cache.load()

    suspend fun getGame(slug: String): GameDto {
        checkConfig()
        ensureDataToken()
        val rows = dataApi.getGame(
            mapOf(
                "slug" to "eq.$slug",
                "select" to "*,game_versions(*),game_categories(categories(*))",
            ),
        )
        return rows.firstOrNull()?.toDto() ?: throw ApiException(404, null, "Jogo não encontrado: $slug")
    }

    suspend fun listVersions(gameId: String): List<GameVersionDto> {
        ensureDataToken()
        return dataApi.listVersionsForGame(
            mapOf("game_id" to "eq.$gameId", "order" to "created_at.desc"),
        ).map { it.toVersionDto() }
    }

    suspend fun listCategories(): List<CategoryDto> {
        ensureDataToken()
        return dataApi.listCategories(mapOf("order" to "sort_order.asc")).map { it.toDto() }
    }

    /* ═══════════════════ Downloads ═══════════════════ */

    /**
     * Regista a descarga (tabela game_downloads + contador do jogo) e
     * adiciona à biblioteca do utilizador. Só com sessão iniciada —
     * utilizadores anónimos podem descarregar sem registo.
     */
    suspend fun registerDownload(game: GameDto, version: GameVersionDto?) {
        ensureAuthCookie()
        if (ApiClient.sessionCookie.isNullOrBlank()) return // anónimo: sem registo
        val uid = requireUserId()
        ensureDataToken()
        runCatching {
            dataApi.registerDownload(RegisterDownloadBody(gameId = game.id, versionId = version?.id))
        }
        addToLibrary(uid, game.id, version?.id)
    }

    /* ═══════════════════ Biblioteca ═══════════════════ */

    suspend fun listLibrary(): List<GameDto> {
        ensureAuthCookie()
        ensureDataToken()
        val rows = dataApi.listLibrary(
            mapOf(
                "select" to "*,games(*,game_versions(*),game_categories(categories(*)))",
                "order" to "created_at.desc",
            ),
        )
        return rows.mapNotNull { it.games?.toDto() }
    }

    private suspend fun addToLibrary(userId: String, gameId: String, versionId: String?) {
        runCatching {
            dataApi.insertLibrary(
                mapOf(
                    "user_id" to userId,
                    "game_id" to gameId,
                    "game_version_id" to versionId.orEmpty(),
                ).filterValues { it.isNotBlank() },
            )
        } // duplicado (já na biblioteca) ou falha de rede não bloqueiam o download
    }

    suspend fun removeFromLibrary(gameId: String): Result<Unit> = runCatching {
        ensureAuthCookie()
        ensureDataToken()
        dataApi.deleteLibrary(mapOf("game_id" to "eq.$gameId"))
        Unit
    }

    /* ═══════════════════ Favoritos ═══════════════════ */

    suspend fun listFavorites(): List<GameDto> {
        ensureAuthCookie()
        ensureDataToken()
        val rows = dataApi.listFavorites(
            mapOf(
                "select" to "*,games(*,game_versions(*),game_categories(categories(*)))",
                "order" to "created_at.desc",
            ),
        )
        return rows.mapNotNull { it.games?.toDto() }
    }

    suspend fun isFavorite(gameId: String): Boolean {
        ensureAuthCookie()
        if (ApiClient.sessionCookie.isNullOrBlank()) return false
        val uid = sessionStore.current().userId ?: return false
        ensureDataToken()
        val rows = runCatching {
            dataApi.listFavorites(mapOf("game_id" to "eq.$gameId", "user_id" to "eq.$uid", "select" to "id"))
        }.getOrNull() ?: return false
        return rows.isNotEmpty()
    }

    suspend fun addFavorite(gameId: String): Result<Unit> = runCatching {
        val uid = requireUserId()
        ensureDataToken()
        runCatching {
            dataApi.insertFavorite(mapOf("user_id" to uid, "game_id" to gameId))
        }
        Unit
    }

    suspend fun removeFavorite(gameId: String): Result<Unit> = runCatching {
        ensureAuthCookie()
        ensureDataToken()
        dataApi.deleteFavorite(mapOf("game_id" to "eq.$gameId"))
        Unit
    }

    /* ═══════════════════ Reviews ═══════════════════ */

    suspend fun listReviews(gameId: String): List<ReviewDto> {
        ensureDataToken()
        val rows = dataApi.listReviews(
            mapOf(
                "game_id" to "eq.$gameId",
                "select" to "*",
                "order" to "updated_at.desc",
            ),
        )
        if (rows.isEmpty()) return emptyList()
        // Nomes dos autores: colunas PÚBLICAS de profiles, lidas sempre com
        // o token anónimo — o RLS só devolve a linha completa ao próprio.
        val ids = rows.map { it.userId }.distinct().joinToString(",") { "\"$it\"" }
        val authors = runCatching {
            ensureAnonDataToken()
            ApiClient.anonDataApi.listAuthorProfiles(
                mapOf("id" to "in.($ids)", "select" to "id,display_name,avatar_url"),
            )
        }.getOrNull().orEmpty()
        val byId = authors.associateBy { it.id }
        return rows.map { row ->
            row.toDto(authorName = byId[row.userId]?.displayName, authorAvatar = byId[row.userId]?.avatarUrl)
        }
    }

    /** Token anónimo dedicado à leitura pública (autores das reviews). */
    private suspend fun ensureAnonDataToken(): String? {
        ApiClient.anonDataToken?.let { return it }
        val t = anonymousTokenOrNull() ?: return null
        ApiClient.anonDataToken = t
        return t
    }

    /** Cria ou atualiza a review do próprio utilizador para o jogo. */
    suspend fun upsertReview(gameId: String, rating: Int, comment: String?): Result<ReviewDto> = runCatching {
        val uid = requireUserId()
        ensureDataToken()
        val existing = dataApi.listReviews(
            mapOf("game_id" to "eq.$gameId", "user_id" to "eq.$uid", "select" to "*"),
        )
        if (existing.isEmpty()) {
            dataApi.insertReview(
                UpsertReviewBody(gameId = gameId, userId = uid, rating = rating.coerceIn(1, 5), comment = comment),
            ).first()
        } else {
            dataApi.updateReview(
                mapOf("id" to "eq.${existing.first().id}"),
                UpsertReviewBody(gameId = gameId, userId = uid, rating = rating.coerceIn(1, 5), comment = comment),
            ).first()
        }.toDto()
    }

    /* ═══════════════════ Administração (só role=admin) ═══════════════════ */

    suspend fun createGame(
        name: String,
        slug: String,
        type: String,
        description: String?,
        shortDescription: String?,
        iconUrl: String?,
        screenshots: List<String>,
        status: String,
        developerId: String?,
    ): Result<GameDto> = runCatching {
        ensureAuthCookie()
        ensureDataToken()
        val row = dataApi.insertGame(
            CreateGameBody(
                name = name,
                slug = slug,
                type = type,
                description = description?.takeIf { it.isNotBlank() },
                shortDescription = shortDescription?.takeIf { it.isNotBlank() },
                iconUrl = iconUrl?.takeIf { it.isNotBlank() },
                screenshots = screenshots.filter { it.isNotBlank() },
                status = status,
                developerId = developerId,
            ),
        ).firstOrNull() ?: throw ApiException(500, null, "Jogo criado mas não devolvido")
        row.toDto()
    }

    suspend fun updateGame(
        gameId: String,
        name: String?,
        type: String?,
        description: String?,
        shortDescription: String?,
        iconUrl: String?,
        status: String?,
    ): Result<GameDto> = runCatching {
        ensureAuthCookie()
        ensureDataToken()
        dataApi.updateGame(
            mapOf("id" to "eq.$gameId"),
            UpdateGameBody(
                name = name,
                type = type,
                description = description,
                shortDescription = shortDescription,
                iconUrl = iconUrl,
                status = status,
            ),
        ).firstOrNull()?.toDto() ?: throw ApiException(500, null, "Jogo atualizado mas não devolvido")
    }

    suspend fun setGameCategory(gameId: String, categoryId: String): Result<Unit> = runCatching {
        ensureAuthCookie()
        ensureDataToken()
        dataApi.deleteGameCategory(mapOf("game_id" to "eq.$gameId"))
        if (categoryId.isNotBlank()) {
            dataApi.insertGameCategory(GameCategoryLinkBody(gameId = gameId, categoryId = categoryId))
        }
        Unit
    }

    suspend fun createVersion(
        gameId: String,
        version: String,
        versionCode: Long?,
        releaseNotes: String?,
        apkUrl: String,
    ): Result<GameVersionDto> = runCatching {
        ensureAuthCookie()
        ensureDataToken()
        dataApi.insertVersion(
            CreateVersionBody(
                gameId = gameId,
                version = version,
                versionCode = versionCode,
                releaseNotes = releaseNotes?.takeIf { it.isNotBlank() },
                apkUrl = apkUrl.trim(),
                releaseTag = "app-$version",
            ),
        ).firstOrNull()?.toVersionDto() ?: throw ApiException(500, null, "Versão criada mas não devolvida")
    }

    /** Estatísticas de download do jogo (visíveis só para o admin via RLS). */
    suspend fun downloadStats(gameId: String): DownloadStatsLite {
        ensureAuthCookie()
        ensureDataToken()
        fun q(desdeIso: String?) = buildMap {
            put("game_id", "eq.$gameId")
            put("select", "id")
            put("limit", "100000")
            if (desdeIso != null) put("created_at", "gte.$desdeIso")
        }
        val total = dataApi.listDownloads(q(null)).size.toLong()
        val last7 = dataApi.listDownloads(q(isoDaysAgo(7))).size.toLong()
        val last30 = dataApi.listDownloads(q(isoDaysAgo(30))).size.toLong()
        return DownloadStatsLite(total = total, last7Days = last7, last30Days = last30)
    }

    /* ═══════════════════ Mapeamentos ═══════════════════ */

    private fun GameRow.toDto(): GameDto {
        val cats = gameCategories.mapNotNull { it.categories }
        return GameDto(
            id = id,
            slug = slug,
            name = name,
            type = type,
            description = description,
            shortDescription = shortDescription,
            developer = developer,
            developerId = developerId,
            category = cats.firstOrNull()?.name ?: category,
            categories = cats.map { it.slug },
            iconUrl = iconUrl,
            screenshots = screenshots,
            version = version ?: gameVersions.maxByOrNull { it.createdAt ?: "" }?.version,
            versionCode = versionCode,
            downloads = downloads,
            status = status.name.lowercase(),
            createdAt = createdAt,
            updatedAt = updatedAt,
        )
    }

    private fun GameVersionRow.toVersionDto(): GameVersionDto = GameVersionDto(
        id = id,
        gameId = gameId,
        version = version,
        versionCode = versionCode,
        releaseNotes = releaseNotes,
        apkUrl = apkUrl,
        apkFileName = apkFileName,
        apkSizeBytes = apkSizeBytes,
        releaseTag = releaseTag,
        createdAt = createdAt,
    )

    private fun com.gstore.app.data.remote.CategoryRow.toDto(): CategoryDto = CategoryDto(
        id = id,
        slug = slug,
        name = name,
        type = type,
        description = description,
        iconUrl = iconUrl,
        sortOrder = sortOrder,
        gamesCount = gamesCount,
    )

    private fun com.gstore.app.data.remote.ProfileRow.toDto(email: String?): ProfileDto = ProfileDto(
        id = id,
        authUserId = id,
        email = email,
        displayName = displayName,
        avatarUrl = avatarUrl,
        role = when (role) {
            "admin" -> Role.ADMIN
            "developer" -> Role.DEVELOPER
            else -> Role.USER
        },
        createdAt = createdAt,
    )

    private fun com.gstore.app.data.remote.ReviewRow.toDto(
        authorName: String? = null,
        authorAvatar: String? = null,
    ): ReviewDto = ReviewDto(
        id = id,
        gameId = gameId,
        userId = userId,
        rating = rating,
        comment = comment,
        authorName = authorName ?: "Utilizador G Store",
        authorAvatar = authorAvatar,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )

    /* ═══════════════════ Erros ═══════════════════ */

    private fun checkConfig() {
        val faltam = ConfigCheck.fromBuild()
        if (faltam.isNotEmpty()) {
            throw IllegalStateException(
                "Neon não configurado: falta ${faltam.joinToString(", ")}. " +
                    "Defina em android/gradle.properties (ou -PNEON_AUTH_URL=... no build).",
            )
        }
    }

    private fun parseAuthError(body: String?): AuthErrorBody? = body?.let {
        runCatching { json.decodeFromString(AuthErrorBody.serializer(), it) }.getOrNull()
    }

    companion object {
        @Volatile private var instance: GStoreRepository? = null

        fun get(context: Context, sessionStore: SessionStore): GStoreRepository =
            instance ?: synchronized(this) {
                instance ?: GStoreRepository(context.applicationContext, sessionStore).also { instance = it }
            }
    }
}

/* ═════ DTOs de UI (mantêm o formato que as telas já usam) ═════ */

@kotlinx.serialization.Serializable
data class GameDto(
    val id: String,
    val slug: String,
    val name: String,
    /** "game" (padrão) ou "app" — a G Store vende apps E jogos. */
    val type: String = "game",
    val description: String? = null,
    @kotlinx.serialization.SerialName("short_description") val shortDescription: String? = null,
    val developer: String? = null,
    @kotlinx.serialization.SerialName("developer_id") val developerId: String? = null,
    val category: String? = null,
    val categories: List<String> = emptyList(),
    @kotlinx.serialization.SerialName("icon_url") val iconUrl: String? = null,
    val screenshots: List<String> = emptyList(),
    val version: String? = null,
    @kotlinx.serialization.SerialName("version_code") val versionCode: Long? = null,
    val downloads: Long = 0,
    val status: String = "published",
    @kotlinx.serialization.SerialName("created_at") val createdAt: String? = null,
    @kotlinx.serialization.SerialName("updated_at") val updatedAt: String? = null,
)

@kotlinx.serialization.Serializable
data class GameVersionDto(
    val id: String,
    @kotlinx.serialization.SerialName("game_id") val gameId: String,
    val version: String,
    @kotlinx.serialization.SerialName("version_code") val versionCode: Long? = null,
    @kotlinx.serialization.SerialName("release_notes") val releaseNotes: String? = null,
    @kotlinx.serialization.SerialName("apk_url") val apkUrl: String,
    @kotlinx.serialization.SerialName("apk_file_name") val apkFileName: String? = null,
    @kotlinx.serialization.SerialName("apk_size_bytes") val apkSizeBytes: Long? = null,
    @kotlinx.serialization.SerialName("release_tag") val releaseTag: String? = null,
    @kotlinx.serialization.SerialName("created_at") val createdAt: String? = null,
)

@kotlinx.serialization.Serializable
data class CategoryDto(
    val id: String,
    val slug: String,
    val name: String,
    /** "game" (padrão) ou "app". */
    val type: String = "game",
    val description: String? = null,
    @kotlinx.serialization.SerialName("icon_url") val iconUrl: String? = null,
    @kotlinx.serialization.SerialName("sort_order") val sortOrder: Int = 0,
    @kotlinx.serialization.SerialName("games_count") val gamesCount: Long = 0,
)

@kotlinx.serialization.Serializable
data class ProfileDto(
    val id: String,
    val authUserId: String,
    val email: String? = null,
    @kotlinx.serialization.SerialName("display_name") val displayName: String? = null,
    @kotlinx.serialization.SerialName("avatar_url") val avatarUrl: String? = null,
    val role: Role = Role.USER,
    @kotlinx.serialization.SerialName("created_at") val createdAt: String? = null,
)

@kotlinx.serialization.Serializable
data class ReviewDto(
    val id: String,
    @kotlinx.serialization.SerialName("game_id") val gameId: String,
    @kotlinx.serialization.SerialName("user_id") val userId: String,
    val rating: Int,
    val comment: String? = null,
    val authorName: String? = null,
    val authorAvatar: String? = null,
    @kotlinx.serialization.SerialName("created_at") val createdAt: String? = null,
    @kotlinx.serialization.SerialName("updated_at") val updatedAt: String? = null,
)

data class DownloadStatsLite(
    val total: Long,
    val last7Days: Long,
    val last30Days: Long,
)

/* ═══════════════ Helpers de data (formato ISO do Postgres) ═══════════════ */

internal fun isoDaysAgo(days: Long): String {
    val t = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(days)
    return java.time.format.DateTimeFormatter.ISO_INSTANT
        .format(java.time.Instant.ofEpochMilli(t).truncatedTo(java.time.temporal.ChronoUnit.SECONDS))
}

/**
 * Filtro de tipo usado na Home e na Busca: Tudo / Apps / Jogos.
 * [apiValue] é o valor enviado à coluna `type` ("" = sem filtro).
 */
enum class CatalogTypeFilter(val rotulo: String, val apiValue: String) {
    ALL("Tudo", ""),
    APPS("Apps", "app"),
    GAMES("Jogos", "game");

    fun matches(type: String?): Boolean = apiValue.isEmpty() || type == apiValue
}

/** Plural correto em português: 1 app / 2 apps, 1 jogo / 2 jogos… */
fun countLabel(n: Long, singular: String, plural: String): String =
    if (n == 1L) "1 $singular" else "$n $plural"
