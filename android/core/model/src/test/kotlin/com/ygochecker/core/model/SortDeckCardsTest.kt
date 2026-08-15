package com.ygochecker.core.model

import org.junit.Assert.assertEquals
import org.junit.Test

class SortDeckCardsTest {
    @Test
    fun `main is monster spell trap then passcode`() {
        val trap = card(3, "Trap Hole", "Normal Trap")
        val spellHi = card(200, "Pot", "Normal Spell")
        val spellLo = card(100, "Raigeki", "Normal Spell")
        val monsterHi = card(50, "Blue-Eyes", "Normal Monster")
        val monsterLo = card(10, "Dark Magician", "Normal Monster")
        val sorted = sortDeckCards(
            listOf(
                DeckCard(trap, 1, DeckSection.MAIN),
                DeckCard(spellHi, 1, DeckSection.MAIN),
                DeckCard(spellLo, 1, DeckSection.MAIN),
                DeckCard(monsterHi, 1, DeckSection.MAIN),
                DeckCard(monsterLo, 1, DeckSection.MAIN),
            ),
        )
        assertEquals(listOf(10, 50, 100, 200, 3), sorted.map { it.card.id })
    }

    @Test
    fun `extra is passcode only`() {
        val a = card(999, "Cyz", "XYZ Monster")
        val b = card(111, "Link", "Link Monster")
        val sorted = sortDeckCards(
            listOf(
                DeckCard(a, 1, DeckSection.EXTRA),
                DeckCard(b, 1, DeckSection.EXTRA),
            ),
        )
        assertEquals(listOf(111, 999), sorted.map { it.card.id })
    }

    @Test
    fun `side matches main type then passcode`() {
        val spell = card(5, "Spell", "Quick-Play Spell")
        val monster = card(9, "Monster", "Effect Monster")
        val link = card(2, "Link", "Link Monster")
        val sorted = sortDeckCards(
            listOf(
                DeckCard(spell, 1, DeckSection.SIDE),
                DeckCard(monster, 1, DeckSection.SIDE),
                DeckCard(link, 1, DeckSection.SIDE),
            ),
        )
        assertEquals(listOf(2, 9, 5), sorted.map { it.card.id })
    }

    private fun card(id: Int, name: String, type: String) = Card(id, name, type)
}
