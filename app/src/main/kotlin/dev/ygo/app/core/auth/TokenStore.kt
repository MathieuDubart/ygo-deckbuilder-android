package dev.ygo.app.core.auth

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first

private val Context.authStore by preferencesDataStore(name = "auth")

/**
 * Les jetons de session et l'adresse du serveur.
 *
 * Ils vivent dans un DataStore de préférences, pas dans le Keystore. C'est un choix assumé
 * et noté : un jeton d'accès dure quinze minutes et le rafraîchissement est révocable côté
 * serveur, donc le gain d'un chiffrement matériel est faible devant sa complexité — mais si
 * l'app doit un jour tenir sur un appareil partagé, c'est ici qu'il faut revenir.
 */
class TokenStore(private val context: Context) {
    private val accessKey = stringPreferencesKey("accessToken")
    private val refreshKey = stringPreferencesKey("refreshToken")
    private val serverKey = stringPreferencesKey("serverUrl")

    /** Gardés en mémoire : chaque requête les lit, et toucher le disque à chaque fois coûte. */
    @Volatile private var cachedAccess: String? = null
    @Volatile private var cachedRefresh: String? = null
    @Volatile private var cachedServer: String? = null
    @Volatile private var loaded = false

    suspend fun load() {
        val prefs = context.authStore.data.first()
        cachedAccess = prefs[accessKey]
        cachedRefresh = prefs[refreshKey]
        cachedServer = prefs[serverKey]
        loaded = true
    }

    fun accessToken(): String? = cachedAccess
    fun refreshToken(): String? = cachedRefresh
    fun serverUrl(): String? = cachedServer
    fun isLoaded(): Boolean = loaded

    suspend fun save(access: String, refresh: String) {
        cachedAccess = access
        cachedRefresh = refresh
        context.authStore.edit {
            it[accessKey] = access
            it[refreshKey] = refresh
        }
    }

    suspend fun setServer(url: String) {
        cachedServer = url
        context.authStore.edit { it[serverKey] = url }
    }

    suspend fun clearSession() {
        cachedAccess = null
        cachedRefresh = null
        // L'adresse du serveur survit à une déconnexion : on se reconnecte à la même
        // instance, et la retaper à chaque fois serait absurde.
        context.authStore.edit {
            it.remove(accessKey)
            it.remove(refreshKey)
        }
    }
}
