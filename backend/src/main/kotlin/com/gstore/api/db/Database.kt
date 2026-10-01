package com.gstore.api.db

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import java.sql.Connection
import java.sql.SQLException

/**
 * Pool de conexões com o Neon PostgreSQL.
 *
 * A connection string vem exclusivamente da variável de ambiente DATABASE_URL
 * e NUNCA é registrada em logs nem retornada pela API.
 *
 * Observação: a URL do Neon pode conter o parâmetro `channel_binding=require`,
 * que é do cliente libpq e não do driver JDBC; ele é removido defensivamente.
 */
class Database(databaseUrl: String) {

    val dataSource: HikariDataSource

    init {
        val jdbcUrl = sanitizeForJdbc(databaseUrl)
        val config = HikariConfig().apply {
            // Usa o DataSource do PostgreSQL diretamente — mais confiável do que
            // DriverManager/ServiceLoader (evita "Failed to get driver instance"
            // em workers de teste e ambientes com classloader restrito).
            val pgDataSource = org.postgresql.ds.PGSimpleDataSource()
            val (urlSemCredenciais, user, password) = splitCredentials(jdbcUrl)
            pgDataSource.setURL(urlSemCredenciais)
            if (user != null) pgDataSource.setUser(user)
            if (password != null) pgDataSource.setPassword(password)
            dataSource = pgDataSource
            maximumPoolSize = 5
            minimumIdle = 1
            idleTimeout = 60_000
            connectionTimeout = 15_000
            maxLifetime = 5 * 60_000
            poolName = "g-store-pool"
            // Neon encerra conexões ociosas; manter validação leve.
            connectionTestQuery = "SELECT 1"
        }
        dataSource = HikariDataSource(config)
    }

    fun <T> withConnection(block: (Connection) -> T): T =
        dataSource.connection.use { conn -> block(conn) }

    fun <T> withTransaction(block: (Connection) -> T): T =
        dataSource.connection.use { conn ->
            conn.autoCommit = false
            try {
                val result = block(conn)
                conn.commit()
                result
            } catch (e: Exception) {
                try {
                    conn.rollback()
                } catch (ignored: SQLException) {
                    // rollback best-effort
                }
                throw e
            }
        }

    fun healthCheck(): Boolean = try {
        withConnection { conn ->
            conn.createStatement().use { st -> st.executeQuery("SELECT 1").next() }
        }
    } catch (e: Exception) {
        false
    }

    fun close() {
        dataSource.close()
    }

    companion object {
        /**
         * Ajusta a URL para o driver JDBC:
         * - troca prefixo postgresql:// -> jdbc:postgresql://
         * - remove channel_binding (parâmetro do libpq, não do JDBC)
         */
        fun sanitizeForJdbc(url: String): String {
            var u = url.trim()
            if (u.startsWith("postgres://")) u = "postgresql://" + u.removePrefix("postgres://")
            if (!u.startsWith("jdbc:postgresql://")) u = "jdbc:postgresql://" + u.removePrefix("postgresql://")
            u = u.replace(Regex("([?&])channel_binding=[^&]*&?"), "$1").trimEnd('&', '?')
            return u
        }

        /**
         * Separa credenciais embutidas (user:pass@host) da URL JDBC.
         *
         * O driver PostgreSQL JDBC (pgjdbc) não interpreta user:password@host
         * na URL — credenciais devem ir como propriedades do DataSource.
         * (Observação: assume credenciais sem '%' não codificado; a connection
         * string do Neon usa formato compatível.)
         */
        fun splitCredentials(jdbcUrl: String): Triple<String, String?, String?> {
            val match = Regex("^jdbc:postgresql://([^/@?:]+):([^/@?:]+)@(.*)$").find(jdbcUrl)
            return if (match == null) {
                Triple(jdbcUrl, null, null)
            } else {
                val (user, pass, rest) = match.destructured
                Triple("jdbc:postgresql://$rest", user, pass)
            }
        }
    }
}
