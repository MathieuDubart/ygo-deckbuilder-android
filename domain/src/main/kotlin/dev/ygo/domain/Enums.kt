package dev.ygo.domain

/**
 * Les énumérations partagées avec l'API, alignées sur `packages/shared/src/domain/enums.ts`.
 * L'ordre compte pour `CardCondition` : du meilleur au pire, et plusieurs règles s'appuient
 * dessus (l'état minimum d'un lien Cardmarket, par exemple).
 */
enum class CardCondition { MINT, NEAR_MINT, EXCELLENT, GOOD, LIGHT_PLAYED, PLAYED, POOR }

enum class CardLanguage { FR, EN, DE, IT, ES, PT, JP, KR }

enum class DeckZone { MAIN, EXTRA, SIDE }

enum class WishlistPriority { LOW, MEDIUM, HIGH }

enum class CardCategory { MONSTER, SPELL, TRAP, SKILL, TOKEN, OTHER }

/**
 * Statut de banlist, normalisé. Les sources ne s'accordent pas sur les libellés — YGOPRODeck
 * écrit « Banned », Konami « Forbidden » — alors qu'il n'existe que trois statuts. On
 * normalise à l'entrée, une fois.
 */
enum class BanStatus {
    FORBIDDEN, LIMITED, SEMI_LIMITED;

    companion object {
        fun of(label: String?): BanStatus? {
            if (label == null) return null
            // Les suites de blancs se replient en UN séparateur, comme le /[\s_]+/ du web :
            // « Semi  Limited » et « Semi\tLimited » circulent, et les manquer rendrait la
            // carte jouable en trois exemplaires au lieu de deux.
            val parts = label.lowercase().split(Regex("""[\s_]+""")).filter { it.isNotEmpty() }
            return when (parts.joinToString("-")) {
                "banned", "forbidden" -> FORBIDDEN
                "limited" -> LIMITED
                "semi-limited" -> SEMI_LIMITED
                else -> null
            }
        }
    }
}

/** Règles de construction d'un deck, identiques au web et à l'app iOS. */
object DeckRules {
    const val MAX_COPIES = 3
    val MAIN = 40..60
    const val EXTRA_MAX = 15
    const val SIDE_MAX = 15

    /** Exemplaires autorisés d'une carte, selon son statut de banlist. */
    fun maxCopies(banLabel: String?): Int = when (BanStatus.of(banLabel)) {
        BanStatus.FORBIDDEN -> 0
        BanStatus.LIMITED -> 1
        BanStatus.SEMI_LIMITED -> 2
        null -> MAX_COPIES
    }

    /** Plafond de la zone, pour savoir s'il reste de la place. */
    fun capacity(zone: DeckZone): Int = when (zone) {
        DeckZone.MAIN -> MAIN.last
        DeckZone.EXTRA -> EXTRA_MAX
        DeckZone.SIDE -> SIDE_MAX
    }
}
