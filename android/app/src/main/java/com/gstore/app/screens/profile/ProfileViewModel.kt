package com.gstore.app.screens.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.gstore.app.data.local.SessionStore
import com.gstore.app.data.remote.UserProfileDto
import com.gstore.app.data.repo.GStoreRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class ProfileViewModel(
    private val repository: GStoreRepository,
    private val sessionStore: SessionStore,
) : ViewModel() {

    private val _profile = MutableStateFlow<UserProfileDto?>(null)
    val profile: StateFlow<UserProfileDto?> = _profile

    private val _loggedOut = MutableStateFlow(false)
    val loggedOut: StateFlow<Boolean> = _loggedOut

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            // Usuário autenticado via Appwrite -> sincroniza perfil local.
            _profile.value = repository.currentProfile()
        }
    }

    fun becomeDeveloper() {
        viewModelScope.launch {
            _busy.value = true
            repository.becomeDeveloper().onSuccess { _profile.value = it }
            _busy.value = false
        }
    }

    fun rename(displayName: String) {
        viewModelScope.launch {
            repository.updateProfile(displayName).onSuccess { _profile.value = it }
        }
    }

    fun logout() {
        viewModelScope.launch {
            repository.logout()
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
