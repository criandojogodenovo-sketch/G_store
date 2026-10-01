package com.gstore.api

import com.gstore.api.routes.adminRoutes
import com.gstore.api.routes.authRoutes
import com.gstore.api.routes.categoryRoutes
import com.gstore.api.routes.downloadRoutes
import com.gstore.api.routes.gameRoutes
import com.gstore.api.routes.healthRoutes
import com.gstore.api.routes.moduleKey
import com.gstore.api.routes.versionRoutes
import com.gstore.api.services.GitHubService
import com.gstore.api.repositories.NotFoundException
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.calllogging.CallLogging
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.cors.routing.CORS
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.request.path
import io.ktor.server.request.uri
import io.ktor.server.response.respondText
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import org.slf4j.event.Level

/**
 * G Store API — ponto de entrada.
 *
 * Arquitetura:
 *   Android  →  API Ktor (esta aplicação)  →  Neon PostgreSQL
 *                              ├→  Appwrite (validação de identidade)
 *                              └→  GitHub Releases (APKs)
 */
fun main() {
    val config = com.gstore.api.config.AppConfig.fromEnv()
    val module = AppModule(config)

    val server = embeddedServer(Netty, port = config.port) {
        configureApp(module)
    }
    Runtime.getRuntime().addShutdownHook(Thread { module.close() })
    server.start(wait = true)
}

fun Application.configureApp(module: AppModule) {
    val logger = LoggerFactory.getLogger("g-store-api")

    // Módulo disponível para as rotas.
    attributes.put(moduleKey, module)

    install(CallLogging) {
        level = Level.INFO
        filter { call -> call.request.path().startsWith("/api") }
    }

    install(ContentNegotiation) {
        json(Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
            explicitNulls = true
        })
    }

    install(CORS) {
        anyHost()
        allowMethod(HttpMethod.Get)
        allowMethod(HttpMethod.Post)
        allowMethod(HttpMethod.Put)
        allowMethod(HttpMethod.Delete)
        allowMethod(HttpMethod.Options)
        allowHeader(HttpHeaders.Authorization)
        allowHeader(HttpHeaders.ContentType)
        allowNonSimpleContentTypes = true
    }

    install(StatusPages) {
        exception<Throwable> { call, cause ->
            when (cause) {
                is NotFoundException -> call.respondText(
                    """{"ok":false,"error":"${cause.message}"}""",
                    ContentType.Application.Json,
                    HttpStatusCode.NotFound,
                )
                is IllegalArgumentException -> call.respondText(
                    """{"ok":false,"error":"${cause.message ?: "invalid_argument"}"}""",
                    ContentType.Application.Json,
                    HttpStatusCode.BadRequest,
                )
                is GitHubService.GitHubException -> {
                    logger.error("GitHub error em {}: {}", call.request.uri, cause.message)
                    call.respondText(
                        """{"ok":false,"error":"github_error"}""",
                        ContentType.Application.Json,
                        HttpStatusCode.BadGateway,
                    )
                }
                else -> {
                    // Log sem segredos: apenas tipo da exceção, mensagem e URI.
                    logger.error("Erro interno em {}: {}: {}", call.request.uri, cause.javaClass.simpleName, cause.message)
                    call.respondText(
                        """{"ok":false,"error":"internal_error"}""",
                        ContentType.Application.Json,
                        HttpStatusCode.InternalServerError,
                    )
                }
            }
        }
    }

    routing {
        healthRoutes()
        gameRoutes()
        versionRoutes()
        downloadRoutes()
        categoryRoutes()
        authRoutes()
        adminRoutes()

        // Catch-all: qualquer rota /api não correspondida responde em JSON.
        route("/api/{...}") {
            handle {
                call.respondText(
                    """{"ok":false,"error":"not_found"}""",
                    ContentType.Application.Json,
                    HttpStatusCode.NotFound,
                )
            }
        }
    }
}
