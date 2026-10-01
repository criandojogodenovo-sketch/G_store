package com.gstore.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Testes do verificador de configuração do Appwrite (valores públicos
 * do cliente). A APPWRITE_API_KEY nunca faz parte desta verificação
 * porque é segredo exclusivo do backend.
 */
class ConfigCheckTest {

    @Test
    fun `configuração completa não devolve faltas`() {
        val faltam = ConfigCheck.missingValues(
            endpoint = "https://fra.cloud.appwrite.io/v1",
            projectId = "6abe7ca20035c289a748",
        )
        assertTrue("Não devia faltar nada", faltam.isEmpty())
    }

    @Test
    fun `project id em branco é detetado`() {
        val faltam = ConfigCheck.missingValues(
            endpoint = "https://fra.cloud.appwrite.io/v1",
            projectId = "",
        )
        assertEquals(listOf("APPWRITE_PROJECT_ID"), faltam)
    }

    @Test
    fun `endpoint em branco é detetado`() {
        val faltam = ConfigCheck.missingValues(
            endpoint = "",
            projectId = "6abe7ca20035c289a748",
        )
        assertEquals(listOf("APPWRITE_ENDPOINT"), faltam)
    }

    @Test
    fun `endpoint não https é recusado`() {
        val faltam = ConfigCheck.missingValues(
            endpoint = "http://fra.cloud.appwrite.io/v1",
            projectId = "6abe7ca20035c289a748",
        )
        assertEquals(listOf("APPWRITE_ENDPOINT"), faltam)
    }

    @Test
    fun `tudo em falta devolve os dois valores`() {
        val faltam = ConfigCheck.missingValues(endpoint = " ", projectId = " ")
        assertEquals(listOf("APPWRITE_ENDPOINT", "APPWRITE_PROJECT_ID"), faltam)
    }
}
