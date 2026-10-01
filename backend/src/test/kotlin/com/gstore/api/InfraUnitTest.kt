package com.gstore.api

import com.gstore.api.db.Database
import com.gstore.api.db.Migrator
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class InfraUnitTest {

    private val dollars = "\$\$"

    @Test
    fun `sanitizeForJdbc converte URLs do Neon para JDBC`() {
        assertEquals(
            "jdbc:postgresql://host/db?sslmode=require",
            Database.sanitizeForJdbc("postgresql://host/db?sslmode=require"),
        )
        assertEquals(
            "jdbc:postgresql://host/db?sslmode=require",
            Database.sanitizeForJdbc("postgres://host/db?sslmode=require&channel_binding=require"),
        )
        assertEquals(
            "jdbc:postgresql://host-pooler.db/aws.neon.tech/neondb?sslmode=require",
            Database.sanitizeForJdbc(
                "postgresql://host-pooler.db/aws.neon.tech/neondb?sslmode=require&channel_binding=require",
            ),
        )
    }

    @Test
    fun `splitStatements respeita dollar-quoting e comentarios`() {
        val script = """
            -- comentário; com ponto e vírgula
            CREATE TABLE t (id INT);
            CREATE OR REPLACE FUNCTION f() RETURNS trigger AS ${dollars}
            BEGIN
              NEW.updated_at = now(); -- segundo; ponto e vírgula
              RETURN NEW;
            END;
            ${dollars} LANGUAGE plpgsql;
            INSERT INTO t VALUES ('texto; com ponto e vírgula');
        """.trimIndent()

        val statements = Migrator.splitStatements(script)
        assertEquals(3, statements.size, "deve dividir em 3 statements: $statements")
        assertTrue(statements[0].startsWith("CREATE TABLE"))
        assertTrue(statements[1].startsWith("CREATE OR REPLACE FUNCTION"))
        assertTrue(statements[1].contains("NEW.updated_at = now();"))
        assertTrue(statements[2].contains("texto; com ponto e vírgula"))
    }

    @Test
    fun `splitStatements com script vazio retorna vazio`() {
        assertTrue(Migrator.splitStatements("").isEmpty())
    }
}
