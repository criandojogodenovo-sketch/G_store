package com.gstore.api.services

import com.gstore.api.models.Game
import com.gstore.api.models.GameVersion
import com.gstore.api.repositories.CategoryRepository
import com.gstore.api.repositories.GameRepository
import com.gstore.api.repositories.UserRepository
import com.gstore.api.util.Slug
import java.nio.file.Path
import java.util.UUID

/**
 * Orquestra a publicação de uma versão de jogo:
 *
 * 1. valida dados + arquivos (APK obrigatório; ícone/screenshots opcionais);
 * 2. cria/localiza a GitHub Release correspondente à versão;
 * 3. envia APK (e imagens) como assets em streaming;
 * 4. registra no Neon a relação game -> game_version -> release/asset;
 * 5. publica o jogo (status published) e atualiza versão corrente.
 *
 * Fluxo visto pelo developer no app: "Publicar" -> "Enviando APK..." ->
 * "Processando..." -> "Publicado".
 */
class PublishService(
    private val games: GameRepository,
    private val categories: CategoryRepository,
    private val users: UserRepository,
    private val github: GitHubService,
) {

    data class UploadedFile(
        val path: Path,
        val originalName: String,
        val contentType: String,
        val size: Long,
    )

    data class PublishVersionInput(
        val gameId: UUID,
        val developerId: UUID,
        val version: String,
        val versionCode: Long?,
        val releaseNotes: String?,
        val apk: UploadedFile,
        val icon: UploadedFile?,
        val screenshots: List<UploadedFile>,
        val publish: Boolean = true,
    )

    fun publishVersion(input: PublishVersionInput): Pair<Game, GameVersion> {
        val game = games.findById(input.gameId)
            ?: throw com.gstore.api.repositories.NotFoundException("game_not_found")

        if (games.versionExists(input.gameId, input.version)) {
            throw IllegalArgumentException("version_already_exists")
        }

        // Tag determinística por jogo/versão.
        val tag = "game-${game.slug}-v${Slug.sanitizeVersion(input.version)}"
        val releaseName = "${game.name} ${input.version}"
        val releaseBody = input.releaseNotes ?: "Release ${input.version} de ${game.name}"

        val release = github.getOrCreateRelease(tag, releaseName, releaseBody)

        val apkFileName = "${game.slug}-${Slug.sanitizeVersion(input.version)}.apk"
        val apkAsset = github.uploadAsset(release, apkFileName, "application/vnd.android.package-archive", input.apk.path)

        // Ícone (opcional) -> vira asset da release e icon_url do jogo.
        var iconUrl: String? = null
        input.icon?.let { icon ->
            val ext = icon.originalName.substringAfterLast('.', "png").lowercase()
            val name = "${game.slug}-icon.$ext"
            val asset = github.uploadAsset(release, name, icon.contentType.ifBlank { "image/png" }, icon.path)
            iconUrl = asset.browserDownloadUrl
        }

        // Screenshots (opcionais) -> assets da release + array no jogo.
        val screenshotUrls = mutableListOf<String>()
        input.screenshots.forEachIndexed { index, shot ->
            val ext = shot.originalName.substringAfterLast('.', "png").lowercase()
            val name = "${game.slug}-screen-${index + 1}.$ext"
            val asset = github.uploadAsset(release, name, shot.contentType.ifBlank { "image/png" }, shot.path)
            screenshotUrls.add(asset.browserDownloadUrl)
        }

        val createdVersion = games.insertVersion(
            gameId = input.gameId,
            version = input.version,
            versionCode = input.versionCode,
            releaseNotes = input.releaseNotes,
            apkUrl = apkAsset.browserDownloadUrl,
            apkFileName = apkAsset.name,
            apkSizeBytes = apkAsset.size,
            apkAssetId = apkAsset.id,
            releaseId = release.id,
            releaseTag = tag,
            createdBy = input.developerId,
        )

        if (iconUrl != null || screenshotUrls.isNotEmpty()) {
            val current = games.findById(input.gameId)
            games.update(
                id = input.gameId,
                name = null,
                description = null,
                shortDescription = null,
                developerName = null,
                category = null,
                iconUrl = iconUrl ?: current?.iconUrl,
                screenshots = if (screenshotUrls.isNotEmpty()) screenshotUrls else current?.screenshots,
                status = null,
            )
        }

        if (input.publish) {
            games.markPublished(input.gameId, input.version, input.versionCode, apkAsset.browserDownloadUrl)
        }

        val updatedGame = games.findById(input.gameId) ?: game
        return updatedGame to createdVersion
    }

    /** Cria um jogo em rascunho, resolvendo categoria por nome/slug e vínculo N:N. */
    fun createGame(
        name: String,
        description: String?,
        shortDescription: String?,
        developerId: UUID,
        developerDisplayName: String?,
        categoryValue: String?,
        iconUrl: String?,
        screenshots: List<String>,
        status: com.gstore.api.models.GameStatus,
    ): Game {
        val baseSlug = Slug.slugify(name)
        val slug = Slug.uniqueSlug(baseSlug) { candidate -> games.slugExists(candidate) }
        val category = categoryValue?.let { categories.findByNameOrSlugOrNull(it) }
        val developerName = developerDisplayName ?: "Desenvolvedor G Store"
        val created = games.insert(
            slug = slug,
            name = name,
            description = description,
            shortDescription = shortDescription,
            developerName = developerName,
            developerId = developerId,
            category = category?.name,
            iconUrl = iconUrl,
            screenshots = screenshots,
            status = status,
        )
        if (category != null) {
            categories.linkGameToCategory(created.id.let(UUID::fromString), category.id.let(UUID::fromString))
        }
        return created
    }
}
