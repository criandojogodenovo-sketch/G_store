package com.gstore.api.routes

import com.gstore.api.repositories.GameRepository
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.route

/**
 * Categorias:
 *
 *   GET /api/categories          — lista (pública)
 *   GET /api/categories/{slug}   — detalhe + jogos publicados (pública)
 */
fun Route.categoryRoutes() {
    route("/api/categories") {
        get {
            val categories = call.module.categoryRepository.list(includeCounts = true)
            call.respondData(CategoryListEnvelope(count = categories.size, categories = categories))
        }

        get("/{slug}") {
            val slug = call.parameters["slug"] ?: return@get call.respondError(HttpStatusCode.BadRequest, "invalid_slug")
            val category = call.module.categoryRepository.findBySlugOrNull(slug)
                ?: return@get call.respondError(HttpStatusCode.NotFound, "category_not_found")
            val games = call.module.gameRepository.listGames(
                GameRepository.GameFilters(
                    publishedOnly = true,
                    category = slug,
                    limit = 100,
                ),
            )
            call.respondData(CategoryDetailEnvelope(category = category, count = games.size, games = games))
        }
    }
}

@kotlinx.serialization.Serializable
data class CategoryListEnvelope(val ok: Boolean = true, val count: Int, val categories: List<com.gstore.api.models.Category>)

@kotlinx.serialization.Serializable
data class CategoryDetailEnvelope(
    val ok: Boolean = true,
    val category: com.gstore.api.models.Category,
    val count: Int,
    val games: List<com.gstore.api.models.Game>,
)
