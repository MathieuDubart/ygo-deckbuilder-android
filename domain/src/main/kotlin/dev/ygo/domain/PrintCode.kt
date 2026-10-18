package dev.ygo.domain

/**
 * Code d'impression tel qu'il est écrit sur la carte physique : « SDBE-FR001 », « LOB-EN001 »,
 * « MP22-FR266 ».
 *
 * On garde le préfixe de l'extension et le numéro, **sans la langue** : le catalogue ne stocke
 * que les impressions anglaises, mais le numéro est le même d'une langue à l'autre. C'est ce
 * qui permet de scanner une carte française et de retrouver son impression.
 *
 * Portage de `packages/shared/src/domain/print-code.ts` et de son jumeau Swift. Les trois
 * doivent rester d'accord : un code reconnu d'un côté et pas de l'autre, c'est une carte qu'on
 * peut ranger depuis le web mais pas depuis le téléphone.
 */
data class PrintCode(
    val set: String,
    val number: String,
    /** Région lue dans le code (FR, EN…), quand elle y est. */
    val region: String?,
) {
    /** « SDBE-EN001 » correspond-il au code scanné « SDBE-FR001 » ? La langue est ignorée. */
    fun matches(printCode: String): Boolean {
        val other = parse(printCode) ?: return false
        return other.set == set && other.number == number
    }

    val text: String get() = "$set-${region ?: ""}$number"

    companion object {
        private val PATTERN = Regex("""\s*([A-Za-z0-9]{2,5})-([A-Za-z]{0,2})(\d{2,4})\s*""")

        /** Null si le texte n'est pas un code d'impression entier. */
        fun parse(text: String): PrintCode? {
            val m = PATTERN.matchEntire(text) ?: return null
            val region = m.groupValues[2]
            return PrintCode(
                set = m.groupValues[1].uppercase(),
                number = m.groupValues[3],
                region = region.ifEmpty { null }?.uppercase(),
            )
        }
    }
}

/**
 * Extraction des codes d'impression dans un texte lu par la caméra.
 *
 * La reconnaissance de texte confond régulièrement O avec 0 et I ou L avec 1 — sur un numéro
 * de carte, ces trois lettres n'ont aucune raison d'exister, donc les rabattre sur des
 * chiffres rattrape une lecture sur deux sans jamais inventer un code valide à partir d'un
 * texte qui n'en est pas un : le résultat est revalidé par `PrintCode.parse`.
 *
 * Deux précautions valent une explication, parce que les retirer produit un bug silencieux.
 *
 * On ne replie que les espaces **autour du tiret** — l'artefact OCR qu'on voulait rattraper —
 * et surtout pas tous les espaces du texte : sur « ULTRA RARE SDBE-FR001 1st », tout coller
 * donne « ULTRARARESDBE-FR0011ST », d'où le lecteur tire « ESDBE-FR0011 ». Ce faux code est
 * assez bien formé pour passer la validation, donc le scan irait demander au serveur une
 * carte qui n'existe pas et annoncerait « introuvable » sur une carte parfaitement lisible.
 *
 * Et le motif est bordé des deux côtés : une frontière de mot avant le préfixe, rien qui
 * ressemble à un chiffre après le numéro. Sans ces bornes, un mot voisin déborde dans le
 * code même avec les espaces conservés. La formulation est choisie pour être la même dans
 * les trois portages — `\b` et l'anticipation négative existent partout, le lookbehind non.
 *
 * Le `\s*` entre la région et le numéro garde ce que la suppression globale rattrapait au
 * passage : l'OCR coupe parfois après la région, « SDBE-FR 001 », et ce code-là est bon.
 */
object PrintCodeReader {
    private val PATTERN =
        Regex("""\b([A-Z0-9]{2,5})-([A-Z]{0,2})\s*([0-9OIL]{3,4})(?![0-9OIL])""")
    private val AROUND_DASH = Regex("""\s*-\s*""")

    fun codes(text: String): List<String> {
        val upper = AROUND_DASH.replace(text.uppercase(), "-")
        return PATTERN.findAll(upper).mapNotNull { match ->
            val digits = match.groupValues[3]
                .replace('O', '0')
                .replace('I', '1')
                .replace('L', '1')
            val code = "${match.groupValues[1]}-${match.groupValues[2]}$digits"
            if (PrintCode.parse(code) != null) code else null
        }.toList()
    }
}
