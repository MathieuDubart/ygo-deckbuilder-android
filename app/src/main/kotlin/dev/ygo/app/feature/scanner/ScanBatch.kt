package dev.ygo.app.feature.scanner

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import dev.ygo.app.core.net.AddCollectionItemBody
import dev.ygo.app.core.net.CardPrint
import dev.ygo.app.core.net.CardSummary
import dev.ygo.app.core.net.Endpoints
import dev.ygo.domain.CardCondition
import dev.ygo.domain.CardLanguage
import java.util.UUID

/**
 * Une carte en attente d'être ajoutée à la collection : ce que le scan a trouvé, modifiable
 * avant validation.
 */
class ScanDraft(
    val card: CardSummary,
    /** Impressions connues de la carte, pour le sélecteur d'édition. */
    val prints: List<CardPrint>,
    /** Code lu par la caméra, null si la carte a été ajoutée à la main. */
    val code: String?,
    printId: String?,
    quantity: Int = 1,
    condition: CardCondition = CardCondition.NEAR_MINT,
    language: CardLanguage,
    firstEdition: Boolean = false,
) {
    val id: String = UUID.randomUUID().toString()

    var printId by mutableStateOf(printId)
    var quantity by mutableStateOf(quantity)
    var condition by mutableStateOf(condition)
    var language by mutableStateOf(language)
    var firstEdition by mutableStateOf(firstEdition)

    val print: CardPrint? get() = prints.firstOrNull { it.id == printId }

    val subtitle: String
        get() = print?.let { "${it.printCode} — ${it.rarity}" } ?: code ?: card.type
}

/**
 * La file des cartes scannées. On scanne en continu, on vérifie à la fin.
 *
 * Repasser la même carte n'ajoute pas une deuxième ligne : on incrémente celle qui existe
 * déjà, pour la même carte et la même impression. C'est ce qui permet d'empiler trois
 * exemplaires d'un Dragon Blanc en trois passages sans avoir trois lignes à fusionner après.
 */
class ScanBatch {
    private val _drafts = mutableStateListOf<ScanDraft>()
    val drafts: List<ScanDraft> get() = _drafts

    var last by mutableStateOf<ScanDraft?>(null)
        private set
    var saving by mutableStateOf(false)
        private set
    var saved by mutableStateOf(0)
        private set
    var failures by mutableStateOf<List<String>>(emptyList())
        private set

    val isEmpty: Boolean get() = _drafts.isEmpty()
    val totalCopies: Int get() = _drafts.sumOf { it.quantity }

    fun add(
        card: CardSummary,
        prints: List<CardPrint>,
        code: String?,
        printId: String?,
        language: CardLanguage,
    ) {
        val existing = _drafts.firstOrNull { it.card.id == card.id && it.printId == printId }
        if (existing != null) {
            existing.quantity += 1
            last = existing
            return
        }
        val draft = ScanDraft(card, prints, code, printId, language = language)
        _drafts += draft
        last = draft
    }

    /** Ajout à la main depuis la relecture, sans code lu. */
    fun addManual(card: CardSummary, prints: List<CardPrint>, language: CardLanguage) {
        val draft = ScanDraft(card, prints, code = null, printId = prints.firstOrNull()?.id, language = language)
        _drafts += draft
        last = draft
    }

    fun remove(draft: ScanDraft) {
        _drafts.remove(draft)
        if (last?.id == draft.id) last = _drafts.lastOrNull()
    }

    /** Annule le dernier geste : un exemplaire de moins, et la ligne si elle tombe à zéro. */
    fun undoLast() {
        val target = last ?: return
        if (target.quantity > 1) {
            target.quantity -= 1
            return
        }
        remove(target)
    }

    /**
     * Envoie tout. Une ligne qui échoue n'emporte pas les autres : on continue et on rend
     * la liste des refus, parce qu'après vingt cartes scannées, tout rejouer serait pire
     * que de corriger les deux qui ont raté.
     */
    suspend fun submit(endpoints: Endpoints): Boolean {
        if (saving) return false
        saving = true
        saved = 0
        val refused = mutableListOf<String>()
        try {
            for (draft in _drafts.toList()) {
                val body = AddCollectionItemBody(
                    cardId = draft.card.id,
                    printId = draft.printId,
                    quantity = draft.quantity,
                    condition = draft.condition,
                    language = draft.language,
                    firstEdition = draft.firstEdition,
                )
                runCatching { endpoints.addToCollection(body) }
                    .onSuccess { saved += 1 }
                    .onFailure { refused += draft.card.name }
            }
        } finally {
            saving = false
        }
        failures = refused
        if (refused.isEmpty()) _drafts.clear()
        return refused.isEmpty()
    }
}
