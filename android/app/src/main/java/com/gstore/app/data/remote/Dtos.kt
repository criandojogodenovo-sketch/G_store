package com.gstore.app.data.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/* ════════════════════ Neon Auth (Better Auth) ════════════════════ */

@Serializable
data class SignInBody(
    val email: String,
    val password: String,
)

@Serializable
data class SignUpBody(
    val name: String,
    val email: String,
    val password: String,
)

@Serializable
data class UpdateUserBody(
    val name: String? = null,
    val image: String? = null,
)

@Serializable
data class AuthUser(
    val id: String,
    val name: String? = null,
    val email: String? = null,
    @SerialName("emailVerified") val emailVerified: Boolean = false,
    val image: String? = null,
    val role: String? = null,
    val banned: Boolean = false,
    @SerialName("createdAt") val createdAt: String? = null,
)

@Serializable
data class AuthResponse(
    val token: String? = null,
    val user: AuthUser? = null,
    val redirect: Boolean? = null,
)

@Serializable
data class AuthSession(
    val id: String? = null,
    @SerialName("userId") val userId: String? = null,
    @SerialName("expiresAt") val expiresAt: String? = null,
)

@Serializable
data class SessionResponse(
    val session: AuthSession? = null,
    val user: AuthUser? = null,
)

@Serializable
data class AnonymousTokenResponse(
    val token: String,
    @SerialName("expires_at") val expiresAt: Long,
)

@Serializable
data class AuthErrorBody(
    val code: String? = null,
    val message: String? = null,
)

/* ════════════════════ Data API (PostgREST) ════════════════════ */

@kotlinx.serialization.Serializable
enum class GameStatus {
    @kotlinx.serialization.SerialName("draft") DRAFT,
    @kotlinx.serialization.SerialName("published") PUBLISHED,
    @kotlinx.serialization.SerialName("unpublished") UNPUBLISHED,
}

@kotlinx.serialization.Serializable
enum class Role {
    @kotlinx.serialization.SerialName("user") USER,
    @kotlinx.serialization.SerialName("developer") DEVELOPER,
    @kotlinx.serialization.SerialName("admin") ADMIN,
}

/** Linha da tabela `games` (com relações embutidas opcionais). */
@Serializable
data class GameRow(
    val id: String,
    val slug: String,
    val name: String,
    val description: String? = null,
    @SerialName("short_description") val shortDescription: String? = null,
    val developer: String? = null,
    @SerialName("developer_id") val developerId: String? = null,
    val category: String? = null,
    @SerialName("icon_url") val iconUrl: String? = null,
    val screenshots: List<String> = emptyList(),
    val version: String? = null,
    @SerialName("version_code") val versionCode: Long? = null,
    val downloads: Long = 0,
    val status: GameStatus = GameStatus.DRAFT,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null,
    @SerialName("game_versions") val gameVersions: List<GameVersionRow> = emptyList(),
    @SerialName("game_categories") val gameCategories: List<GameCategoryJoinRow> = emptyList(),
)

@Serializable
data class GameVersionRow(
    val id: String,
    @SerialName("game_id") val gameId: String,
    val version: String,
    @SerialName("version_code") val versionCode: Long? = null,
    @SerialName("release_notes") val releaseNotes: String? = null,
    @SerialName("apk_url") val apkUrl: String,
    @SerialName("apk_file_name") val apkFileName: String? = null,
    @SerialName("apk_size_bytes") val apkSizeBytes: Long? = null,
    @SerialName("release_tag") val releaseTag: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
)

/** Linha de `game_categories` com a categoria embutida. */
@Serializable
data class GameCategoryJoinRow(
    @SerialName("game_id") val gameId: String? = null,
    @SerialName("category_id") val categoryId: String? = null,
    val categories: CategoryRow? = null,
)

/** Linha da vista `v_categories` (com contagem de jogos publicados). */
@Serializable
data class CategoryRow(
    val id: String,
    val slug: String,
    val name: String,
    val description: String? = null,
    @SerialName("icon_url") val iconUrl: String? = null,
    @SerialName("sort_order") val sortOrder: Int = 0,
    @SerialName("games_count") val gamesCount: Long = 0,
)

/** Linha de `profiles` (um por utilizador do Neon Auth). */
@Serializable
data class ProfileRow(
    val id: String,
    @SerialName("display_name") val displayName: String? = null,
    @SerialName("avatar_url") val avatarUrl: String? = null,
    val role: String = "user",
    @SerialName("created_at") val createdAt: String? = null,
)

/** Corpo para criar o próprio perfil (o id tem de ser o do utilizador). */
@Serializable
data class UpsertProfileBody(
    val id: String,
    @SerialName("display_name") val displayName: String? = null,
    @SerialName("avatar_url") val avatarUrl: String? = null,
    val role: String = "user",
)

/** Corpo para atualizar o próprio perfil (nunca toca no role). */
@Serializable
data class UpdateProfileRowBody(
    @SerialName("display_name") val displayName: String? = null,
    @SerialName("avatar_url") val avatarUrl: String? = null,
)

/** Linha de `library` (jogos na biblioteca do utilizador). */
@Serializable
data class LibraryRow(
    val id: String? = null,
    @SerialName("user_id") val userId: String,
    @SerialName("game_id") val gameId: String,
    @SerialName("game_version_id") val gameVersionId: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
    val games: GameRow? = null,
)

/** Linha de `favorites`. */
@Serializable
data class FavoriteRow(
    val id: String? = null,
    @SerialName("user_id") val userId: String,
    @SerialName("game_id") val gameId: String,
    @SerialName("created_at") val createdAt: String? = null,
    val games: GameRow? = null,
)

/** Linha de `reviews` (com o perfil do autor embutido). */
@Serializable
data class ReviewRow(
    val id: String,
    @SerialName("game_id") val gameId: String,
    @SerialName("user_id") val userId: String,
    val rating: Int,
    val comment: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null,
    val profiles: ProfileRow? = null,
)

/** Corpo para criar/atualizar review. */
@Serializable
data class UpsertReviewBody(
    @SerialName("game_id") val gameId: String,
    @SerialName("user_id") val userId: String,
    val rating: Int,
    val comment: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null,
)

/** Corpo do RPC register_download. */
@Serializable
data class RegisterDownloadBody(
    @SerialName("p_game_id") val gameId: String,
    @SerialName("p_version_id") val versionId: String? = null,
)

/** Corpos de escrita admin (games / game_versions / game_categories). */
@Serializable
data class CreateGameBody(
    val name: String,
    val slug: String,
    val description: String? = null,
    @SerialName("short_description") val shortDescription: String? = null,
    val developer: String? = null,
    @SerialName("developer_id") val developerId: String? = null,
    @SerialName("icon_url") val iconUrl: String? = null,
    val screenshots: List<String> = emptyList(),
    val status: String = "draft",
    val version: String? = null,
    @SerialName("version_code") val versionCode: Long? = null,
)

@Serializable
data class UpdateGameBody(
    val name: String? = null,
    val description: String? = null,
    @SerialName("short_description") val shortDescription: String? = null,
    @SerialName("icon_url") val iconUrl: String? = null,
    val screenshots: List<String>? = null,
    val status: String? = null,
    val version: String? = null,
    @SerialName("version_code") val versionCode: Long? = null,
    @SerialName("developer_id") val developerId: String? = null,
)

@Serializable
data class CreateVersionBody(
    @SerialName("game_id") val gameId: String,
    val version: String,
    @SerialName("version_code") val versionCode: Long? = null,
    @SerialName("release_notes") val releaseNotes: String? = null,
    @SerialName("apk_url") val apkUrl: String,
    @SerialName("apk_size_bytes") val apkSizeBytes: Long? = null,
    @SerialName("release_tag") val releaseTag: String? = null,
)

@Serializable
data class GameCategoryLinkBody(
    @SerialName("game_id") val gameId: String,
    @SerialName("category_id") val categoryId: String,
)

/** Erro devolvido pela Data API (PostgREST) ou pelo Neon Auth. */
@Serializable
data class ApiErrorBody(
    val code: String? = null,
    val message: String? = null,
    val details: String? = null,
    val hint: String? = null,
)

/** Exceção de API com o código e mensagem REAIS do servidor. */
class ApiException(
    val httpCode: Int,
    val errorCode: String?,
    override val message: String,
) : Exception("[$httpCode${errorCode?.let { "/$it" } ?: ""}] $message")

/** Sinaliza "não autenticado" para a UI pedir login. */
class NotAuthenticatedException : Exception("not_authenticated")
