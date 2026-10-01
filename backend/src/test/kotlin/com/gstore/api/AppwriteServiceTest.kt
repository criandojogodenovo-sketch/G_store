package com.gstore.api

import com.gstore.api.services.AppwriteService
import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Testes do AppwriteService contra um servidor Appwrite falso (JDK HttpServer).
 * Valida a lógica de validação de JWT sem depender de serviço externo.
 */
class AppwriteServiceTest {

    private lateinit var server: HttpServer
    private var port: Int = 0
    private var lastJwtSeen: String? = null

    @BeforeTest
    fun setUp() {
        server = HttpServer.create(InetSocketAddress(0), 0)
        server.createContext("/v1/account") { exchange ->
            lastJwtSeen = exchange.requestHeaders.getFirst("X-Appwrite-JWT")
            val status = when (lastJwtSeen) {
                "jwt-valido" -> 200
                else -> 401
            }
            val body = if (status == 200) {
                """{"${'$'}id":"user-appwrite-123","email":"dev@gstore.app","name":"Dev Teste"}"""
            } else {
                """{"message":"Invalid JWT"}"""
            }
            val bytes = body.toByteArray()
            exchange.sendResponseHeaders(status, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
        server.start()
        port = server.address.port
    }

    @AfterTest
    fun tearDown() {
        server.stop(0)
    }

    @Test
    fun `JWT válido retorna o usuário do Appwrite`() {
        val service = AppwriteService(
            endpoint = "http://localhost:$port/v1",
            projectId = "projeto-teste",
            apiKey = null,
        )
        val user = service.validateJwt("jwt-valido")
        assertEquals("user-appwrite-123", user?.id)
        assertEquals("dev@gstore.app", user?.email)
        assertEquals("Dev Teste", user?.name)
        assertEquals("jwt-valido", lastJwtSeen)
    }

    @Test
    fun `JWT inválido retorna null`() {
        val service = AppwriteService(
            endpoint = "http://localhost:$port/v1",
            projectId = "projeto-teste",
            apiKey = null,
        )
        assertNull(service.validateJwt("jwt-lixo"))
    }

    @Test
    fun `serviço não configurado retorna null sem chamar HTTP`() {
        val service = AppwriteService(endpoint = null, projectId = null, apiKey = null)
        assertNull(service.validateJwt("qualquer"))
    }
}
