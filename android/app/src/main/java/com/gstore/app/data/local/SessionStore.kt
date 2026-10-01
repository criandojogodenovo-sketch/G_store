package com.gstore.app.data.local

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.sessionDataStore by preferencesDataStore(name = "session_prefs")

/**
 * Sessão local: guarda o JWT curto do Appwrite e o id do usuário para
 * revalidação. O JWT expira em ~15 min; quando expirar, o app refaz login
 * silenciosamente com a sessão do Appwrite (Account.createJWT).
 */
data class LocalSession(
    val jwt: String?,
    val appwriteUserId: String?,
    val email: String?,
    val displayName: String?,
)

class SessionStore(private val context: Context) {

    private val keyJwt = stringPreferencesKey("jwt")
    private val keyUserId = stringPreferencesKey("appwrite_user_id")
    private val keyEmail = stringPreferencesKey("email")
    private val keyDisplayName = stringPreferencesKey("display_name")

    val session: Flow<LocalSession> = context.sessionDataStore.data.map { prefs ->
        LocalSession(
            jwt = prefs[keyJwt],
            appwriteUserId = prefs[keyUserId],
            email = prefs[keyEmail],
            displayName = prefs[keyDisplayName],
        )
    }

    suspend fun current(): LocalSession = session.first()

    suspend fun save(jwt: String?, userId: String?, email: String?, displayName: String?) {
        context.sessionDataStore.edit { prefs ->
            jwt?.let { prefs[keyJwt] = it }
            userId?.let { prefs[keyUserId] = it }
            email?.let { prefs[keyEmail] = it }
            displayName?.let { prefs[keyDisplayName] = it }
        }
    }

    suspend fun clearJwt() {
        context.sessionDataStore.edit { prefs -> prefs.remove(keyJwt) }
    }

    suspend fun clearAll() {
        context.sessionDataStore.edit { prefs -> prefs.clear() }
    }
}
