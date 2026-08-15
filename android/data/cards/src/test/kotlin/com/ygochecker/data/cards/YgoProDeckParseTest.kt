package com.ygochecker.data.cards

import org.junit.Assert.assertEquals
import org.junit.Test

class YgoProDeckParseTest {
    @Test
    fun `parses cardinfo payload like the web API`() {
        val json = """
            {"data":[{"id":46986414,"name":"Dark Magician","type":"Normal Monster","race":"Spellcaster",
            "attribute":"DARK","atk":2500,"def":2100,"level":7,"desc":"The ultimate wizard."}]}
        """.trimIndent()
        val cards = parseCardInfo(json)
        assertEquals(1, cards.size)
        assertEquals(46986414, cards[0].id)
        assertEquals("Dark Magician", cards[0].name)
        assertEquals(2500, cards[0].attack)
    }
}
