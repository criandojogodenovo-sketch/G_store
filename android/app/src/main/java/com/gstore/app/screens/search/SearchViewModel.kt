package com.gstore.app.screens.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gstore.app.data.remote.GameDto
import com.gstore.app.data.repo.GStoreRepository
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch

sealed interface SearchUiState {
    data object Idle : SearchUiState
    data object Loading : SearchUiState
    data class Results(val games: List<GameDto>) : SearchUiState
    data object Empty : SearchUiState
    data class Error(val message: String) : SearchUiState
}

@OptIn(FlowPreview::class)
class SearchViewModel(private val repository: GStoreRepository) : ViewModel() {

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query

    private val _state = MutableStateFlow<SearchUiState>(SearchUiState.Idle)
    val state: StateFlow<SearchUiState> = _state

    init {
        viewModelScope.launch {
            _query.debounce(300).collect { q ->
                if (q.isBlank()) {
                    _state.value = SearchUiState.Idle
                } else {
                    search(q)
                }
            }
        }
    }

    fun onQueryChange(q: String) {
        _query.value = q
    }

    /** Carrega jogos de uma categoria (navegação a partir de Categorias). */
    fun searchCategory(categorySlug: String) {
        viewModelScope.launch {
            _state.value = SearchUiState.Loading
            try {
                val results = repository.listGames(category = categorySlug)
                _state.value = if (results.isEmpty()) SearchUiState.Empty else SearchUiState.Results(results)
            } catch (e: Exception) {
                _state.value = SearchUiState.Error(e.message ?: "Falha ao carregar a categoria")
            }
        }
    }

    private suspend fun search(q: String) {
        _state.value = SearchUiState.Loading
        try {
            val results = repository.listGames(search = q)
            _state.value = if (results.isEmpty()) SearchUiState.Empty else SearchUiState.Results(results)
        } catch (e: Exception) {
            _state.value = SearchUiState.Error(e.message ?: "Falha na busca")
        }
    }
}
