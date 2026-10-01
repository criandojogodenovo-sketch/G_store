package com.gstore.app.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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

    init {
        load()
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
        val byDownloads = games.sortedByDescending { it.downloads }
        return HomeUiState.Ready(
            featured = byDownloads.take(5),
            recent = games.take(10),
            categories = categories,
            grid = games,
            fromCache = fromCache,
        )
    }
}
