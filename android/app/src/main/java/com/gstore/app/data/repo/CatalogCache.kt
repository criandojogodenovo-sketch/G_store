package com.gstore.app.data.repo

import android.content.Context
import com.gstore.app.data.remote.ApiClient
import kotlinx.serialization.builtins.ListSerializer
import java.io.File

/**
 * Cache local do último catálogo lido com sucesso — permite usar a loja
 * offline (dados móveis instáveis, avião, etc.). O cache é apenas de
 * LEITURA pública (jogos publicados + categorias); nada sensível é
 * guardado aqui.
 */
class CatalogCache(private val context: Context) {

    private val json get() = ApiClient.json

    private fun gamesFile(): File = File(context.filesDir, "catalog_games.json")
    private fun categoriesFile(): File = File(context.filesDir, "catalog_categories.json")

    fun save(games: List<GameDto>, categories: List<CategoryDto>) {
        runCatching {
            gamesFile().writeText(json.encodeToString(ListSerializer(GameDto.serializer()), games))
            categoriesFile().writeText(json.encodeToString(ListSerializer(CategoryDto.serializer()), categories))
        }
    }

    fun loadGames(): List<GameDto>? = runCatching {
        val f = gamesFile()
        if (!f.exists()) return null
        json.decodeFromString(ListSerializer(GameDto.serializer()), f.readText())
    }.getOrNull()

    fun loadCategories(): List<CategoryDto>? = runCatching {
        val f = categoriesFile()
        if (!f.exists()) return null
        json.decodeFromString(ListSerializer(CategoryDto.serializer()), f.readText())
    }.getOrNull()

    fun load(): Pair<List<GameDto>, List<CategoryDto>>? {
        val games = loadGames() ?: return null
        val categories = loadCategories() ?: return null
        return games to categories
    }

    fun timestamp(): Long = gamesFile().let { if (it.exists()) it.lastModified() else 0L }
}
