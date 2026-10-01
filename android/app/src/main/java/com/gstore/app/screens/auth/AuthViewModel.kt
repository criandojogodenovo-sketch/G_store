package com.gstore.app.screens.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.gstore.app.data.remote.Role
import com.gstore.app.data.remote.UserProfileDto
import com.gstore.app.data.repo.GStoreRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class AuthViewModel(private val repository: GStoreRepository) : ViewModel() {

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    private val _success = MutableStateFlow<UserProfileDto?>(null)
    val success: StateFlow<UserProfileDto?> = _success

    fun login(email: String, password: String) {
        if (email.isBlank() || password.isBlank()) {
            _error.value = "Preencha e-mail e senha."
            return
        }
        _busy.value = true
        _error.value = null
        viewModelScope.launch {
            repository.login(email.trim(), password)
                .onSuccess { _success.value = it }
                .onFailure { _error.value = readable(it) }
            _busy.value = false
        }
    }

    fun register(name: String, email: String, password: String) {
        if (name.isBlank() || email.isBlank() || password.length < 8) {
            _error.value = "Preencha os campos. A senha precisa de ao menos 8 caracteres."
            return
        }
        _busy.value = true
        _error.value = null
        viewModelScope.launch {
            repository.register(name.trim(), email.trim(), password)
                .onSuccess { _success.value = it }
                .onFailure { _error.value = readable(it) }
            _busy.value = false
        }
    }

    fun becomeDeveloper() {
        viewModelScope.launch {
            repository.becomeDeveloper()
                .onSuccess { _success.value = it }
        }
    }

    private fun readable(t: Throwable): String {
        val msg = t.message ?: return "Falha na autenticação"
        return when {
            msg.contains("configurado") || msg.contains("APPWRITE") -> msg
            msg.contains("invalid_credentials", true) || msg.contains("password", true) -> "E-mail ou senha incorretos."
            msg.contains("user_already_exists", true) -> "Já existe uma conta com este e-mail."
            else -> "Falha na autenticação: verifique a configuração do Appwrite."
        }
    }

    companion object {
        fun factory(repository: GStoreRepository) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T =
                AuthViewModel(repository) as T
        }
    }
}
