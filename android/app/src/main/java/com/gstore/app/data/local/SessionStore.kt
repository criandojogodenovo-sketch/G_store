package com.gstore.app.data.local

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.sessionDataStore by preferencesDataStore(name = "session_prefs")

/**
 * Sessão local do Neon Auth.
 *
 * O Neon Auth (Better Auth) devolve, no login/registo:
 *  - um cookie de sessão `__Secure-neon-auth.session_token = token.assinatura`
 *    válido por 7 dias — é ele que mantém a sessão no dispositivo;
 *  - um JWT de acesso (header `set-auth-jwt` do get-session), válido 15 min,
 *    usado nas chamadas à Data API.
 *
 * Guardamos o cookie (para renovar o JWT enquanto a sessão viver) e o JWT
 * em cache com a sua data de expiração. Tudo fica no DataStore privado do
 * app (não é acessível a outras aplicações no Android normal).
 */
data class LocalSession(
    val cookie: String?,
    val userId: String?,
    val email: String?,
    val displayName: String?,
    val avatarUrl: String?,
)

/** Cache do JWT de acesso (15 min) e do token anónimo (1 h). */
data class TokenCache(
    val token: String?,
    val expiresAt: Long?, // epoch millis
)

class SessionStore(private val context: Context) {

    private val keyCookie = stringPreferencesKey("session_cookie")
    private val keyUserId = stringPreferencesKey("user_id")
    private val keyEmail = stringPreferencesKey("email")
    private val keyDisplayName = stringPreferencesKey("display_name")
    private val keyAvatarUrl = stringPreferencesKey("avatar_url")

    private val keyJwt = stringPreferencesKey("jwt")
    private val keyJwtExp = longPreferencesKey("jwt_exp")

    private val keyAnon = stringPreferencesKey("anon_token")
    private val keyAnonExp = longPreferencesKey("anon_token_exp")

    val session: Flow<LocalSession> = context.sessionDataStore.data.map { prefs ->
        LocalSession(
            cookie = prefs[keyCookie],
            userId = prefs[keyUserId],
            email = prefs[keyEmail],
            displayName = prefs[keyDisplayName],
            avatarUrl = prefs[keyAvatarUrl],
        )
    }

    suspend fun current(): LocalSession = session.first()

    suspend fun save(
        cookie: String? = null,
        userId: String? = null,
        email: String? = null,
        displayName: String? = null,
        avatarUrl: String? = null,
    ) {
        context.sessionDataStore.edit { prefs ->
            cookie?.let { prefs[keyCookie] = it }
            userId?.let { prefs[keyUserId] = it }
            email?.let { prefs[keyEmail] = it }
            displayName?.let { prefs[keyDisplayName] = it }
            avatarUrl?.let { prefs[keyAvatarUrl] = it }
        }
    }

    /* ---------------- JWT autenticado ---------------- */

    suspend fun jwt(): TokenCache {
        val prefs = context.sessionDataStore.data.first()
        return TokenCache(
            token = prefs[keyJwt],
            expiresAt = prefs[keyJwtExp]?.let { it * 1000L },
        )
    }

    suspend fun saveJwt(token: String, expiresAtEpochSeconds: Long) {
        context.sessionDataStore.edit { prefs ->
            prefs[keyJwt] = token
            prefs[keyJwtExp] = expiresAtEpochSeconds
        }
    }

    suspend fun clearJwt() {
        context.sessionDataStore.edit { prefs ->
            prefs.remove(keyJwt)
            prefs.remove(keyJwtExp)
        }
    }

    /* ---------------- Token anónimo ---------------- */

    suspend fun anonymousToken(): TokenCache {
        val prefs = context.sessionDataStore.data.first()
        return TokenCache(
            token = prefs[keyAnon],
            expiresAt = prefs[keyAnonExp]?.let { it * 1000L },
        )
    }

    suspend fun saveAnonymousToken(token: String, expiresAtEpochSeconds: Long) {
        context.sessionDataStore.edit { prefs ->
            prefs[keyAnon] = token
            prefs[keyAnonExp] = expiresAtEpochSeconds
        }
    }

    /* ---------------- Limpeza ---------------- */

    suspend fun clearAll() {
        context.sessionDataStore.edit { prefs -> prefs.clear() }
    }
}
