package com.gstore.app.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gstore.app.data.repo.CatalogTypeFilter
import com.gstore.app.data.repo.CategoryDto
import com.gstore.app.data.repo.GameDto
import com.gstore.app.data.repo.GStoreRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.io.IOException

sealed interface HomeUiState {
    data object Loading : HomeUiState
    data class Ready(
        val featured: List<GameDto>,
        val recent: List<GameDto>,
        val categories: List<CategoryDto>,
        val grid: List<GameDto>,
        /** true = vindo do cache offline (rede falhou). */
        val fromCache: Boolean = false,
    ) : HomeUiState
    data class Error(val message: String, val temCache: Boolean = false) : HomeUiState
}

class HomeViewModel(private val repository: GStoreRepository) : ViewModel() {

    private val _state = MutableStateFlow<HomeUiState>(HomeUiState.Loading)
    val state: StateFlow<HomeUiState> = _state

    private val _refreshing = MutableStateFlow(false)
    val refreshing: StateFlow<Boolean> = _refreshing

    /** Filtro Tudo / Apps / Jogos (MUDANÇA: a loja é de apps E jogos). */
    private val _typeFilter = MutableStateFlow(CatalogTypeFilter.ALL)
    val typeFilter: StateFlow<CatalogTypeFilter> = _typeFilter

    /** Catálogo completo em memória (sem filtro) para recalcular ao trocar. */
    private var allGames: List<GameDto> = emptyList()
    private var allCategories: List<CategoryDto> = emptyList()
    private var fromCacheFlag: Boolean = false

    init {
        load()
    }

    /** Troca o filtro Tudo/Apps/Jogos sem refazer o pedido à rede. */
    fun setTypeFilter(f: CatalogTypeFilter) {
        if (_typeFilter.value == f) return
        _typeFilter.value = f
        _state.value = ready(allGames, allCategories, fromCacheFlag)
    }

    fun load() {
        _state.value = HomeUiState.Loading
        viewModelScope.launch {
            try {
                val (games, categories) = repository.fetchCatalog()
                _state.value = ready(games, categories, fromCache = false)
            } catch (e: Exception) {
                // Rede falhou → tenta o cache do último catálogo (offline).
                val cache = repository.cachedCatalog()
                if (cache != null) {
                    _state.value = ready(cache.first, cache.second, fromCache = true)
                } else {
                    _state.value = HomeUiState.Error(
                        message = (e.message ?: "Falha ao carregar a loja") +
                            if (e is IOException) " (sem ligação)" else "",
                        temCache = false,
                    )
                }
            }
        }
    }

    /** Puxar para atualizar: recarrega sem esconder o conteúdo atual. */
    fun refresh() {
        if (_refreshing.value) return
        _refreshing.value = true
        viewModelScope.launch {
            try {
                val (games, categories) = repository.fetchCatalog()
                _state.value = ready(games, categories, fromCache = false)
            } catch (_: Exception) {
                // Mantém o estado atual (cache ou conteúdo já carregado).
            } finally {
                _refreshing.value = false
            }
        }
    }

    private fun ready(games: List<GameDto>, categories: List<CategoryDto>, fromCache: Boolean): HomeUiState.Ready {
        allGames = games
        allCategories = categories
        fromCacheFlag = fromCache
        val filtro = _typeFilter.value
        val visiveis = games.filter { filtro.matches(it.type) }
        val catsVisiveis = categories.filter { filtro.matches(it.type) }
        val byDownloads = visiveis.sortedByDescending { it.downloads }
        return HomeUiState.Ready(
            featured = byDownloads.take(5),
            recent = visiveis.take(10),
            categories = catsVisiveis,
            grid = visiveis,
            fromCache = fromCache,
        )
    }
}
