package com.gstore.api.routes

import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get

/**
 * GET /api/health — health check (compatível com o formato legado).
 * GET /api/health?deep=1 — valida também a conexão com o Neon.
 */
fun Route.healthRoutes() {
    get("/api/health") {
        val deep = call.request.queryParameters["deep"] == "1"
        val dbOk = if (deep) call.module.database.healthCheck() else null
        if (deep == true && dbOk == false) {
            call.respondText(
                """{"ok":false,"error":"database_unavailable"}""",
                io.ktor.http.ContentType.Application.Json,
                HttpStatusCode.ServiceUnavailable,
            )
            return@get
        }
        val payload = buildString {
            append("""{"ok":true,"service":"g-store-api"""")
            if (deep) append(""","database":"${if (dbOk == true) "ok" else "unknown"}"""")
            append("}")
        }
        call.respondText(payload, io.ktor.http.ContentType.Application.Json)
    }
}
