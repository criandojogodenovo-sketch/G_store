package com.gstore.app.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gstore.app.data.remote.CategoryDto
import com.gstore.app.data.remote.GameDto
import com.gstore.app.data.repo.GStoreRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

sealed interface HomeUiState {
    data object Loading : HomeUiState
    data class Ready(
        val featured: List<GameDto>,
        val recent: List<GameDto>,
        val categories: List<CategoryDto>,
        val grid: List<GameDto>,
    ) : HomeUiState
    data class Error(val message: String) : HomeUiState
}

class HomeViewModel(private val repository: GStoreRepository) : ViewModel() {

    private val _state = MutableStateFlow<HomeUiState>(HomeUiState.Loading)
    val state: StateFlow<HomeUiState> = _state

    init {
        load()
    }

    fun load() {
        _state.value = HomeUiState.Loading
        viewModelScope.launch {
            try {
                val newest = repository.listGames(sort = "newest", limit = 30)
                val byDownloads = repository.listGames(sort = "downloads", limit = 8)
                val categories = repository.listCategories()
                _state.value = HomeUiState.Ready(
                    featured = byDownloads.take(5),
                    recent = newest.take(10),
                    categories = categories,
                    grid = newest,
                )
            } catch (e: Exception) {
                _state.value = HomeUiState.Error(e.message ?: "Falha ao carregar a loja")
            }
        }
    }
}
