package com.gstore.app.screens.categories

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gstore.app.data.remote.CategoryDto
import com.gstore.app.data.repo.GStoreRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

sealed interface CategoriesUiState {
    data object Loading : CategoriesUiState
    data class Ready(val categories: List<CategoryDto>) : CategoriesUiState
    data class Error(val message: String) : CategoriesUiState
}

class CategoriesViewModel(private val repository: GStoreRepository) : ViewModel() {

    private val _state = MutableStateFlow<CategoriesUiState>(CategoriesUiState.Loading)
    val state: StateFlow<CategoriesUiState> = _state

    init {
        load()
    }

    fun load() {
        _state.value = CategoriesUiState.Loading
        viewModelScope.launch {
            try {
                _state.value = CategoriesUiState.Ready(repository.listCategories())
            } catch (e: Exception) {
                _state.value = CategoriesUiState.Error(e.message ?: "Falha ao carregar categorias")
            }
        }
    }
}
