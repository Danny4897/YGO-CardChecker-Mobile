package com.ygochecker.feature.overlay

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Mirrors `ygo-card-checker/src/app/utils/mdpro-ocr-parse.spec.ts`. */
class MdproOcrParseTest {
    private val blueEyesSample = """
        Blue-Eyes White Dragon
        8
        ATK 3000
        DEF 2500
        OCG TCG G:0
        [Dragon/Normal] [Blue-Eyes] [89631151/89631139]
        This legendary dragon is a powerful engine of destruction.
        Save Image
    """.trimIndent()

    @Test
    fun `extracts passcodes and candidate name from MDPRO detail OCR`() {
        val result = MdproOcrParse.parse(blueEyesSample)
        assertEquals(listOf(89631151, 89631139), result.passcodes)
        assertEquals("Blue-Eyes White Dragon", result.candidateName)
        assertEquals("id:89631151", MdproOcrParse.identityKey(result))
    }

    @Test
    fun `returns empty for blank input`() {
        val result = MdproOcrParse.parse("   \n  ")
        assertEquals(emptyList<Int>(), result.passcodes)
        assertNull(result.candidateName)
        assertEquals("", MdproOcrParse.identityKey(result))
    }

    @Test
    fun `falls back to name when no passcode`() {
        val result = MdproOcrParse.parse("Ash Blossom & Joyous Spring\nATK 0\nDEF 1800")
        assertEquals(emptyList<Int>(), result.passcodes)
        assertEquals("Ash Blossom & Joyous Spring", result.candidateName)
        assertEquals("name:ashblossomjoyousspring", MdproOcrParse.identityKey(result))
    }

    @Test
    fun `skips spell trap type labels and reads the real title`() {
        val result = MdproOcrParse.parse(
            """
            Spell Card
            Normal Spell
            Pot of Prosperity
            [Spell Card]
            [84211599]
            """.trimIndent(),
        )
        assertEquals("Pot of Prosperity", result.candidateName)
        assertEquals(listOf(84211599), result.passcodes)
    }

    @Test
    fun `prefers bracket passcode over truncated title OCR`() {
        val result = MdproOcrParse.parse(
            """
            ost Sleeper, the Underworld Princess
            [Zombie/Effect] [99370594]
            If this card is sent to the GY
            """.trimIndent(),
        )
        assertEquals(listOf(99370594), result.passcodes)
        assertEquals("id:99370594", MdproOcrParse.identityKey(result))
    }

    @Test
    fun `matches known card by passcode without re-fetch`() {
        val result = MdproOcrParse.parse(blueEyesSample)
        assertTrue(MdproOcrParse.matchesKnownCard(result, 89631151, "Blue-Eyes White Dragon"))
        assertFalse(MdproOcrParse.matchesKnownCard(result, 99999999, "Other"))
    }
}
