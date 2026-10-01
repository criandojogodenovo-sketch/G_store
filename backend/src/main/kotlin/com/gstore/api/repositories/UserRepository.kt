package com.gstore.api.repositories

import com.gstore.api.db.Database
import com.gstore.api.models.Role
import com.gstore.api.models.UserProfile
import java.sql.ResultSet
import java.util.UUID

class NotFoundException(message: String) : RuntimeException(message)

/**
 * Perfis locais espelhando usuários do Appwrite no Neon.
 * A autenticação em si é do Appwrite; aqui guardamos apenas o perfil/role.
 */
class UserRepository(private val db: Database) {

    fun upsertFromAppwrite(appwriteUserId: String, email: String?, displayName: String?, avatarUrl: String?, adminEmails: Set<String>): UserProfile {
        val isDesignatedAdmin = email != null && email.lowercase() in adminEmails
        val existing = findByAppwriteIdOrNull(appwriteUserId)
        if (existing != null) {
            db.withConnection { conn ->
                conn.prepareStatement(
                    """
                    UPDATE users SET
                        email = COALESCE(?, email),
                        display_name = COALESCE(?, display_name),
                        avatar_url = COALESCE(?, avatar_url),
                        role = CASE WHEN ? THEN 'admin' ELSE role END,
                        updated_at = now()
                    WHERE appwrite_user_id = ?
                    """.trimIndent()
                ).use { ps ->
                    ps.setString(1, email)
                    ps.setString(2, displayName)
                    ps.setString(3, avatarUrl)
                    ps.setBoolean(4, isDesignatedAdmin)
                    ps.setString(5, appwriteUserId)
                    ps.executeUpdate()
                }
            }
            return findByAppwriteIdOrNull(appwriteUserId)!!
        }
        val initialRole = if (isDesignatedAdmin) Role.ADMIN else Role.USER
        db.withConnection { conn ->
            conn.prepareStatement(
                """
                INSERT INTO users (appwrite_user_id, email, display_name, avatar_url, role)
                VALUES (?, ?, ?, ?, ?)
                ON CONFLICT (appwrite_user_id) DO UPDATE SET
                    email = COALESCE(EXCLUDED.email, users.email),
                    display_name = COALESCE(EXCLUDED.display_name, users.display_name),
                    updated_at = now()
                """.trimIndent()
            ).use { ps ->
                ps.setString(1, appwriteUserId)
                ps.setString(2, email)
                ps.setString(3, displayName)
                ps.setString(4, avatarUrl)
                ps.setString(5, initialRole.value)
                ps.executeUpdate()
            }
        }
        return findByAppwriteIdOrNull(appwriteUserId)!!
    }

    fun findByAppwriteIdOrNull(appwriteUserId: String): UserProfile? =
        db.withConnection { conn ->
            conn.prepareStatement("SELECT * FROM users WHERE appwrite_user_id = ?").use { ps ->
                ps.setString(1, appwriteUserId)
                ps.executeQuery().use { rs -> if (rs.next()) rs.toUser() else null }
            }
        }

    fun findByIdOrNull(id: UUID): UserProfile? =
        db.withConnection { conn ->
            conn.prepareStatement("SELECT * FROM users WHERE id = ?").use { ps ->
                ps.setObject(1, id)
                ps.executeQuery().use { rs -> if (rs.next()) rs.toUser() else null }
            }
        }

    fun updateProfile(id: UUID, displayName: String?, avatarUrl: String?): UserProfile {
        val updated = db.withConnection { conn ->
            conn.prepareStatement(
                """
                UPDATE users SET
                    display_name = COALESCE(?, display_name),
                    avatar_url = COALESCE(?, avatar_url),
                    updated_at = now()
                WHERE id = ?
                """.trimIndent()
            ).use { ps ->
                ps.setString(1, displayName)
                ps.setString(2, avatarUrl)
                ps.setObject(3, id)
                ps.executeUpdate()
            }
        }
        if (updated == 0) throw NotFoundException("user_not_found")
        return findByIdOrNull(id) ?: throw NotFoundException("user_not_found")
    }

    fun updateRole(id: UUID, role: Role): UserProfile {
        val updated = db.withConnection { conn ->
            conn.prepareStatement("UPDATE users SET role = ?, updated_at = now() WHERE id = ?").use { ps ->
                ps.setString(1, role.value)
                ps.setObject(2, id)
                ps.executeUpdate()
            }
        }
        if (updated == 0) throw NotFoundException("user_not_found")
        return findByIdOrNull(id)!!
    }

    fun becomeDeveloper(id: UUID): UserProfile =
        db.withConnection { conn ->
            conn.prepareStatement(
                "UPDATE users SET role = 'developer', updated_at = now() WHERE id = ? AND role IN ('user','developer')"
            ).use { ps ->
                ps.setObject(1, id)
                ps.executeUpdate()
            }
        }
            .let { findByIdOrNull(id) ?: throw NotFoundException("user_not_found") }

    fun listAll(limit: Int = 100, offset: Int = 0): List<UserProfile> =
        db.withConnection { conn ->
            conn.prepareStatement("SELECT * FROM users ORDER BY created_at DESC LIMIT ? OFFSET ?").use { ps ->
                ps.setInt(1, limit)
                ps.setInt(2, offset)
                ps.executeQuery().use { rs ->
                    val out = mutableListOf<UserProfile>()
                    while (rs.next()) out.add(rs.toUser())
                    out
                }
            }
        }

    private fun ResultSet.toUser(): UserProfile = UserProfile(
        id = getObject("id").toString(),
        appwriteUserId = getString("appwrite_user_id"),
        email = getString("email"),
        displayName = getString("display_name"),
        avatarUrl = getString("avatar_url"),
        role = Role.from(getString("role")),
        createdAt = getObject("created_at")?.toString(),
    )
}
