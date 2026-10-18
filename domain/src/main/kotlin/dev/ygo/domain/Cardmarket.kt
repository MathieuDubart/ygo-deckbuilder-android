package dev.ygo.domain

/**
 * Liens vers Cardmarket, la place de marché européenne où l'on achète les cartes à l'unité.
 *
 * Portage de `packages/shared/src/domain/cardmarket.ts` et de son jumeau Swift : les trois
 * doivent rester d'accord, un lien qui diffère d'une plateforme à l'autre étant un lien faux
 * quelque part. Tout est construit ici, sans réseau : Cardmarket n'a pas d'API publique
 * utilisable sans compte vendeur, et leur site est derrière une protection anti-robot. On ne
 * lit donc jamais leurs pages — on fabrique une URL que l'utilisateur ouvre lui-même, dans son
 * navigateur, où il est déjà connecté.
 *
 * Deux limites assumées, qui expliquent la forme de ce fichier :
 *
 *  1. **On passe par la recherche, pas par la fiche produit.** Une fiche a l'adresse
 *     `/Products/Singles/<Extension>/<Carte>`, mais ces deux morceaux sont les noms de
 *     Cardmarket, qui ne sont pas ceux de notre catalogue (YGOPRODeck). Deviner le libellé
 *     d'une extension donnerait un 404 ; une recherche sur le nom anglais tombe toujours juste.
 *  2. **Il n'existe pas de panier pré-rempli par URL.** L'ajout passe par une session
 *     connectée, carte par carte. Pour un deck entier on produit donc un texte à coller
 *     (`wantsListText`), que l'assistant d'achat de Cardmarket sait ensuite exploiter.
 */
object Cardmarket {
    private const val HOST = "https://www.cardmarket.com"

    /** Les cinq langues d'interface de Cardmarket. Les autres retombent sur l'anglais. */
    private val SITE_LOCALES = setOf("en", "fr", "de", "it", "es")

    /**
     * Identifiants de langue de Cardmarket, tels que leur API les documente. Les langues
     * qu'ils ne numérotent pas publiquement (japonais, coréen, portugais) sont absentes
     * exprès : sans identifiant sûr, mieux vaut une fiche non filtrée qu'un filtre au hasard.
     */
    private val LANGUAGE_IDS = mapOf(
        CardLanguage.EN to 1,
        CardLanguage.FR to 2,
        CardLanguage.DE to 3,
        CardLanguage.ES to 4,
        CardLanguage.IT to 5,
    )

    /**
     * Les langues sur lesquelles on sait filtrer, dans l'ordre d'affichage. C'est la liste à
     * proposer dans un réglage : offrir le japonais donnerait un choix sans effet, ce qui est
     * pire que ne pas l'offrir.
     */
    val languages: List<CardLanguage> =
        listOf(CardLanguage.EN, CardLanguage.FR, CardLanguage.DE, CardLanguage.ES, CardLanguage.IT)

    /**
     * État minimum accepté. Cardmarket ordonne les états exactement comme nous
     * (`CardCondition.entries`), du meilleur au pire, et les numérote à partir de 1 — d'où la
     * position dans la liste.
     */
    private fun conditionId(condition: CardCondition): Int =
        CardCondition.entries.indexOf(condition) + 1

    /** Ce que l'utilisateur veut voir en arrivant sur une fiche. Tout est facultatif. */
    data class Preferences(
        /** Langue des exemplaires cherchés. Par défaut, celle de rangement de la collection. */
        val language: CardLanguage? = null,
        /** État minimum accepté. */
        val minCondition: CardCondition? = null,
    )

    /**
     * Lien vers une carte, filtré selon les préférences.
     *
     * [nameEn] est le nom **anglais** : c'est celui que Cardmarket indexe, et un nom français
     * n'y renvoie rien. Les filtres s'appliquent au tableau des offres une fois sur la fiche ;
     * sur la page de résultats ils sont simplement ignorés, ce qui ne coûte rien.
     */
    fun searchUrl(
        nameEn: String,
        preferences: Preferences = Preferences(),
        locale: String = "en",
    ): String {
        val site = if (locale in SITE_LOCALES) locale else "en"
        val query = mutableListOf("searchString=${encode(nameEn)}")
        preferences.language?.let { LANGUAGE_IDS[it] }?.let { query += "language=$it" }
        preferences.minCondition?.let { query += "minCondition=${conditionId(it)}" }
        return "$HOST/$site/YuGiOh/Products/Search?${query.joinToString("&")}"
    }

    /** Les listes de souhaits de l'utilisateur, où se colle le texte produit ci-dessous. */
    fun wantsUrl(locale: String = "en"): String {
        val site = if (locale in SITE_LOCALES) locale else "en"
        return "$HOST/$site/YuGiOh/Wants"
    }

    /** Une ligne de la liste à coller : une carte, et combien il en manque. */
    data class Wanted(val nameEn: String, val quantity: Int)

    /**
     * Le texte à coller dans une liste de souhaits : une ligne par carte, `quantité nom`.
     *
     * Volontairement sans extension ni rareté. Cardmarket nomme ses extensions autrement que
     * notre catalogue, et une ligne dont l'extension n'est pas reconnue est rejetée entière —
     * on perdrait la carte pour gagner une précision que l'acheteur ajuste de toute façon sur
     * place. Les cartes sont rangées par nom pour que deux exports du même deck donnent le
     * même texte, et les doublons sont cumulés : une carte demandée en main et en extra n'y
     * figure qu'une fois.
     */
    fun wantsListText(cards: List<Wanted>): String {
        val total = LinkedHashMap<String, Int>()
        for (card in cards) {
            if (card.quantity > 0) total[card.nameEn] = (total[card.nameEn] ?: 0) + card.quantity
        }
        return total.keys.sorted().joinToString("\n") { "${total[it]} $it" }
    }

    /**
     * `encodeURIComponent` du web, à la main : `java.net.URLEncoder` écrit les espaces en `+`
     * et n'échappe pas tout ce qu'il faut, ce qui ferait diverger l'URL de celle des deux
     * autres plateformes.
     */
    private fun encode(value: String): String = buildString {
        for (byte in value.toByteArray(Charsets.UTF_8)) {
            val c = byte.toInt().toChar()
            if (c.isLetterOrDigit() && c.code < 128 || c in "-_.!~*'()") append(c)
            else append('%').append("%02X".format(byte))
        }
    }
}

/**
 * Le nom sous lequel une carte se cherche sur une place de marché : l'anglais quand on l'a,
 * sinon celui qu'on a. L'API ne renvoie `nameEn` que lorsqu'il diffère du nom affiché, donc
 * son absence veut dire « le nom affiché EST l'anglais », pas « on ne sait pas ».
 */
fun marketName(name: String, nameEn: String?): String = nameEn ?: name
