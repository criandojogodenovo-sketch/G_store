package com.gstore.app.screens.gamedetails

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.gstore.app.data.remote.GameDto
import com.gstore.app.data.remote.GameVersionDto
import com.gstore.app.data.repo.DownloadRepository
import com.gstore.app.data.repo.GStoreRepository
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

    private var downloadId: Long = -1

    fun load() {
        _loading.value = true
        _error.value = null
        viewModelScope.launch {
            try {
                val game = repository.getGame(slug)
                _game.value = game
                _versions.value = repository.listVersions(game.id)
            } catch (e: Exception) {
                _error.value = e.message ?: "Falha ao carregar o jogo"
            } finally {
                _loading.value = false
            }
        }
    }

    fun startDownload() {
        val game = _game.value ?: return
        _downloadState.value = DownloadState.Preparing
        viewModelScope.launch {
            try {
                downloadId = downloads.startDownload(game.slug, game.name, game.version)
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

    companion object {
        fun factory(repository: GStoreRepository, downloads: DownloadRepository, slug: String) =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T =
                    GameDetailsViewModel(repository, downloads, slug) as T
            }
    }
}
