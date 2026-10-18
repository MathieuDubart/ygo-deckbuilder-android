package dev.ygo.app.core.l10n

import android.content.Context
import android.icu.text.MessageFormat
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.util.Locale

/**
 * Les textes de l'app, lus dans les **mêmes fichiers que le web et l'app iOS**.
 *
 * `scripts/sync-messages.py` fusionne les messages du front web et ceux propres à Android en
 * `app/src/main/assets/messages.<langue>.json`. Ces fichiers sont donc générés : les éditer
 * à la main, c'est perdre ses textes au prochain sync. Une clé nouvelle qui existe sur le web
 * va dans le dépôt web ; une clé propre à Android va dans `localization/android.<langue>.json`,
 * sous le préfixe `android.`.
 *
 * Le format est celui d'ICU MessageFormat, comme next-intl côté web — `{count, plural, one
 * {# carte} other {# cartes}}`. `android.icu.text.MessageFormat` le parle nativement, donc
 * les pluriels des cinq langues marchent sans rien réécrire.
 */
object L10n {
    private const val FALLBACK = "en"
    private val json = Json { ignoreUnknownKeys = true }

    private var messages: JsonObject = JsonObject(emptyMap())
    private var fallback: JsonObject = JsonObject(emptyMap())

    /** Langue courante, en code à deux lettres : celle de l'appareil si on la parle. */
    var locale: String = FALLBACK
        private set

    private val supported = setOf("en", "fr", "de", "it", "pt")

    fun load(context: Context, preferred: String? = null) {
        val device = preferred ?: Locale.getDefault().language
        locale = if (device in supported) device else FALLBACK
        messages = read(context, locale)
        fallback = if (locale == FALLBACK) messages else read(context, FALLBACK)
    }

    private fun read(context: Context, lang: String): JsonObject = runCatching {
        context.assets.open("messages.$lang.json").bufferedReader().use {
            json.parseToJsonElement(it.readText()) as JsonObject
        }
    }.getOrDefault(JsonObject(emptyMap()))

    /**
     * Le texte d'une clé pointée, avec ses paramètres.
     *
     * Une clé absente se rend **telle quelle** plutôt que vide : à l'écran, « wishlist.row.buy »
     * se voit tout de suite et se corrige, alors qu'un bouton sans libellé passe inaperçu.
     * L'anglais sert de repli, exactement comme sur le web.
     */
    fun t(key: String, args: Map<String, Any> = emptyMap()): String {
        val raw = lookup(messages, key) ?: lookup(fallback, key) ?: return key
        if (args.isEmpty()) return raw
        return runCatching {
            MessageFormat(raw, Locale.forLanguageTag(locale)).format(args)
        }.getOrDefault(raw)
    }

    private fun lookup(root: JsonObject, key: String): String? {
        var node: Any? = root
        for (part in key.split('.')) {
            node = (node as? JsonObject)?.get(part) ?: return null
        }
        return (node as? JsonPrimitive)?.content
    }
}

/** Raccourci, pour écrire `t("...")` dans les écrans comme côté iOS. */
fun t(key: String, args: Map<String, Any> = emptyMap()): String = L10n.t(key, args)
