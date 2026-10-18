package dev.ygo.app.core.state

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import dev.ygo.app.core.auth.TokenStore
import dev.ygo.app.core.l10n.L10n
import dev.ygo.app.core.net.ApiClient
import dev.ygo.app.core.net.Endpoints
import dev.ygo.app.core.net.PublicUser
import dev.ygo.domain.CardLanguage
import dev.ygo.domain.Cardmarket
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * L'état partagé de l'app : le serveur, la session, et les compteurs de rafraîchissement.
 *
 * Même rôle que l'`AppState` de l'app iOS, et volontairement la même forme — un objet unique
 * passé par le haut, pas d'injection de dépendances. À trois écrans comme à trente, ça reste
 * lisible, et les deux apps se relisent l'une l'autre.
 *
 * Les `version` sont le signal de rechargement : un écran qui dépend de la collection
 * s'abonne à `collectionVersion`, et toute action qui la modifie appelle `collectionChanged()`.
 */
class AppState(context: Context, private val scope: CoroutineScope) {

    val tokens = TokenStore(context.applicationContext)

    val api = ApiClient(
        tokens = tokens,
        baseUrlProvider = { tokens.serverUrl() },
        localeProvider = { L10n.locale },
    ).also { client ->
        client.onSessionExpired = { scope.launch { signOut() } }
    }

    val endpoints = Endpoints(api)

    sealed interface Session {
        data object Loading : Session
        /** Aucun serveur choisi : on ne peut même pas proposer de se connecter. */
        data object NeedsServer : Session
        data object SignedOut : Session
        data class SignedIn(val user: PublicUser) : Session
    }

    var session by mutableStateOf<Session>(Session.Loading)
        private set

    var collectionVersion by mutableStateOf(0)
        private set

    val user: PublicUser? get() = (session as? Session.SignedIn)?.user

    /**
     * La langue dans laquelle ranger une carte qui arrive : le choix de l'utilisateur, sinon
     * celle de l'interface. C'est le défaut du scan — ranger selon la région lue sur la
     * carte est précisément ce qui mélangeait les collections.
     */
    val collectionLanguage: CardLanguage
        get() = user?.collectionLanguage ?: runCatching {
            CardLanguage.valueOf(L10n.locale.uppercase())
        }.getOrDefault(CardLanguage.EN)

    /**
     * Les préférences d'achat telles qu'un lien doit les appliquer. Une langue d'achat non
     * réglée suit celle de la collection : c'est déjà un choix assumé.
     */
    val marketPreferences: Cardmarket.Preferences
        get() = Cardmarket.Preferences(
            language = user?.marketLanguage ?: user?.collectionLanguage,
            minCondition = user?.marketMinCondition,
        )

    /** Au lancement : on relit le disque, puis le serveur si on a de quoi. */
    suspend fun start() {
        tokens.load()
        if (tokens.serverUrl() == null) {
            session = Session.NeedsServer
            return
        }
        session = if (tokens.accessToken() == null) Session.SignedOut else restore()
    }

    private suspend fun restore(): Session =
        runCatching { Session.SignedIn(endpoints.me()) }.getOrDefault(Session.SignedOut)

    suspend fun setServer(url: String) {
        tokens.setServer(url)
        session = Session.SignedOut
    }

    fun signedIn(user: PublicUser) {
        session = Session.SignedIn(user)
    }

    suspend fun signOut() {
        tokens.clearSession()
        session = Session.SignedOut
    }

    /** Le serveur fait foi : après un réglage, on relit l'utilisateur. */
    suspend fun refreshUser() {
        val fresh = runCatching { endpoints.me() }.getOrNull() ?: return
        if (session is Session.SignedIn) session = Session.SignedIn(fresh)
    }

    fun collectionChanged() { collectionVersion++ }
}
