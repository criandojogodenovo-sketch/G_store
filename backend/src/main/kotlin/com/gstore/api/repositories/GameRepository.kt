package com.gstore.api.repositories

import com.gstore.api.db.Database
import com.gstore.api.models.Game
import com.gstore.api.models.GameStatus
import com.gstore.api.models.GameVersion
import java.sql.Connection
import java.sql.ResultSet
import java.util.UUID

/**
 * Catálogo de jogos e versões no Neon PostgreSQL.
 *
 * Compatível com o schema legado (colunas name, slug, developer, version,
 * download_url...), que permanece preenchido para não quebrar o Worker antigo.
 */
class GameRepository(private val db: Database) {

    data class GameFilters(
        val publishedOnly: Boolean = true,
        val search: String? = null,
        val category: String? = null,
        val ownerDeveloperId: UUID? = null,
        val status: GameStatus? = null,
        val limit: Int = 50,
        val offset: Int = 0,
        val sort: Sort = Sort.NEWEST,
    ) {
        enum class Sort { NEWEST, MOST_DOWNLOADED, NAME }
    }

    fun listGames(filters: GameFilters): List<Game> =
        db.withConnection { conn ->
            val where = mutableListOf<String>()
            val args = mutableListOf<Any?>()

            if (filters.publishedOnly) {
                where.add("g.status = 'published'")
            } else if (filters.status != null) {
                where.add("g.status = ?")
                args.add(filters.status.value)
            }
            filters.search?.takeIf { it.isNotBlank() }?.let {
                where.add("(g.name ILIKE ? OR g.description ILIKE ? OR g.developer ILIKE ?)")
                args.add("%$it%"); args.add("%$it%"); args.add("%$it%")
            }
            filters.category?.takeIf { it.isNotBlank() }?.let {
                // aceita nome ou slug da categoria (legado usa nome; novo usa slug)
                where.add("(LOWER(g.category) = LOWER(?) OR EXISTS (SELECT 1 FROM game_categories gc JOIN categories c ON c.id = gc.category_id WHERE gc.game_id = g.id AND LOWER(c.slug) = LOWER(?)))")
                args.add(it); args.add(it)
            }
            filters.ownerDeveloperId?.let {
                where.add("g.developer_id = ?")
                args.add(it)
            }

            val orderBy = when (filters.sort) {
                GameFilters.Sort.NEWEST -> "g.created_at DESC"
                GameFilters.Sort.MOST_DOWNLOADED -> "g.downloads DESC, g.created_at DESC"
                GameFilters.Sort.NAME -> "g.name ASC"
            }

            val sql = buildString {
                append(BASE_SELECT)
                if (where.isNotEmpty()) append(" WHERE ").append(where.joinToString(" AND "))
                append(" ORDER BY ").append(orderBy)
                append(" LIMIT ? OFFSET ?")
            }

            conn.prepareStatement(sql).use { ps ->
                var idx = 1
                for (a in args) bind(ps, idx++, a)
                ps.setInt(idx++, filters.limit.coerceIn(1, 100))
                ps.setInt(idx, filters.offset.coerceAtLeast(0))
                ps.executeQuery().use { rs ->
                    val out = mutableListOf<Game>()
                    while (rs.next()) out.add(rs.toGame())
                    out
                }
            }
        }

    fun findBySlug(slug: String, publishedOnly: Boolean = true): Game? {
        var sql = "$BASE_SELECT WHERE g.slug = ?"
        if (publishedOnly) sql += " AND g.status = 'published'"
        return db.withConnection { conn ->
            conn.prepareStatement(sql).use { ps ->
                ps.setString(1, slug)
                ps.executeQuery().use { rs -> if (rs.next()) rs.toGame() else null }
            }
        }
    }

    fun findById(id: UUID): Game? {
        val sql = "$BASE_SELECT WHERE g.id = ?"
        return db.withConnection { conn ->
            conn.prepareStatement(sql).use { ps ->
                ps.setObject(1, id)
                ps.executeQuery().use { rs -> if (rs.next()) rs.toGame() else null }
            }
        }
    }

    fun slugExists(slug: String): Boolean =
        db.withConnection { conn ->
            conn.prepareStatement("SELECT 1 FROM games WHERE slug = ?").use { ps ->
                ps.setString(1, slug)
                ps.executeQuery().use { rs -> rs.next() }
            }
        }

    /** Cria um jogo (rascunho por padrão). Mantém colunas legadas preenchidas. */
    fun insert(
        slug: String,
        name: String,
        description: String?,
        shortDescription: String?,
        developerName: String?,
        developerId: UUID?,
        category: String?,
        iconUrl: String?,
        screenshots: List<String>,
        status: GameStatus,
    ): Game =
        db.withConnection { conn ->
            conn.prepareStatement(
                """
                INSERT INTO games (slug, name, description, short_description, developer, developer_id,
                                   category, icon_url, screenshots, status, version, download_url)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, NULL, NULL)
                RETURNING id
                """.trimIndent()
            ).use { ps ->
                ps.setString(1, slug)
                ps.setString(2, name)
                ps.setString(3, description)
                ps.setString(4, shortDescription)
                ps.setString(5, developerName)
                if (developerId != null) ps.setObject(6, developerId) else ps.setNull(6, java.sql.Types.OTHER)
                ps.setString(7, category)
                ps.setString(8, iconUrl)
                ps.setArray(9, conn.createArrayOf("text", screenshots.toTypedArray()))
                ps.setString(10, status.value)
                ps.executeQuery().use { rs -> rs.next(); rs.getObject(1).toString() }
            }
        }.let { id -> findById(UUID.fromString(id))!! }

    fun update(
        id: UUID,
        name: String?,
        description: String?,
        shortDescription: String?,
        developerName: String?,
        category: String?,
        iconUrl: String?,
        screenshots: List<String>?,
        status: GameStatus?,
    ): Game {
        val updated = db.withConnection { conn ->
            val sets = mutableListOf(
                "updated_at = now()"
            )
            if (name != null) sets.add("name = ?")
            if (description != null) sets.add("description = ?")
            if (shortDescription != null) sets.add("short_description = ?")
            if (developerName != null) sets.add("developer = ?")
            if (category != null) sets.add("category = ?")
            if (iconUrl != null) sets.add("icon_url = ?")
            if (screenshots != null) sets.add("screenshots = ?")
            if (status != null) sets.add("status = ?")

            conn.prepareStatement(
                "UPDATE games SET ${sets.joinToString(", ")} WHERE id = ?"
            ).use { ps ->
                var idx = 1
                if (name != null) ps.setString(idx++, name)
                if (description != null) ps.setString(idx++, description)
                if (shortDescription != null) ps.setString(idx++, shortDescription)
                if (developerName != null) ps.setString(idx++, developerName)
                if (category != null) ps.setString(idx++, category)
                if (iconUrl != null) ps.setString(idx++, iconUrl)
                if (screenshots != null) ps.setArray(idx++, conn.createArrayOf("text", screenshots.toTypedArray()))
                if (status != null) ps.setString(idx++, status.value)
                ps.setObject(idx, id)
                ps.executeUpdate()
            }
        }
        if (updated == 0) throw NotFoundException("game_not_found")
        return findById(id) ?: throw NotFoundException("game_not_found")
    }

    fun delete(id: UUID): Boolean =
        db.withConnection { conn ->
            conn.prepareStatement("DELETE FROM games WHERE id = ?").use { ps ->
                ps.setObject(1, id)
                ps.executeUpdate() > 0
            }
        }

    /* ------------------------- Versões ------------------------- */

    fun listVersions(gameId: UUID): List<GameVersion> =
        db.withConnection { conn ->
            conn.prepareStatement(
                "SELECT * FROM game_versions WHERE game_id = ? ORDER BY created_at DESC"
            ).use { ps ->
                ps.setObject(1, gameId)
                ps.executeQuery().use { rs ->
                    val out = mutableListOf<GameVersion>()
                    while (rs.next()) out.add(rs.toVersion())
                    out
                }
            }
        }

    fun findVersionById(id: UUID): GameVersion? =
        db.withConnection { conn ->
            conn.prepareStatement("SELECT * FROM game_versions WHERE id = ?").use { ps ->
                ps.setObject(1, id)
                ps.executeQuery().use { rs -> if (rs.next()) rs.toVersion() else null }
            }
        }

    fun findLatestVersion(gameId: UUID): GameVersion? =
        db.withConnection { conn ->
            conn.prepareStatement(
                "SELECT * FROM game_versions WHERE game_id = ? ORDER BY created_at DESC LIMIT 1"
            ).use { ps ->
                ps.setObject(1, gameId)
                ps.executeQuery().use { rs -> if (rs.next()) rs.toVersion() else null }
            }
        }

    fun versionExists(gameId: UUID, version: String): Boolean =
        db.withConnection { conn ->
            conn.prepareStatement("SELECT 1 FROM game_versions WHERE game_id = ? AND version = ?").use { ps ->
                ps.setObject(1, gameId)
                ps.setString(2, version)
                ps.executeQuery().use { rs -> rs.next() }
            }
        }

    fun insertVersion(
        gameId: UUID,
        version: String,
        versionCode: Long?,
        releaseNotes: String?,
        apkUrl: String,
        apkFileName: String?,
        apkSizeBytes: Long?,
        apkAssetId: Long?,
        releaseId: Long?,
        releaseTag: String?,
        createdBy: UUID?,
    ): GameVersion =
        db.withConnection { conn ->
            conn.prepareStatement(
                """
                INSERT INTO game_versions (game_id, version, version_code, release_notes, apk_url,
                                           apk_file_name, apk_size_bytes, apk_asset_id, release_id, release_tag, created_by)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                RETURNING id
                """.trimIndent()
            ).use { ps ->
                ps.setObject(1, gameId)
                ps.setString(2, version)
                if (versionCode != null) ps.setLong(3, versionCode) else ps.setNull(3, java.sql.Types.BIGINT)
                ps.setString(4, releaseNotes)
                ps.setString(5, apkUrl)
                ps.setString(6, apkFileName)
                if (apkSizeBytes != null) ps.setLong(7, apkSizeBytes) else ps.setNull(7, java.sql.Types.BIGINT)
                if (apkAssetId != null) ps.setLong(8, apkAssetId) else ps.setNull(8, java.sql.Types.BIGINT)
                if (releaseId != null) ps.setLong(9, releaseId) else ps.setNull(9, java.sql.Types.BIGINT)
                ps.setString(10, releaseTag)
                if (createdBy != null) ps.setObject(11, createdBy) else ps.setNull(11, java.sql.Types.OTHER)
                ps.executeQuery().use { rs -> rs.next(); rs.getObject(1).toString() }
            }
        }.let { vid -> findVersionById(UUID.fromString(vid))!! }

    /**
     * Após publicar uma versão: atualiza versão corrente + URL de download
     * (colunas legadas) e publica o jogo.
     */
    fun markPublished(gameId: UUID, version: String, versionCode: Long?, downloadUrl: String) {
        db.withConnection { conn ->
            conn.prepareStatement(
                """
                UPDATE games SET
                    version = ?,
                    version_code = ?,
                    download_url = ?,
                    status = 'published',
                    updated_at = now()
                WHERE id = ?
                """.trimIndent()
            ).use { ps ->
                ps.setString(1, version)
                if (versionCode != null) ps.setLong(2, versionCode) else ps.setNull(2, java.sql.Types.BIGINT)
                ps.setString(3, downloadUrl)
                ps.setObject(4, gameId)
                ps.executeUpdate()
            }
        }
    }

    fun incrementDownloads(gameId: UUID) {
        db.withConnection { conn ->
            conn.prepareStatement("UPDATE games SET downloads = downloads + 1 WHERE id = ?").use { ps ->
                ps.setObject(1, gameId)
                ps.executeUpdate()
            }
        }
    }

    companion object {
        private val BASE_SELECT = """
            SELECT g.id, g.slug, g.name, g.description, g.short_description, g.developer,
                   g.developer_id, g.category, g.icon_url, g.screenshots, g.version, g.version_code,
                   g.downloads, g.status, g.created_at, g.updated_at,
                   COALESCE(
                       (SELECT ARRAY_AGG(c.name ORDER BY c.sort_order)
                        FROM game_categories gc JOIN categories c ON c.id = gc.category_id
                        WHERE gc.game_id = g.id),
                       '{}'
                   ) AS category_names
            FROM games g
        """.trimIndent()
    }
}

fun ResultSet.toGame(): Game = Game(
    id = getObject("id").toString(),
    slug = getString("slug"),
    name = getString("name"),
    description = getString("description"),
    shortDescription = getString("short_description"),
    developer = getString("developer"),
    developerId = getObject("developer_id")?.toString(),
    category = getString("category"),
    categories = getArray("category_names")?.let { a ->
        (a.array as? Array<*>)?.filterNotNull()?.map { it.toString() } ?: emptyList()
    } ?: emptyList(),
    iconUrl = getString("icon_url"),
    screenshots = getArray("screenshots")?.let { a ->
        (a.array as? Array<*>)?.filterNotNull()?.map { it.toString() } ?: emptyList()
    } ?: emptyList(),
    version = getString("version"),
    versionCode = getObject("version_code")?.let { (it as Number).toLong() },
    downloads = getLong("downloads"),
    status = GameStatus.from(getString("status")),
    createdAt = getObject("created_at")?.toString(),
    updatedAt = getObject("updated_at")?.toString(),
)

fun ResultSet.toVersion(): GameVersion = GameVersion(
    id = getObject("id").toString(),
    gameId = getObject("game_id").toString(),
    version = getString("version"),
    versionCode = getObject("version_code")?.let { (it as Number).toLong() },
    releaseNotes = getString("release_notes"),
    apkUrl = getString("apk_url"),
    apkFileName = getString("apk_file_name"),
    apkSizeBytes = getObject("apk_size_bytes")?.let { (it as Number).toLong() },
    apkAssetId = getObject("apk_asset_id")?.let { (it as Number).toLong() },
    releaseTag = getString("release_tag"),
    createdAt = getObject("created_at")?.toString(),
)

fun bind(ps: java.sql.PreparedStatement, index: Int, value: Any?) {
    when (value) {
        null -> ps.setNull(index, java.sql.Types.NULL)
        is String -> ps.setString(index, value)
        is Int -> ps.setInt(index, value)
        is Long -> ps.setLong(index, value)
        is Boolean -> ps.setBoolean(index, value)
        is java.util.UUID -> ps.setObject(index, value)
        else -> ps.setObject(index, value)
    }
}
