package com.gstore.app.screens.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.gstore.app.data.local.SessionStore
import com.gstore.app.data.repo.GStoreRepository
import com.gstore.app.data.repo.ProfileDto
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class ProfileViewModel(
    private val repository: GStoreRepository,
    private val sessionStore: SessionStore,
) : ViewModel() {

    private val _profile = MutableStateFlow<ProfileDto?>(null)
    val profile: StateFlow<ProfileDto?> = _profile

    private val _loggedOut = MutableStateFlow(false)
    val loggedOut: StateFlow<Boolean> = _loggedOut

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy

    private val _renameError = MutableStateFlow<String?>(null)
    val renameError: StateFlow<String?> = _renameError

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            // Utilizador autenticado no Neon Auth -> perfil local (tabela profiles).
            _profile.value = repository.currentProfile()
        }
    }

    /** Renomear: atualiza o nome no Neon Auth e no perfil local. */
    fun rename(displayName: String) {
        if (displayName.isBlank()) return
        viewModelScope.launch {
            _busy.value = true
            _renameError.value = null
            repository.updateDisplayName(displayName.trim())
                .onSuccess {
                    _profile.value = it
                    sessionStore.save(displayName = it.displayName, avatarUrl = it.avatarUrl)
                }
                .onFailure { _renameError.value = it.message ?: "Falha ao renomear" }
            _busy.value = false
        }
    }

    fun logout() {
        viewModelScope.launch {
            repository.logout()
            _profile.value = null
            _loggedOut.value = true
        }
    }

    companion object {
        fun factory(repository: GStoreRepository, sessionStore: SessionStore) =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T =
                    ProfileViewModel(repository, sessionStore) as T
            }
    }
}
