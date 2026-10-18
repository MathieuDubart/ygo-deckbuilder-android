package dev.ygo.app.core.net

import io.ktor.client.request.parameter

/**
 * Les routes de l'API, une fonction chacune. C'est le miroir de `Core/Networking/Endpoints.swift`
 * côté iOS : même découpage, mêmes noms, pour qu'une évolution du serveur se répercute au
 * même endroit dans les deux apps.
 */
class Endpoints(private val api: ApiClient) {

    // MARK: Session

    suspend fun login(email: String, password: String): PublicUser =
        api.signIn("auth/login", LoginBody(email.lowercase(), password))

    suspend fun register(email: String, username: String, password: String): PublicUser =
        api.signIn("auth/register", RegisterBody(email.lowercase(), username, password))

    suspend fun me(): PublicUser = api.get("auth/me")

    // MARK: Catalogue et collection

    suspend fun addToCollection(body: AddCollectionItemBody) {
        api.perform("collection", "POST", body)
    }

    suspend fun card(id: Int): CardDetail = api.get("cards/$id")

    suspend fun searchCards(query: String, pageSize: Int = 20): Paginated<CardSummary> =
        api.get("cards") {
            parameter("q", query)
            parameter("pageSize", pageSize)
        }

    /**
     * Un code imprimé → la carte, en une requête.
     *
     * Surtout pas de pourcent-encodage : le tiret est légal dans un chemin, et un `%2D`
     * serait ré-encodé — le serveur recevrait un code qui ne correspond à rien. Un code lu
     * ne contient de toute façon que des lettres, des chiffres et un tiret ; on écarte
     * seulement ce qui le découperait.
     */
    suspend fun cardByPrintCode(code: String): PrintLookup {
        val safe = code.filter { it.isLetterOrDigit() || it == '-' }
        return api.get("cards/by-print-code/$safe")
    }
}
