package com.goodlife.app.dex

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NutridexTest {
    @Test fun ingredientsPrincipaux() {
        val ids = Nutridex.fromProduct("Taboulé oriental", "Semoule de blé dur 45%, tomates 15%, oignons, menthe, huile d'olive, jus de citron")
        assertEquals(4, ids.size)
        assertTrue(ids.containsAll(listOf("taboule", "semoule", "tomate")))
    }

    @Test fun nomLePlusLongDAbord() {
        val ids = Nutridex.fromProduct("Purée", "Pommes de terre 90%, lait, sel")
        assertTrue("pomme-de-terre" in ids)
        assertFalse("pomme" in ids)
    }

    @Test fun pasDeTracesNiDeFauxAmis() {
        val ids = Nutridex.fromProduct("Pâte à tartiner", "Sucre, huile de palme, pâte de cacao, lait écrémé en poudre. Peut contenir des traces de noisettes et d'amandes.")
        assertFalse("pates" in ids)
        assertFalse("noisettes" in ids)
        assertFalse("amandes" in ids)
    }

    @Test fun auPlusQuatre() {
        assertTrue(Nutridex.fromProduct("Salade", "tomate, concombre, poivron, oignon, maïs, carotte").size <= 4)
        assertTrue(Nutridex.fromProduct("", "").isEmpty())
    }
}
