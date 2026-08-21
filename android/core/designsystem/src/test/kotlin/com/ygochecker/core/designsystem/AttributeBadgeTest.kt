package com.ygochecker.core.designsystem

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AttributeBadgeTest {
    @Test
    fun `known attributes resolve to their kanji`() {
        assertEquals("闇", attributeSpecOrNull("DARK")!!.kanji)
        assertEquals("光", attributeSpecOrNull("LIGHT")!!.kanji)
        assertEquals("水", attributeSpecOrNull("WATER")!!.kanji)
        assertEquals("炎", attributeSpecOrNull("FIRE")!!.kanji)
        assertEquals("地", attributeSpecOrNull("EARTH")!!.kanji)
        assertEquals("風", attributeSpecOrNull("WIND")!!.kanji)
        assertEquals("神", attributeSpecOrNull("DIVINE")!!.kanji)
    }

    @Test
    fun `lookup is case-insensitive because the API is not always uppercase`() {
        assertEquals("闇", attributeSpecOrNull("dark")!!.kanji)
    }

    @Test
    fun `null or unknown attribute returns null so callers can skip rendering`() {
        assertNull(attributeSpecOrNull(null))
        assertNull(attributeSpecOrNull("NOT_AN_ATTRIBUTE"))
    }
}
