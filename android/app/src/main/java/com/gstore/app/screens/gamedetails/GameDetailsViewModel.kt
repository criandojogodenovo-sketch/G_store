package com.gstore.app.screens.gamedetails

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.gstore.app.data.repo.DownloadRepository
import com.gstore.app.data.repo.GameDto
import com.gstore.app.data.repo.GameVersionDto
import com.gstore.app.data.repo.GStoreRepository
import com.gstore.app.data.repo.ReviewDto
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

sealed interface DownloadState {
    data object Idle : DownloadState
    data object Preparing : DownloadState
    data class Downloading(val progress: Int) : DownloadState
    data object Success : DownloadState
    data class Failed(val message: String) : DownloadState
}

data class MyReview(
    val rating: Int = 0,
    val comment: String = "",
)

class GameDetailsViewModel(
    private val repository: GStoreRepository,
    private val downloads: DownloadRepository,
    private val slug: String,
) : ViewModel() {

    private val _game = MutableStateFlow<GameDto?>(null)
    val game: StateFlow<GameDto?> = _game

    private val _versions = MutableStateFlow<List<GameVersionDto>>(emptyList())
    val versions: StateFlow<List<GameVersionDto>> = _versions

    private val _loading = MutableStateFlow(true)
    val loading: StateFlow<Boolean> = _loading

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    private val _downloadState = MutableStateFlow<DownloadState>(DownloadState.Idle)
    val downloadState: StateFlow<DownloadState> = _downloadState

    private val _favorite = MutableStateFlow(false)
    val favorite: StateFlow<Boolean> = _favorite

    private val _reviews = MutableStateFlow<List<ReviewDto>>(emptyList())
    val reviews: StateFlow<List<ReviewDto>> = _reviews

    private val _myReview = MutableStateFlow<MyReview?>(null)
    val myReview: StateFlow<MyReview?> = _myReview

    private val _loggedIn = MutableStateFlow(false)
    val loggedIn: StateFlow<Boolean> = _loggedIn

    /** Nota média das reviews (null se ainda não existirem). */
    private val _averageRating = MutableStateFlow<Double?>(null)
    val averageRating: StateFlow<Double?> = _averageRating

    private var downloadId: Long = -1

    fun load() {
        _loading.value = true
        _error.value = null
        viewModelScope.launch {
            try {
                val game = repository.getGame(slug)
                _game.value = game
                _versions.value = repository.listVersions(game.id)
                _loggedIn.value = repository.currentUser() != null
                loadUserSpecific(game.id)
            } catch (e: Exception) {
                _error.value = e.message ?: "Falha ao carregar o jogo"
            } finally {
                _loading.value = false
            }
        }
    }

    private suspend fun loadUserSpecific(gameId: String) {
        _reviews.value = repository.listReviews(gameId)
        _averageRating.value = _reviews.value.takeIf { it.isNotEmpty() }
            ?.let { list -> list.map { it.rating }.average() }
        runCatching {
            _favorite.value = repository.isFavorite(gameId)
            val uid = repository.currentUser()?.id
            _myReview.value = _reviews.value.firstOrNull { it.userId == uid }
                ?.let { MyReview(it.rating, it.comment ?: "") }
        }
    }

    /** Última versão disponível (ou null se o jogo ainda não tem versões). */
    fun latestVersion(): GameVersionDto? =
        _versions.value.maxByOrNull { it.createdAt ?: it.version }

    fun startDownload() {
        val game = _game.value ?: return
        val version = latestVersion() ?: run {
            _downloadState.value = DownloadState.Failed("Este jogo ainda não tem APK publicado.")
            return
        }
        _downloadState.value = DownloadState.Preparing
        viewModelScope.launch {
            try {
                downloadId = downloads.startDownload(game.slug, game.name, version)
                // Regista a descarga (só faz algo com sessão iniciada).
                repository.registerDownload(game, version)
                pollProgress()
            } catch (e: Exception) {
                _downloadState.value = DownloadState.Failed(e.message ?: "Falha ao iniciar o download")
            }
        }
    }

    private suspend fun pollProgress() {
        while (true) {
            val status = downloads.queryStatus(downloadId)
            when (status.status) {
                DownloadRepository.STATUS_SUCCESSFUL -> {
                    _downloadState.value = DownloadState.Success
                    return
                }
                DownloadRepository.STATUS_FAILED -> {
                    _downloadState.value = DownloadState.Failed("O download foi interrompido")
                    return
                }
                else -> {
                    _downloadState.value = DownloadState.Downloading(status.progress)
                }
            }
            delay(700)
        }
    }

    fun toggleFavorite() {
        val game = _game.value ?: return
        viewModelScope.launch {
            if (_favorite.value) {
                repository.removeFavorite(game.id)
                    .onSuccess { _favorite.value = false }
            } else {
                repository.addFavorite(game.id)
                    .onSuccess { _favorite.value = true }
            }
        }
    }

    /** Cria/atualiza a review do próprio utilizador. */
    fun submitReview(rating: Int, comment: String, onDone: (Boolean) -> Unit) {
        val game = _game.value ?: return
        viewModelScope.launch {
            repository.upsertReview(game.id, rating, comment.ifBlank { null })
                .onSuccess {
                    _myReview.value = MyReview(it.rating, it.comment ?: "")
                    loadUserSpecific(game.id)
                    onDone(true)
                }
                .onFailure { onDone(false) }
        }
    }

    companion object {
        fun factory(repository: GStoreRepository, downloads: DownloadRepository, slug: String) =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T =
                    GameDetailsViewModel(repository, downloads, slug) as T
            }
    }
}
