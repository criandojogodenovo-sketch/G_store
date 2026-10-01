package com.gstore.app.screens.developer

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.gstore.app.data.remote.GameDto
import com.gstore.app.data.remote.GameVersionDto
import com.gstore.app.data.repo.DownloadStatsLite
import com.gstore.app.data.repo.GStoreRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.io.File

sealed interface PublishState {
    data object Idle : PublishState
    data class Uploading(val progress: Int) : PublishState
    data object Processing : PublishState
    data class Done(val gameId: String) : PublishState
    data class Error(val message: String) : PublishState
}

class DeveloperViewModel(private val repository: GStoreRepository) : ViewModel() {

    private val _myGames = MutableStateFlow<List<GameDto>>(emptyList())
    val myGames: StateFlow<List<GameDto>> = _myGames

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading

    private val _publishState = MutableStateFlow<PublishState>(PublishState.Idle)
    val publishState: StateFlow<PublishState> = _publishState

    private val _versions = MutableStateFlow<List<GameVersionDto>>(emptyList())
    val versions: StateFlow<List<GameVersionDto>> = _versions

    private val _stats = MutableStateFlow<DownloadStatsLite?>(null)
    val stats: StateFlow<DownloadStatsLite?> = _stats

    fun loadMyGames() {
        _loading.value = true
        viewModelScope.launch {
            try {
                // Endpoint "all=1" requer developer; erros simplesmente esvaziam a lista.
                _myGames.value = repository.listGames(limit = 100)
            } catch (e: Exception) {
                _myGames.value = emptyList()
            } finally {
                _loading.value = false
            }
        }
    }

    fun publish(
        context: Context,
        name: String,
        description: String,
        category: String,
        version: String,
        versionCode: Int?,
        releaseNotes: String,
        apkUri: Uri?,
    ) {
        if (apkUri == null) {
            _publishState.value = PublishState.Error("Selecione o arquivo APK.")
            return
        }
        viewModelScope.launch {
            try {
                val apk = copyToTemp(context, apkUri, "upload.apk")
                    ?: run {
                        _publishState.value = PublishState.Error("Não foi possível ler o APK selecionado.")
                        return@launch
                    }
                _publishState.value = PublishState.Uploading(0)

                val gameResult = repository.createGame(name, description, category)
                val game = gameResult.getOrElse { e ->
                    _publishState.value = PublishState.Error(readable(e))
                    apk.delete()
                    return@launch
                }

                // Envia APK -> API -> GitHub Releases -> Neon
                repository.publishVersion(
                    gameId = game.id,
                    version = version,
                    versionCode = versionCode,
                    releaseNotes = releaseNotes,
                    apk = apk,
                    icon = null,
                    screenshots = emptyList(),
                    onProgress = { sent, total ->
                        val pct = if (total > 0) ((sent * 100) / total).toInt() else 0
                        _publishState.value = PublishState.Uploading(pct.coerceIn(0, 99))
                    },
                ).onSuccess { (publishedGame, _) ->
                    _publishState.value = PublishState.Done(publishedGame.id)
                    loadMyGames()
                }.onFailure { e ->
                    _publishState.value = PublishState.Error(readable(e))
                }
                apk.delete()
            } catch (e: Exception) {
                _publishState.value = PublishState.Error(e.message ?: "Falha ao publicar")
            }
        }
    }

    fun loadVersions(gameId: String) {
        viewModelScope.launch {
            try {
                _versions.value = repository.listVersions(gameId)
                _stats.value = repository.downloadStats(gameId)
            } catch (e: Exception) {
                _versions.value = emptyList()
            }
        }
    }

    fun updateGame(gameId: String, name: String, description: String, category: String, onDone: () -> Unit) {
        viewModelScope.launch {
            repository.updateGame(gameId, name, description, category, null)
                .onSuccess { onDone() }
        }
    }

    private fun readable(t: Throwable): String = when {
        t.message?.contains("not_authenticated") == true -> "Sessão expirada — entre novamente."
        t.message?.contains("forbidden") == true -> "Sua conta precisa do papel DEVELOPER (disponível no Perfil)."
        t.message?.contains("Appwrite") == true || t.message?.contains("configurado") == true -> t.message ?: "Configuração ausente"
        else -> "Falha na publicação: ${t.message ?: "erro desconhecido"}"
    }

    companion object {
        fun factory(repository: GStoreRepository) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T =
                DeveloperViewModel(repository) as T
        }
    }
}

/** Estatísticas simplificadas para exibição no dashboard. */
data class DownloadStatsLite(
    val counter: Long,
    val total: Long,
    val last7Days: Long,
    val last30Days: Long,
)

private fun copyToTemp(context: Context, uri: Uri, name: String): File? = try {
    val out = File(context.cacheDir, name)
    context.contentResolver.openInputStream(uri)?.use { input ->
        out.outputStream().use { output -> input.copyTo(output, 64 * 1024) }
    } ?: return null
    out
} catch (e: Exception) {
    null
}
