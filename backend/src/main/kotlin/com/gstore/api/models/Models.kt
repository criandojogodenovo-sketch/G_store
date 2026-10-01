package com.gstore.api.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Papéis suportados pela plataforma. */
@Serializable
enum class Role(val value: String) {
    @SerialName("user") USER("user"),
    @SerialName("developer") DEVELOPER("developer"),
    @SerialName("admin") ADMIN("admin");

    companion object {
        fun from(value: String?): Role =
            entries.firstOrNull { it.value == value?.lowercase() } ?: USER
    }
}

/** Status de publicação de um jogo (compatível com o legado). */
@Serializable
enum class GameStatus(val value: String) {
    @SerialName("draft") DRAFT("draft"),
    @SerialName("published") PUBLISHED("published"),
    @SerialName("unpublished") UNPUBLISHED("unpublished");

    companion object {
        fun from(value: String?): GameStatus =
            entries.firstOrNull { it.value == value?.lowercase() } ?: DRAFT
    }
}

@Serializable
data class UserProfile(
    val id: String,
    val appwriteUserId: String,
    val email: String?,
    val displayName: String?,
    val avatarUrl: String?,
    val role: Role,
    val createdAt: String?,
)

@Serializable
data class GameVersion(
    val id: String,
    val gameId: String,
    val version: String,
    val versionCode: Long? = null,
    val releaseNotes: String? = null,
    val apkUrl: String,
    val apkFileName: String? = null,
    val apkSizeBytes: Long? = null,
    val apkAssetId: Long? = null,
    val releaseTag: String? = null,
    val createdAt: String? = null,
)

@Serializable
data class Game(
    val id: String,
    val slug: String,
    val name: String,
    val description: String? = null,
    val shortDescription: String? = null,
    val developer: String? = null,
    val developerId: String? = null,
    val category: String? = null,
    val categories: List<String> = emptyList(),
    val iconUrl: String? = null,
    val screenshots: List<String> = emptyList(),
    val version: String? = null,
    val versionCode: Long? = null,
    val downloads: Long = 0,
    val status: GameStatus = GameStatus.DRAFT,
    val createdAt: String? = null,
    val updatedAt: String? = null,
)

@Serializable
data class Category(
    val id: String,
    val slug: String,
    val name: String,
    val description: String? = null,
    val iconUrl: String? = null,
    val sortOrder: Int = 0,
    val gamesCount: Long = 0,
)

/* ------------------------- DTOs de requisição ------------------------- */

@Serializable
data class CreateGameRequest(
    val name: String,
    val description: String? = null,
    val shortDescription: String? = null,
    val category: String? = null,
    val developerName: String? = null,
    val iconUrl: String? = null,
    val screenshots: List<String> = emptyList(),
    val status: GameStatus = GameStatus.DRAFT,
)

@Serializable
data class UpdateGameRequest(
    val name: String? = null,
    val description: String? = null,
    val shortDescription: String? = null,
    val category: String? = null,
    val developerName: String? = null,
    val iconUrl: String? = null,
    val screenshots: List<String>? = null,
    val status: GameStatus? = null,
)

@Serializable
data class UpdateProfileRequest(
    val displayName: String? = null,
    val avatarUrl: String? = null,
)

@Serializable
data class UpdateRoleRequest(
    val role: Role,
)

/* ------------------------- Envelopes de resposta ------------------------- */

@Serializable
data class ApiOk(val ok: Boolean = true, val data: kotlinx.serialization.json.JsonElement? = null)

@Serializable
data class ApiError(val ok: Boolean = false, val error: String, val message: String? = null)
