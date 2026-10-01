package com.gstore.app.screens.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.gstore.app.data.remote.ApiException
import com.gstore.app.data.repo.GStoreRepository
import com.gstore.app.data.repo.ProfileDto
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import retrofit2.HttpException
import java.io.IOException

class AuthViewModel(private val repository: GStoreRepository) : ViewModel() {

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    private val _success = MutableStateFlow<ProfileDto?>(null)
    val success: StateFlow<ProfileDto?> = _success

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

    /**
     * Mostra o ERRO REAL do servidor (código + mensagem) para o utilizador
     * poder diagnosticar — sem mensagens genéricas.
     */
    private fun readable(t: Throwable): String {
        val msg = t.message ?: return "Falha na autenticação"
        return when (t) {
            is ApiException -> {
                when (t.errorCode) {
                    "INVALID_EMAIL_OR_PASSWORD" -> "E-mail ou senha incorretos. (INVALID_EMAIL_OR_PASSWORD)"
                    "USER_ALREADY_EXISTS", "USER_ALREADY_EXISTS_USE_ANOTHER_EMAIL" ->
                        "Já existe uma conta com este e-mail. (USER_ALREADY_EXISTS)"
                    "INVALID_ORIGIN" -> "Origem do app não registada no Neon Auth. (INVALID_ORIGIN)"
                    else -> "$msg"
                }
            }
            is HttpException -> "HTTP ${t.code()}: $msg"
            is IOException -> "Sem ligação ao Neon ($msg). Verifique a internet e tente novamente."
            else -> msg
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
