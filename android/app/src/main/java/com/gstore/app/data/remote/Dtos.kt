package com.gstore.app.data.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class GameStatus {
    @SerialName("draft") DRAFT,
    @SerialName("published") PUBLISHED,
    @SerialName("unpublished") UNPUBLISHED,
}

@Serializable
enum class Role {
    @SerialName("user") USER,
    @SerialName("developer") DEVELOPER,
    @SerialName("admin") ADMIN,
}

@Serializable
data class GameDto(
    val id: String,
    val slug: String,
    val name: String,
    val description: String? = null,
    @SerialName("short_description") val shortDescription: String? = null,
    val developer: String? = null,
    @SerialName("developer_id") val developerId: String? = null,
    val category: String? = null,
    val categories: List<String> = emptyList(),
    @SerialName("icon_url") val iconUrl: String? = null,
    val screenshots: List<String> = emptyList(),
    val version: String? = null,
    @SerialName("version_code") val versionCode: Long? = null,
    val downloads: Long = 0,
    val status: GameStatus = GameStatus.DRAFT,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null,
)

@Serializable
data class GameVersionDto(
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

@Serializable
data class CategoryDto(
    val id: String,
    val slug: String,
    val name: String,
    val description: String? = null,
    @SerialName("icon_url") val iconUrl: String? = null,
    @SerialName("sort_order") val sortOrder: Int = 0,
    @SerialName("games_count") val gamesCount: Long = 0,
)

@Serializable
data class UserProfileDto(
    val id: String,
    @SerialName("appwrite_user_id") val appwriteUserId: String,
    val email: String? = null,
    @SerialName("display_name") val displayName: String? = null,
    @SerialName("avatar_url") val avatarUrl: String? = null,
    val role: Role = Role.USER,
    @SerialName("created_at") val createdAt: String? = null,
)

/* ---------------- Envelopes de resposta da API ---------------- */

@Serializable
data class GameListResponse(
    val ok: Boolean = true,
    val count: Int = 0,
    val games: List<GameDto> = emptyList(),
)

@Serializable
data class CategoryListResponse(
    val ok: Boolean = true,
    val count: Int = 0,
    val categories: List<CategoryDto> = emptyList(),
)

@Serializable
data class CategoryDetailResponse(
    val ok: Boolean = true,
    val category: CategoryDto,
    val count: Int = 0,
    val games: List<GameDto> = emptyList(),
)

@Serializable
data class GameDataResponse(
    val ok: Boolean = true,
    val data: GameDto,
)

@Serializable
data class VersionListResponse(
    val ok: Boolean = true,
    @SerialName("game_id") val gameId: String,
    val count: Int = 0,
    val versions: List<GameVersionDto> = emptyList(),
)

@Serializable
data class ProfileDataResponse(
    val ok: Boolean = true,
    val data: UserProfileDto,
)

@Serializable
data class PublishDataResponse(
    val ok: Boolean = true,
    val data: PublishData,
)

@Serializable
data class PublishData(
    val game: GameDto,
    val version: GameVersionDto,
)

@Serializable
data class DownloadStatsResponse(
    val ok: Boolean = true,
    @SerialName("game_id") val gameId: String,
    val counter: Long = 0,
    val stats: DownloadStatsDto,
)

@Serializable
data class DownloadStatsDto(
    val total: Long = 0,
    @SerialName("last7Days") val last7Days: Long = 0,
    @SerialName("last30Days") val last30Days: Long = 0,
)

@Serializable
data class ErrorResponse(
    val ok: Boolean = false,
    val error: String = "unknown_error",
    val message: String? = null,
)
