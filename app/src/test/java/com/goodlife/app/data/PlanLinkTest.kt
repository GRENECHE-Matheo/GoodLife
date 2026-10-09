package com.goodlife.app.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlanLinkTest {
    @Test fun memePlatAvecDesMotsDifferents() {
        assertTrue(PlanLink.similar("Pâtes carbonara", "1× Assiette de pâtes à la carbonara"))
        assertTrue(PlanLink.similar("Salade de tomates", "Tomate, mozzarella"))
        assertTrue(PlanLink.similar("Poulet rôti et riz", "2× Cuisses de poulet"))
    }

    @Test fun platsDifferents() {
        assertFalse(PlanLink.similar("Pâtes carbonara", "Salade niçoise"))
        assertFalse(PlanLink.similar("Assiette de riz", "Assiette de légumes"))   // « assiette » ne compte pas
        assertFalse(PlanLink.similar("", "Pizza"))
    }
}
