package com.ygochecker.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CardRoleClassifierTest {
    private fun monster(atk: Int? = 1000, def: Int? = 1000, type: String = "Effect Monster") =
        Card(id = 1, name = "Test", type = type, attack = atk, defense = def)

    @Test
    fun negates_isInterrupt() {
        val roles = CardRoleClassifier.classify(monster(), listOf("negates"))
        assertTrue(HatCardRole.INTERRUPT in roles)
    }

    @Test
    fun searchesDeck_onMainDeckMonster_isSearcherAndStarter() {
        val roles = CardRoleClassifier.classify(monster(), listOf("searches_deck"))
        assertTrue(HatCardRole.SEARCHER in roles)
        assertTrue(HatCardRole.ENGINE_STARTER in roles)
    }

    @Test
    fun specialSummonFromGy_isExtender() {
        val roles = CardRoleClassifier.classify(monster(), listOf("special_summons", "ss_from_gy"))
        assertTrue(HatCardRole.EXTENDER in roles)
    }

    @Test
    fun selfRevive_isRecycle() {
        val roles = CardRoleClassifier.classify(monster(), listOf("revives_from_gy"))
        assertTrue(HatCardRole.RECYCLE in roles)
    }

    @Test
    fun lowAtkWithGyEffect_isBait() {
        val roles = CardRoleClassifier.classify(monster(atk = 1000), listOf("gy_effect"))
        assertTrue(HatCardRole.BAIT in roles)
    }

    @Test
    fun highAtkWithGyEffect_isNotBait() {
        val roles = CardRoleClassifier.classify(monster(atk = 2500), listOf("gy_effect"))
        assertTrue(HatCardRole.BAIT !in roles)
    }

    @Test
    fun trapType_isTrapLine() {
        val roles = CardRoleClassifier.classify(monster(type = "Normal Trap"), emptyList())
        assertEquals(setOf(HatCardRole.TRAP_LINE), roles)
    }

    @Test
    fun extraDeckMonster_neverEngineStarterOrBait() {
        val roles = CardRoleClassifier.classify(
            monster(atk = 1000, type = "XYZ Monster"),
            listOf("searches_deck", "gy_effect"),
        )
        assertTrue(HatCardRole.ENGINE_STARTER !in roles)
        assertTrue(HatCardRole.BAIT !in roles)
        assertTrue(HatCardRole.SEARCHER in roles)
    }

    @Test
    fun unclearCard_getsNoRole() {
        val roles = CardRoleClassifier.classify(monster(atk = 2500, def = 2000), emptyList())
        assertTrue(roles.isEmpty())
    }
}
