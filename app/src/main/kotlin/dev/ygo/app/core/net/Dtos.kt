package dev.ygo.app.core.net

import dev.ygo.domain.CardCategory
import dev.ygo.domain.CardCondition
import dev.ygo.domain.CardLanguage
import kotlinx.serialization.Serializable

/**
 * Ce que l'API renvoie, tel quel. Les noms de champs sont ceux du serveur — pas de
 * `@SerialName` décoratif : ce qui porte un nom différent ici est un signal, pas un style.
 *
 * Règle valable partout dans ce fichier : **tout champ qui pourrait manquer est optionnel
 * avec un défaut**. L'app et le serveur ne sont pas déployés ensemble, et un téléphone qui
 * refuse de décoder une réponse d'un serveur plus ancien est un téléphone inutilisable. Même
 * raison pour les énumérations tolérantes ci-dessous : un statut inconnu vaut « je ne sais
 * pas », jamais une erreur.
 */

@Serializable
data class PublicUser(
    val id: String,
    val email: String,
    val username: String,
    val role: String = "USER",
    val createdAt: String = "",
    val avatarUrl: String? = null,
    val collectionLanguage: CardLanguage? = null,
    /** Préférences d'achat, absentes d'un serveur antérieur au réglage. */
    val marketLanguage: CardLanguage? = null,
    val marketMinCondition: CardCondition? = null,
) {
    val isAdmin: Boolean get() = role == "ADMIN"
}

/** Réponse d'auth avec l'en-tête `X-Auth-Mode: token` : les jetons sont dans le corps. */
@Serializable
data class TokenSession(
    val user: PublicUser,
    val accessToken: String,
    val refreshToken: String,
)

@Serializable
data class LoginBody(val email: String, val password: String)

@Serializable
data class RegisterBody(val email: String, val username: String, val password: String)

@Serializable
data class RefreshBody(val refreshToken: String)

@Serializable
data class CardSummary(
    val id: Int,
    /** Nom dans la langue de la requête. */
    val name: String,
    /**
     * Nom anglais, et seulement s'il diffère de [name] : c'est le seul que les places de
     * marché indexent. Son absence veut dire « [name] EST déjà l'anglais ».
     */
    val nameEn: String? = null,
    val category: CardCategory = CardCategory.OTHER,
    val type: String = "",
    val frameType: String = "",
    val archetype: String? = null,
    val attribute: String? = null,
    val race: String? = null,
    val level: Int? = null,
    val atk: Int? = null,
    val def: Int? = null,
    val imageUrl: String? = null,
    val imageUrlSmall: String? = null,
    val isExtraDeck: Boolean = false,
    val banTcg: String? = null,
    val banOcg: String? = null,
    val priceCardmarket: Double? = null,
    val ownedQuantity: Int? = null,
    val tagIds: List<String>? = null,
) {
    val owned: Int get() = ownedQuantity ?: 0
}

@Serializable
data class CardPrint(
    val id: String,
    val setCode: String? = null,
    val setName: String,
    val printCode: String,
    val rarity: String,
    val rarityCode: String? = null,
    val price: Double? = null,
)

@Serializable
data class CardDetail(
    val id: Int,
    val name: String,
    val nameEn: String? = null,
    val desc: String = "",
    val category: CardCategory = CardCategory.OTHER,
    val type: String = "",
    val frameType: String = "",
    val archetype: String? = null,
    val attribute: String? = null,
    val race: String? = null,
    val level: Int? = null,
    val atk: Int? = null,
    val def: Int? = null,
    val imageUrl: String? = null,
    val imageUrlSmall: String? = null,
    val isExtraDeck: Boolean = false,
    val banTcg: String? = null,
    val banOcg: String? = null,
    val priceCardmarket: Double? = null,
    val ownedQuantity: Int? = null,
    val tagIds: List<String>? = null,
    val prints: List<CardPrint> = emptyList(),
) {
    /** Le résumé, pour tout ce qui manipule des [CardSummary]. */
    val summary: CardSummary get() = CardSummary(
        id = id, name = name, nameEn = nameEn, category = category, type = type,
        frameType = frameType, archetype = archetype, attribute = attribute, race = race,
        level = level, atk = atk, def = def, imageUrl = imageUrl, imageUrlSmall = imageUrlSmall,
        isExtraDeck = isExtraDeck, banTcg = banTcg, banOcg = banOcg,
        priceCardmarket = priceCardmarket, ownedQuantity = ownedQuantity, tagIds = tagIds,
    )
}

/**
 * Résultat d'un code d'impression scanné. Le serveur fait le gros du travail : devant un code
 * dont il ne connaît pas l'extension, il va la chercher avant d'abandonner — une sortie
 * récente ne doit pas être une impasse pour le scan.
 */
@Serializable
data class PrintLookup(
    val status: Status = Status.UNRECOGNIZED,
    val code: String = "",
    val card: CardDetail? = null,
    /** Nom de l'extension rattrapée à la volée, si le serveur a dû aller la chercher. */
    val imported: String? = null,
) {
    @Serializable
    enum class Status {
        FOUND,
        INVALID_CODE,
        UNKNOWN_SET,
        UNKNOWN_NUMBER,

        /**
         * Statut qu'on ne connaît pas encore : un serveur plus récent ne doit pas faire
         * échouer le décodage, sinon le scan se réduirait à « introuvable » sans raison.
         *
         * C'est `coerceInputValues = true` (voir `ApiClient`) qui rabat une valeur inconnue
         * ici, et il ne le fait QUE parce que le champ a cette valeur par défaut. Retirer
         * l'un ou l'autre fait réapparaître l'exception.
         */
        UNRECOGNIZED,
    }
}

@Serializable
data class AddCollectionItemBody(
    val cardId: Int,
    val printId: String? = null,
    val quantity: Int = 1,
    val condition: CardCondition = CardCondition.NEAR_MINT,
    val language: CardLanguage,
    val firstEdition: Boolean = false,
)

@Serializable
data class Paginated<T>(
    val items: List<T> = emptyList(),
    val total: Int = 0,
    val page: Int = 1,
    val pageSize: Int = 20,
    val totalPages: Int = 1,
)

/** Corps d'erreur de l'API : c'est `message` qu'on montre, il est déjà dans la bonne langue. */
@Serializable
data class ApiError(val message: String? = null, val error: String? = null, val statusCode: Int? = null)
