package dev.ygo.app.core.net

import dev.ygo.app.core.auth.TokenStore
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json

/** Une réponse d'erreur de l'API, avec le message déjà traduit par le serveur. */
class ApiException(val status: Int, override val message: String) : Exception(message)

/** Le jeton a expiré et n'a pas pu être renouvelé : il faut se reconnecter. */
class SessionExpiredException : Exception("session expired")

val ApiJson = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
    // Rabat une valeur inconnue sur le défaut du champ, pour les énumérations que le serveur
    // peut enrichir avant l'app (voir `PrintLookup.Status.UNRECOGNIZED`).
    coerceInputValues = true
}

/**
 * Le client HTTP de l'app : une instance, un serveur, une session.
 *
 * L'authentification suit le même contrat que l'app iOS — en-tête `X-Auth-Mode: token`, donc
 * les jetons arrivent dans le corps plutôt qu'en cookies, et le rafraîchissement se fait avec
 * `{ refreshToken }` dans le corps. Le renouvellement est protégé par un verrou : dix
 * requêtes qui prennent un 401 en même temps ne doivent produire qu'un seul refresh, sinon
 * les suivantes présentent un jeton déjà révoqué et la session saute pour rien.
 */
class ApiClient(
    private val tokens: TokenStore,
    private val baseUrlProvider: () -> String?,
    private val localeProvider: () -> String,
    /** Appelé quand le rafraîchissement échoue : l'app doit revenir à l'écran de connexion. */
    var onSessionExpired: () -> Unit = {},
) {
    private val refreshLock = Mutex()

    private val http = HttpClient(OkHttp) {
        expectSuccess = false
        install(ContentNegotiation) { json(ApiJson) }
        defaultRequest {
            contentType(ContentType.Application.Json)
            header("X-Auth-Mode", "token")
            header("Accept-Language", localeProvider())
        }
    }

    /**
     * `<serveur>/api/<chemin>`.
     *
     * Le segment `api` est ajouté ici, et non écrit dans chaque route : c'est le préfixe que
     * le front web relaie vers l'API, et les contrôleurs NestJS n'en portent pas la trace
     * (`@Controller('auth')`, pas `@Controller('api/auth')`). Les routes de `Endpoints`
     * s'écrivent donc comme côté serveur, et l'adresse enregistrée reste celle du serveur —
     * la même qu'on tape dans un navigateur.
     *
     * Même construction que `APIClient.swift` côté iOS. Les deux doivent rester d'accord :
     * une adresse qui marche sur un téléphone et pas sur l'autre est indébogable à distance.
     */
    private fun url(path: String): String {
        val base = baseUrlProvider()?.trimEnd('/')
            ?: throw ApiException(0, "Aucun serveur configuré")
        return "$base/api/${path.trimStart('/')}"
    }

    suspend inline fun <reified T> get(path: String, noinline block: HttpRequestBuilder.() -> Unit = {}): T =
        request(path, "GET", null, block)

    suspend inline fun <reified T> post(path: String, body: Any? = null): T =
        request(path, "POST", body) {}

    suspend inline fun <reified T> put(path: String, body: Any? = null): T =
        request(path, "PUT", body) {}

    suspend inline fun <reified T> patch(path: String, body: Any? = null): T =
        request(path, "PATCH", body) {}

    /** Pour les routes qui ne renvoient rien (204). */
    suspend fun perform(path: String, method: String, body: Any? = null) {
        val response = sendAuthorized(path, method, body) {}
        ensureSuccess(response)
    }

    suspend inline fun <reified T> request(
        path: String,
        method: String,
        body: Any?,
        noinline block: HttpRequestBuilder.() -> Unit,
    ): T {
        val response = sendAuthorized(path, method, body, block)
        ensureSuccess(response)
        return response.body()
    }

    /**
     * Envoie, et en cas de 401 renouvelle le jeton puis rejoue **une** fois. Rejouer plus
     * d'une fois ferait boucler une session définitivement périmée.
     */
    suspend fun sendAuthorized(
        path: String,
        method: String,
        body: Any?,
        block: HttpRequestBuilder.() -> Unit,
    ): HttpResponse {
        val first = send(path, method, body, block)
        if (first.status != HttpStatusCode.Unauthorized) return first
        if (!refresh()) {
            onSessionExpired()
            throw SessionExpiredException()
        }
        return send(path, method, body, block)
    }

    suspend fun send(
        path: String,
        method: String,
        body: Any?,
        block: HttpRequestBuilder.() -> Unit,
    ): HttpResponse {
        val builder: HttpRequestBuilder.() -> Unit = {
            tokens.accessToken()?.let { header("Authorization", "Bearer $it") }
            if (body != null) setBody(body)
            block()
        }
        val target = url(path)
        return when (method) {
            "GET" -> http.get(target, builder)
            "POST" -> http.post(target, builder)
            "PUT" -> http.put(target, builder)
            "PATCH" -> http.patch(target, builder)
            "DELETE" -> http.delete(target, builder)
            else -> error("méthode inconnue : $method")
        }
    }

    /** Vrai si la session est repartie. Un seul renouvellement à la fois. */
    private suspend fun refresh(): Boolean = refreshLock.withLock {
        val refreshToken = tokens.refreshToken() ?: return false
        return runCatching {
            val response = http.post(url("auth/refresh")) {
                setBody(RefreshBody(refreshToken))
            }
            if (!response.status.isSuccess()) return false
            val session: TokenSession = response.body()
            tokens.save(session.accessToken, session.refreshToken)
            true
        }.getOrDefault(false)
    }

    suspend fun ensureSuccess(response: HttpResponse) {
        if (response.status.isSuccess()) return
        val text = runCatching { response.bodyAsText() }.getOrDefault("")
        val message = runCatching { ApiJson.decodeFromString<ApiError>(text).message }
            .getOrNull()
            ?: text.ifBlank { "Erreur ${response.status.value}" }
        throw ApiException(response.status.value, message)
    }

    /** Connexion : on garde les jetons avant de rendre l'utilisateur. */
    suspend fun signIn(path: String, body: Any): PublicUser {
        val response = http.post(url(path)) { setBody(body) }
        ensureSuccess(response)
        val session: TokenSession = response.body()
        tokens.save(session.accessToken, session.refreshToken)
        return session.user
    }
}
