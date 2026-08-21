package com.ygochecker.core.designsystem

import org.junit.Assert.assertEquals
import org.junit.Test

class CardTypeBadgeTest {
    @Test
    fun `normal and effect monsters map to MONSTER`() {
        assertEquals(CardTypeBadgeKind.MONSTER, cardTypeBadgeKindOf("Normal Monster"))
        assertEquals(CardTypeBadgeKind.MONSTER, cardTypeBadgeKindOf("Effect Monster"))
        assertEquals(CardTypeBadgeKind.MONSTER, cardTypeBadgeKindOf("Ritual Monster"))
    }

    @Test
    fun `spell and trap cards map directly`() {
        assertEquals(CardTypeBadgeKind.SPELL, cardTypeBadgeKindOf("Spell Card"))
        assertEquals(CardTypeBadgeKind.TRAP, cardTypeBadgeKindOf("Trap Card"))
    }

    @Test
    fun `extra deck monster types map to EXTRA via isExtraDeckType`() {
        assertEquals(CardTypeBadgeKind.EXTRA, cardTypeBadgeKindOf("Fusion Monster"))
        assertEquals(CardTypeBadgeKind.EXTRA, cardTypeBadgeKindOf("Synchro Monster"))
        assertEquals(CardTypeBadgeKind.EXTRA, cardTypeBadgeKindOf("XYZ Monster"))
        assertEquals(CardTypeBadgeKind.EXTRA, cardTypeBadgeKindOf("Link Monster"))
    }
}
