package com.gstore.app.screens.developer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.gstore.app.data.repo.CategoryDto
import com.gstore.app.data.repo.DownloadStatsLite
import com.gstore.app.data.repo.GameDto
import com.gstore.app.data.repo.GameVersionDto
import com.gstore.app.data.repo.GStoreRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

sealed interface PublishState {
    data object Idle : PublishState
    data class Saving(val passo: String) : PublishState
    data object Done : PublishState
    data class Error(val message: String) : PublishState
}

/**
 * ViewModel da ÁREA DE ADMINISTRAÇÃO (só visível para role=admin;
 * o servidor nega tudo a não-admins via RLS de qualquer forma).
 */
class DeveloperViewModel(private val repository: GStoreRepository) : ViewModel() {

    private val _myGames = MutableStateFlow<List<GameDto>>(emptyList())
    val myGames: StateFlow<List<GameDto>> = _myGames

    private val _categories = MutableStateFlow<List<CategoryDto>>(emptyList())
    val categories: StateFlow<List<CategoryDto>> = _categories

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading

    private val _publishState = MutableStateFlow<PublishState>(PublishState.Idle)
    val publishState: StateFlow<PublishState> = _publishState

    private val _versions = MutableStateFlow<List<GameVersionDto>>(emptyList())
    val versions: StateFlow<List<GameVersionDto>> = _versions

    private val _stats = MutableStateFlow<DownloadStatsLite?>(null)
    val stats: StateFlow<DownloadStatsLite?> = _stats

    /** Catálogo completo (admin vê rascunhos também). */
    fun loadMyGames() {
        _loading.value = true
        viewModelScope.launch {
            try {
                val (games, categories) = repository.fetchCatalog(includeDrafts = true)
                _myGames.value = games
                _categories.value = categories
            } catch (e: Exception) {
                _myGames.value = emptyList()
                _publishState.value = PublishState.Error(e.message ?: "Falha ao carregar jogos")
            } finally {
                _loading.value = false
            }
        }
    }

    /**
     * Cria um jogo + categoria + primeira versão (APK já hospedado no
     * GitHub Releases — o admin cola o link).
     */
    fun publish(
        name: String,
        slug: String,
        description: String,
        shortDescription: String,
        categoryId: String,
        iconUrl: String,
        screenshots: List<String>,
        status: String,
        version: String,
        versionCode: Long?,
        releaseNotes: String,
        apkUrl: String,
    ) {
        viewModelScope.launch {
            _publishState.value = PublishState.Saving("A criar o jogo...")
            val gameResult = repository.createGame(
                name = name,
                slug = slug,
                description = description,
                shortDescription = shortDescription,
                iconUrl = iconUrl,
                screenshots = screenshots,
                status = status,
                developerId = null,
            )
            val game = gameResult.getOrElse { e ->
                _publishState.value = PublishState.Error(readable(e))
                return@launch
            }

            if (categoryId.isNotBlank()) {
                _publishState.value = PublishState.Saving("A associar a categoria...")
                repository.setGameCategory(game.id, categoryId).onFailure { e ->
                    _publishState.value = PublishState.Error(readable(e))
                    return@launch
                }
            }

            if (version.isNotBlank() && apkUrl.isNotBlank()) {
                _publishState.value = PublishState.Saving("A registar a versão...")
                repository.createVersion(
                    gameId = game.id,
                    version = version,
                    versionCode = versionCode,
                    releaseNotes = releaseNotes,
                    apkUrl = apkUrl,
                ).onFailure { e ->
                    _publishState.value = PublishState.Error(readable(e))
                    return@launch
                }
            }

            _publishState.value = PublishState.Done
            loadMyGames()
        }
    }

    fun loadVersions(gameId: String) {
        viewModelScope.launch {
            try {
                _versions.value = repository.listVersions(gameId)
                _stats.value = repository.downloadStats(gameId)
            } catch (e: Exception) {
                _versions.value = emptyList()
                _stats.value = null
            }
        }
    }

    fun addVersion(
        gameId: String,
        version: String,
        versionCode: Long?,
        releaseNotes: String,
        apkUrl: String,
        onDone: (String?) -> Unit,
    ) {
        viewModelScope.launch {
            repository.createVersion(gameId, version, versionCode, releaseNotes, apkUrl)
                .onSuccess { onDone(null); loadVersions(gameId) }
                .onFailure { onDone(readable(it)) }
        }
    }

    fun updateGame(
        gameId: String,
        name: String,
        description: String,
        shortDescription: String,
        iconUrl: String,
        status: String,
        categoryId: String,
        onDone: (String?) -> Unit,
    ) {
        viewModelScope.launch {
            repository.updateGame(gameId, name, description, shortDescription, iconUrl, status)
                .onSuccess { jogo ->
                    repository.setGameCategory(jogo.id, categoryId).onFailure { e ->
                        onDone(readable(e)); return@launch
                    }
                    onDone(null)
                    loadMyGames()
                }
                .onFailure { onDone(readable(it)) }
        }
    }

    private fun readable(t: Throwable): String = when {
        t.message?.contains("not_authenticated") == true -> "Sessão expirada — entre novamente."
        t.message?.contains("42501") == true || t.message?.contains("permission denied", true) == true ->
            "Apenas o administrador pode alterar o catálogo (RLS bloqueou o pedido)."
        else -> "Falha: ${t.message ?: "erro desconhecido"}"
    }

    companion object {
        fun factory(repository: GStoreRepository) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T =
                DeveloperViewModel(repository) as T
        }
    }
}

/** Gera um slug a partir do nome (para o formulário de publicação). */
fun slugify(name: String): String = name
    .lowercase()
    .trim()
    .replace(Regex("[^a-z0-9\\s-]"), "")
    .replace(Regex("\\s+"), "-")
    .replace(Regex("-+"), "-")
    .trim('-')
