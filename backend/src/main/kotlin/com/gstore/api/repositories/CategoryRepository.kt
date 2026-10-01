package com.gstore.api.repositories

import com.gstore.api.db.Database
import com.gstore.api.models.Category
import java.sql.ResultSet
import java.util.UUID

class CategoryRepository(private val db: Database) {

    fun list(includeCounts: Boolean = true): List<Category> =
        db.withConnection { conn ->
            val sql = if (includeCounts) {
                """
                SELECT c.*, (
                    SELECT COUNT(*)::bigint FROM game_categories gc
                    JOIN games g ON g.id = gc.game_id
                    WHERE gc.category_id = c.id AND g.status = 'published'
                ) AS games_count
                FROM categories c ORDER BY c.sort_order ASC, c.name ASC
                """.trimIndent()
            } else {
                "SELECT c.*, 0::bigint AS games_count FROM categories c ORDER BY c.sort_order ASC, c.name ASC"
            }
            conn.prepareStatement(sql).use { ps ->
                ps.executeQuery().use { rs ->
                    val out = mutableListOf<Category>()
                    while (rs.next()) out.add(rs.toCategory())
                    out
                }
            }
        }

    fun findBySlugOrNull(slug: String): Category? =
        db.withConnection { conn ->
            conn.prepareStatement("SELECT c.*, 0::bigint AS games_count FROM categories c WHERE slug = ?").use { ps ->
                ps.setString(1, slug)
                ps.executeQuery().use { rs -> if (rs.next()) rs.toCategory() else null }
            }
        }

    /** Localiza categoria por nome ou slug, sem criar (retorna null se não existir). */
    fun findByNameOrSlugOrNull(value: String): Category? =
        db.withConnection { conn ->
            conn.prepareStatement(
                "SELECT c.*, 0::bigint AS games_count FROM categories c WHERE LOWER(name) = LOWER(?) OR slug = ?"
            ).use { ps ->
                ps.setString(1, value)
                ps.setString(2, slugify(value))
                ps.executeQuery().use { rs -> if (rs.next()) rs.toCategory() else null }
            }
        }

    fun linkGameToCategory(gameId: UUID, categoryId: UUID) {
        db.withConnection { conn ->
            conn.prepareStatement(
                "INSERT INTO game_categories (game_id, category_id) VALUES (?, ?) ON CONFLICT DO NOTHING"
            ).use { ps ->
                ps.setObject(1, gameId)
                ps.setObject(2, categoryId)
                ps.executeUpdate()
            }
        }
    }

    private fun ResultSet.toCategory(): Category = Category(
        id = getObject("id").toString(),
        slug = getString("slug"),
        name = getString("name"),
        description = getString("description"),
        iconUrl = getString("icon_url"),
        sortOrder = getInt("sort_order"),
        gamesCount = getLong("games_count"),
    )

    companion object {
        fun slugify(value: String): String = value
            .trim()
            .lowercase()
            .replace(Regex("[áàãâä]"), "a")
            .replace(Regex("[éèêë]"), "e")
            .replace(Regex("[íìîï]"), "i")
            .replace(Regex("[óòõôö]"), "o")
            .replace(Regex("[úùûü]"), "u")
            .replace("ç", "c")
            .replace(Regex("[^a-z0-9]+"), "-")
            .trim('-')
            .take(80)
            .ifEmpty { "categoria" }
    }
}
