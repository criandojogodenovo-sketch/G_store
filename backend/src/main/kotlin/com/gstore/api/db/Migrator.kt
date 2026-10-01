package com.gstore.api.db

import java.sql.Connection

/**
 * Executor de migrações SQL idempotentes embutidas no jar
 * (classpath: /db/migrations/V*.sql).
 *
 * - Controla o que já foi aplicado na tabela schema_migrations.
 * - As migrações também são idempotentes por si (CREATE ... IF NOT EXISTS),
 *   como cinto e suspensório.
 * - Suporta corpos plpgsql com $$ ... $$ (o splitter respeita dollar-quoting).
 */
class Migrator(private val database: Database) {

    fun migrate() {
        val statements = loadMigrations()
        database.withConnection { conn ->
            conn.createStatement().use { st ->
                st.execute(
                    """
                    CREATE TABLE IF NOT EXISTS schema_migrations (
                        version TEXT PRIMARY KEY,
                        applied_at TIMESTAMPTZ NOT NULL DEFAULT now()
                    )
                    """.trimIndent()
                )
            }
            for ((version, sql) in statements) {
                val alreadyApplied = conn.createStatement().use { st ->
                    st.executeQuery("SELECT 1 FROM schema_migrations WHERE version = '$version'").next()
                }
                if (alreadyApplied) continue
                for (statement in splitStatements(sql)) {
                    if (statement.isBlank()) continue
                    conn.createStatement().use { st -> st.execute(statement) }
                }
                conn.createStatement().use { st ->
                    st.execute("INSERT INTO schema_migrations (version) VALUES ('$version') ON CONFLICT (version) DO NOTHING")
                }
            }
        }
    }

    private fun loadMigrations(): List<Pair<String, String>> {
        val dir = Migrator::class.java.classLoader.getResource("db/migrations")
            ?: return emptyList()
        val files = if (dir.protocol == "jar") {
            // Dentro do jar: não é possível listar via File; usa lista conhecida ordenada.
            // As migrações são nomeadas V001..., V002... e registradas aqui conforme adicionadas.
            listOf("V001__gstore_schema.sql")
        } else {
            val fileList = java.io.File(dir.toURI()).listFiles()
                ?.filter { it.name.endsWith(".sql") }
                ?.map { it.name }
                ?.sorted()
                ?: emptyList()
            fileList
        }
        return files.map { name ->
            val text = Migrator::class.java.classLoader
                .getResourceAsStream("db/migrations/$name")!!
                .readBytes()
                .toString(Charsets.UTF_8)
            name.substringBefore("__").removePrefix("V") to text
        }
    }

    companion object {
        /**
         * Divide o script SQL em statements respeitando:
         * - strings '...' (não divide dentro)
         * - dollar-quoting $$...$$ e $tag$...$tag$ (corpos de função)
         * - comentários -- e /* */
         */
        fun splitStatements(script: String): List<String> {
            val out = mutableListOf<String>()
            val sb = StringBuilder()
            var i = 0
            var dollarTag: String? = null
            val n = script.length
            while (i < n) {
                val c = script[i]
                if (dollarTag != null) {
                    if (script.startsWith(dollarTag, i)) {
                        sb.append(dollarTag)
                        i += dollarTag.length
                        dollarTag = null
                    } else {
                        sb.append(c)
                        i++
                    }
                    continue
                }
                if (c == '-' && i + 1 < n && script[i + 1] == '-') {
                    // Comentário de linha: descartado (não vai para o statement final).
                    while (i < n && script[i] != '\n') i++
                    sb.append(' ')
                    continue
                }
                if (c == '/' && i + 1 < n && script[i + 1] == '*') {
                    // Comentário de bloco: descartado.
                    i += 2
                    while (i + 1 < n && !(script[i] == '*' && script[i + 1] == '/')) i++
                    i += 2
                    sb.append(' ')
                    continue
                }
                if (c == '\'') {
                    // string literal (com escape '' )
                    sb.append(c); i++
                    while (i < n) {
                        sb.append(script[i])
                        if (script[i] == '\'') {
                            if (i + 1 < n && script[i + 1] == '\'') {
                                sb.append('\''); i += 2; continue
                            }
                            i++
                            break
                        }
                        i++
                    }
                    continue
                }
                if (c == '$') {
                    val tag = Regex("^\\$[A-Za-z_]*\\$").find(script.substring(i))?.value
                    if (tag != null) {
                        dollarTag = tag
                        sb.append(tag)
                        i += tag.length
                        continue
                    }
                }
                if (c == ';') {
                    out.add(sb.toString().trim())
                    sb.clear()
                    i++
                    continue
                }
                sb.append(c)
                i++
            }
            if (sb.isNotBlank()) out.add(sb.toString().trim())
            return out
        }
    }
}
