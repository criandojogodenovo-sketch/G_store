package com.gstore.app.screens.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gstore.app.data.repo.CatalogTypeFilter
import com.gstore.app.data.repo.GameDto
import com.gstore.app.data.repo.GStoreRepository
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch

sealed interface SearchUiState {
    data object Idle : SearchUiState
    data object Loading : SearchUiState
    data class Results(val games: List<GameDto>, val fromCache: Boolean = false) : SearchUiState
    data object Empty : SearchUiState
    data class Error(val message: String) : SearchUiState
}

/** Ordenação disponível na busca. */
enum class SearchSort(val rotulo: String) {
    NEWEST("Mais recentes"),
    DOWNLOADS("Mais baixados"),
}

@OptIn(FlowPreview::class)
class SearchViewModel(private val repository: GStoreRepository) : ViewModel() {

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query

    private val _state = MutableStateFlow<SearchUiState>(SearchUiState.Idle)
    val state: StateFlow<SearchUiState> = _state

    private val _sort = MutableStateFlow(SearchSort.NEWEST)
    val sort: StateFlow<SearchSort> = _sort

    /** Categoria ativa (slug) ou null para busca geral. */
    private val _category = MutableStateFlow<String?>(null)
    val category: StateFlow<String?> = _category

    private val _categoryLabel = MutableStateFlow<String?>(null)
    val categoryLabel: StateFlow<String?> = _categoryLabel

    /** Filtro Tudo / Apps / Jogos na busca. */
    private val _typeFilter = MutableStateFlow(CatalogTypeFilter.ALL)
    val typeFilter: StateFlow<CatalogTypeFilter> = _typeFilter

    private var catalog: List<GameDto> = emptyList()
    private var fromCache: Boolean = false

    init {
        viewModelScope.launch {
            loadCatalog()
            _query.debounce(300).collect { q ->
                if (q.isBlank() && _category.value == null) {
                    _state.value = SearchUiState.Idle
                } else {
                    applyFilters()
                }
            }
        }
        viewModelScope.launch {
            _sort.collect { applyFilters() }
        }
        viewModelScope.launch {
            _typeFilter.collect { applyFilters() }
        }
    }

    private suspend fun loadCatalog() {
        _state.value = if (_category.value != null) SearchUiState.Loading else _state.value
        try {
            val (games, _) = repository.fetchCatalog()
            catalog = games
            fromCache = false
            applyFilters()
        } catch (e: Exception) {
            val cache = repository.cachedCatalog()
            if (cache != null) {
                catalog = cache.first
                fromCache = true
                applyFilters()
            } else if (_category.value != null) {
                _state.value = SearchUiState.Error(e.message ?: "Falha ao carregar jogos")
            }
        }
    }

    fun onQueryChange(q: String) {
        _query.value = q
        if (q.isBlank() && _category.value == null) {
            _state.value = SearchUiState.Idle
        }
    }

    fun onSortChange(s: SearchSort) {
        _sort.value = s
    }

    /** Filtro Tudo / Apps / Jogos (recalcula os resultados na hora). */
    fun setTypeFilter(f: CatalogTypeFilter) {
        _typeFilter.value = f
        applyFilters()
    }

    /** Carrega jogos de uma categoria (navegação a partir de Categorias). */
    fun searchCategory(categorySlug: String, label: String? = null) {
        _category.value = categorySlug
        _categoryLabel.value = label
        if (catalog.isEmpty()) {
            viewModelScope.launch { loadCatalog() }
        } else {
            applyFilters()
        }
    }

    fun clearCategory() {
        _category.value = null
        _categoryLabel.value = null
        applyFilters()
    }

    private fun applyFilters() {
        val cat = _category.value
        val q = _query.value.trim().lowercase()
        val filtro = _typeFilter.value
        if (q.isBlank() && cat == null && filtro == CatalogTypeFilter.ALL) {
            _state.value = SearchUiState.Idle
            return
        }
        _state.value = SearchUiState.Loading
        var results = catalog.asSequence()
        if (!filtro.matches(null)) {
            results = results.filter { filtro.matches(it.type) }
        }
        if (cat != null) {
            results = results.filter { game -> game.categories.contains(cat) }
        }
        if (q.isNotBlank()) {
            results = results.filter { game ->
                game.name.lowercase().contains(q) ||
                    game.developer?.lowercase()?.contains(q) == true ||
                    game.category?.lowercase()?.contains(q) == true
            }
        }
        val sorted = when (_sort.value) {
            SearchSort.NEWEST -> results.sortedByDescending { it.createdAt ?: "" }.toList()
            SearchSort.DOWNLOADS -> results.sortedByDescending { it.downloads }.toList()
        }
        _state.value = if (sorted.isEmpty()) {
            SearchUiState.Empty
        } else {
            SearchUiState.Results(sorted, fromCache)
        }
    }
}
