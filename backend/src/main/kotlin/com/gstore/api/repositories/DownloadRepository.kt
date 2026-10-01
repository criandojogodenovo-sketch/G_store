package com.gstore.api.repositories

import com.gstore.api.db.Database
import java.util.UUID

/**
 * Registro analítico de downloads (game_downloads) e estatísticas.
 */
class DownloadRepository(private val db: Database) {

    fun record(gameId: UUID, versionId: UUID?, userId: UUID?) {
        db.withConnection { conn ->
            conn.prepareStatement(
                "INSERT INTO game_downloads (game_id, game_version_id, user_id) VALUES (?, ?, ?)"
            ).use { ps ->
                ps.setObject(1, gameId)
                if (versionId != null) ps.setObject(2, versionId) else ps.setNull(2, java.sql.Types.OTHER)
                if (userId != null) ps.setObject(3, userId) else ps.setNull(3, java.sql.Types.OTHER)
                ps.executeUpdate()
            }
        }
    }

    @kotlinx.serialization.Serializable
    data class GameDownloadStats(
        val total: Long,
        val last7Days: Long,
        val last30Days: Long,
        val byDay: List<ByDay>,
    ) {
        @kotlinx.serialization.Serializable
        data class ByDay(val day: String, val count: Long)
    }

    fun statsForGame(gameId: UUID): GameDownloadStats =
        db.withConnection { conn ->
            val total = conn.prepareStatement(
                "SELECT COUNT(*)::bigint FROM game_downloads WHERE game_id = ?"
            ).use { ps ->
                ps.setObject(1, gameId)
                ps.executeQuery().use { rs -> rs.next(); rs.getLong(1) }
            }
            val last7 = conn.prepareStatement(
                "SELECT COUNT(*)::bigint FROM game_downloads WHERE game_id = ? AND created_at >= now() - INTERVAL '7 days'"
            ).use { ps ->
                ps.setObject(1, gameId)
                ps.executeQuery().use { rs -> rs.next(); rs.getLong(1) }
            }
            val last30 = conn.prepareStatement(
                "SELECT COUNT(*)::bigint FROM game_downloads WHERE game_id = ? AND created_at >= now() - INTERVAL '30 days'"
            ).use { ps ->
                ps.setObject(1, gameId)
                ps.executeQuery().use { rs -> rs.next(); rs.getLong(1) }
            }
            val byDay = conn.prepareStatement(
                """
                SELECT DATE(created_at) AS day, COUNT(*)::bigint AS n
                FROM game_downloads
                WHERE game_id = ? AND created_at >= now() - INTERVAL '30 days'
                GROUP BY day ORDER BY day ASC
                """.trimIndent()
            ).use { ps ->
                ps.setObject(1, gameId)
                ps.executeQuery().use { rs ->
                    val out = mutableListOf<GameDownloadStats.ByDay>()
                    while (rs.next()) out.add(GameDownloadStats.ByDay(rs.getDate("day").toString(), rs.getLong("n")))
                    out
                }
            }
            GameDownloadStats(total, last7, last30, byDay)
        }
}
