package dev.ygo.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.assertFalse

class PrintCodeTest {
    @Test
    fun `lit un code complet`() {
        val code = PrintCode.parse("SDBE-FR001")!!
        assertEquals("SDBE", code.set)
        assertEquals("001", code.number)
        assertEquals("FR", code.region)
    }

    @Test
    fun `accepte un code sans région`() {
        val code = PrintCode.parse("LOB-001")!!
        assertEquals("LOB", code.set)
        assertNull(code.region)
    }

    @Test
    fun `met l'extension en majuscules et tolère les blancs`() {
        assertEquals("MP22", PrintCode.parse("  mp22-fr266 ")!!.set)
    }

    @Test
    fun `refuse ce qui n'est pas un code entier`() {
        assertNull(PrintCode.parse("SDBE"))
        assertNull(PrintCode.parse("Dragon Blanc"))
        assertNull(PrintCode.parse("SDBE-FR001 et autre chose"))
    }

    // Tout l'intérêt du scan : on photographie une carte française et on retrouve
    // l'impression anglaise du catalogue.
    @Test
    fun `une impression correspond quelle que soit la langue`() {
        val scanned = PrintCode.parse("SDBE-FR001")!!
        assertTrue(scanned.matches("SDBE-EN001"))
        assertTrue(scanned.matches("SDBE-DE001"))
        assertFalse(scanned.matches("SDBE-EN002"))
        assertFalse(scanned.matches("LOB-EN001"))
    }
}

class PrintCodeReaderTest {
    @Test
    fun `trouve un code au milieu d'un texte lu`() {
        assertEquals(listOf("SDBE-FR001"), PrintCodeReader.codes("ULTRA RARE SDBE-FR001 1st"))
    }

    // L'OCR confond ces trois lettres avec des chiffres, et sur un numéro de carte elles
    // n'ont aucune raison d'exister.
    @Test
    fun `rattrape les O, I et L lus à la place de chiffres`() {
        assertEquals(listOf("LOB-EN001"), PrintCodeReader.codes("LOB-ENOO1"))
        assertEquals(listOf("MP22-FR111"), PrintCodeReader.codes("MP22-FRILI"))
    }

    @Test
    fun `ignore les espaces glissés par la caméra`() {
        assertEquals(listOf("SDBE-FR001"), PrintCodeReader.codes("SDBE - FR001"))
    }

    @Test
    fun `rend plusieurs codes quand deux cartes sont dans le viseur`() {
        assertEquals(
            listOf("SDBE-FR001", "LOB-EN001"),
            PrintCodeReader.codes("SDBE-FR001\nLOB-EN001"),
        )
    }

    @Test
    fun `ne rend rien sur un texte sans code`() {
        assertEquals(emptyList(), PrintCodeReader.codes("Dragon Blanc aux Yeux Bleus"))
    }
}

class CardmarketTest {
    @Test
    fun `cherche sur le nom anglais`() {
        assertEquals(
            "https://www.cardmarket.com/en/YuGiOh/Products/Search?searchString=Blue-Eyes%20White%20Dragon",
            Cardmarket.searchUrl("Blue-Eyes White Dragon"),
        )
    }

    @Test
    fun `échappe ce qui casserait l'URL`() {
        assertTrue(
            Cardmarket.searchUrl("Harpie & Pet Dragon")
                .contains("searchString=Harpie%20%26%20Pet%20Dragon")
        )
    }

    @Test
    fun `traduit langue et état en identifiants Cardmarket`() {
        val url = Cardmarket.searchUrl(
            "Dark Magician",
            Cardmarket.Preferences(CardLanguage.FR, CardCondition.NEAR_MINT),
        )
        assertTrue(url.contains("language=2"))
        assertTrue(url.contains("minCondition=2"))
    }

    @Test
    fun `numérote les états du meilleur au pire`() {
        fun idOf(c: CardCondition) =
            Cardmarket.searchUrl("X", Cardmarket.Preferences(minCondition = c))
                .substringAfter("minCondition=")
        assertEquals("1", idOf(CardCondition.MINT))
        assertEquals("3", idOf(CardCondition.EXCELLENT))
        assertEquals("7", idOf(CardCondition.POOR))
    }

    // Mieux vaut une fiche non filtrée qu'un filtre inventé.
    @Test
    fun `omet le filtre pour une langue sans identifiant connu`() {
        val url = Cardmarket.searchUrl("Dark Magician", Cardmarket.Preferences(CardLanguage.JP))
        assertFalse(url.contains("language="))
    }

    @Test
    fun `suit la langue d'interface quand Cardmarket la parle`() {
        assertTrue(Cardmarket.searchUrl("X", locale = "fr").contains("/fr/YuGiOh/"))
        assertTrue(Cardmarket.searchUrl("X", locale = "pt").contains("/en/YuGiOh/"))
    }

    @Test
    fun `liste à coller : une ligne par carte, quantité en tête`() {
        assertEquals(
            "3 Dark Magician\n1 Pot of Greed",
            Cardmarket.wantsListText(
                listOf(Cardmarket.Wanted("Pot of Greed", 1), Cardmarket.Wanted("Dark Magician", 3))
            ),
        )
    }

    @Test
    fun `cumule une carte demandée deux fois et ignore ce qui ne manque pas`() {
        assertEquals(
            "3 Called by the Grave",
            Cardmarket.wantsListText(
                listOf(
                    Cardmarket.Wanted("Called by the Grave", 2),
                    Cardmarket.Wanted("Called by the Grave", 1),
                    Cardmarket.Wanted("Effect Veiler", 0),
                )
            ),
        )
    }

    @Test
    fun `préfère l'anglais quand la carte est affichée traduite`() {
        assertEquals(
            "Blue-Eyes White Dragon",
            marketName("Dragon Blanc aux Yeux Bleus", "Blue-Eyes White Dragon"),
        )
        assertEquals("Pot of Greed", marketName("Pot of Greed", null))
    }
}

class DeckRulesTest {
    @Test
    fun `normalise les libellés de banlist des deux sources`() {
        assertEquals(BanStatus.FORBIDDEN, BanStatus.of("Banned"))
        assertEquals(BanStatus.FORBIDDEN, BanStatus.of("Forbidden"))
        assertEquals(BanStatus.SEMI_LIMITED, BanStatus.of("Semi-Limited"))
        assertEquals(BanStatus.SEMI_LIMITED, BanStatus.of("Semi  Limited"))
        assertNull(BanStatus.of("Unlimited"))
        assertNull(BanStatus.of(null))
    }

    @Test
    fun `plafonne les exemplaires selon la banlist`() {
        assertEquals(0, DeckRules.maxCopies("Forbidden"))
        assertEquals(1, DeckRules.maxCopies("Limited"))
        assertEquals(2, DeckRules.maxCopies("Semi-Limited"))
        assertEquals(3, DeckRules.maxCopies(null))
    }
}
