package com.gstore.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Testes do verificador de configuração do Neon (valores públicos do
 * cliente: URLs do Auth/Data API e origem de confiança). Não existem
 * segredos envolvidos — a autorização é feita por RLS no Postgres.
 */
class ConfigCheckTest {

    private val authUrl = "https://ep-exemplo.neonauth.us-east-2.aws.neon.tech/neondb/auth"
    private val dataApiUrl = "https://ep-exemplo.apirest.us-east-2.aws.neon.tech/neondb/rest/v1"
    private val origin = "https://gstore.app"

    @Test
    fun `configuração completa não devolve faltas`() {
        val faltam = ConfigCheck.missingValues(authUrl, dataApiUrl, origin)
        assertTrue("Não devia faltar nada", faltam.isEmpty())
    }

    @Test
    fun `auth url em branco é detetado`() {
        val faltam = ConfigCheck.missingValues("", dataApiUrl, origin)
        assertEquals(listOf("NEON_AUTH_URL"), faltam)
    }

    @Test
    fun `data api url em branco é detetado`() {
        val faltam = ConfigCheck.missingValues(authUrl, "", origin)
        assertEquals(listOf("NEON_DATA_API_URL"), faltam)
    }

    @Test
    fun `origin em branco é detetado`() {
        val faltam = ConfigCheck.missingValues(authUrl, dataApiUrl, "")
        assertEquals(listOf("NEON_AUTH_ORIGIN"), faltam)
    }

    @Test
    fun `url não https é recusada`() {
        val faltam = ConfigCheck.missingValues("http://ep-exemplo.neonauth...", dataApiUrl, origin)
        assertEquals(listOf("NEON_AUTH_URL"), faltam)
    }

    @Test
    fun `origin não https é recusada`() {
        val faltam = ConfigCheck.missingValues(authUrl, dataApiUrl, "http://gstore.app")
        assertEquals(listOf("NEON_AUTH_ORIGIN"), faltam)
    }

    @Test
    fun `tudo em falta devolve os três valores`() {
        val faltam = ConfigCheck.missingValues(" ", " ", " ")
        assertEquals(
            listOf("NEON_AUTH_URL", "NEON_DATA_API_URL", "NEON_AUTH_ORIGIN"),
            faltam,
        )
    }
}
