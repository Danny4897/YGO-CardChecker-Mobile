package com.ygochecker.data.deck

import com.ygochecker.core.common.errorOrNull
import com.ygochecker.core.common.isOk
import com.ygochecker.core.common.valueOrNull
import com.ygochecker.core.model.Card
import com.ygochecker.core.model.DeckCard
import com.ygochecker.core.model.DeckSection
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DeckCodecsTest {
    private val cards = listOf(
        DeckCard(Card(89631139, "Blue-Eyes White Dragon", "Normal Monster"), 2, DeckSection.MAIN),
        DeckCard(Card(23995346, "Elemental HERO Flame Wingman", "Fusion Monster"), 1, DeckSection.EXTRA),
        DeckCard(Card(55144522, "Pot of Greed", "Normal Spell"), 1, DeckSection.SIDE),
    )

    @Test fun `YDK round trip preserves passcodes and sections`() {
        val encoded = YdkCodec.export(cards)
        val decoded = YdkCodec.import(encoded)
        assertTrue(decoded.isOk)
        assertEquals(
            cards.map { Triple(it.card.id, it.quantity, it.section) }.sortedBy { it.first },
            decoded.valueOrNull!!.map { Triple(it.card.id, it.quantity, it.section) }.sortedBy { it.first },
        )
        assertTrue(encoded.contains("#main"))
        assertTrue(encoded.contains("!side"))
    }

    @Test fun `YDKE export uses padded base64 like web and ydke npm`() {
        // ydke npm: main [46986414, 44095762], extra [1861629], side []
        val cards = listOf(
            DeckCard(Card(46986414, "Dark Magician", "Normal Monster"), 1, DeckSection.MAIN),
            DeckCard(Card(44095762, "Dark Magician Girl", "Effect Monster"), 1, DeckSection.MAIN),
            DeckCard(Card(1861629, "Decode Talker", "Link Monster"), 1, DeckSection.EXTRA),
        )
        assertEquals("ydke://rvTMAhLZoAI=!/WccAA==!!", YdkeCodec.export(cards))
    }

    @Test fun `YDKE round trip preserves passcodes and sections`() {
        val encoded = YdkeCodec.export(cards)
        val decoded = YdkeCodec.import(encoded)
        assertTrue(decoded.isOk)
        assertEquals(
            cards.map { Triple(it.card.id, it.quantity, it.section) },
            decoded.valueOrNull!!.map { Triple(it.card.id, it.quantity, it.section) },
        )
    }

    @Test fun `YDKE normalizes clipboard paste and URL-safe base64`() = runBlocking {
        val encoded = YdkeCodec.export(cards)
        val messy = "  \"$encoded\"  "
        val decoded = YdkeCodec.import(messy)
        assertTrue(decoded.isOk)
        assertEquals(3, decoded.valueOrNull!!.size)
    }

    @Test fun `text codec round trip preserves named sections`() = runBlocking {
        val encoded = DeckTextCodec.export(cards)
        val decoded = DeckTextCodec.import(encoded) { name -> cards.firstOrNull { it.card.name == name }?.card }
        assertTrue(decoded.isOk)
        assertEquals(cards, decoded.valueOrNull)
    }

    @Test fun `text codec reports unresolved names`() = runBlocking {
        val result = DeckTextCodec.import("#main\n1 Missing Card") { null }
        assertEquals("deck.import.card_not_found", result.errorOrNull?.errorKey)
    }

    @Test fun `YDKE resolution replaces passcodes with catalog cards`() {
        val encoded = YdkeCodec.export(cards)
        val decoded = YdkeCodec.import(encoded).valueOrNull!!
        val resolved = YdkeCodec.resolve(decoded, cards.map(DeckCard::card))
        assertTrue(resolved.isOk)
        assertEquals(cards, resolved.valueOrNull)
    }

    @Test fun `YDKE resolution reports every unknown passcode`() {
        val decoded = listOf(
            DeckCard(Card(123, "123", "Unknown"), 1, DeckSection.MAIN),
            DeckCard(Card(456, "456", "Unknown"), 2, DeckSection.SIDE),
        )
        val result = YdkeCodec.resolve(decoded, emptyList())
        assertEquals("deck.import.ydke_card_not_found", result.errorOrNull?.errorKey)
        assertEquals("123, 456", result.errorOrNull?.detail)
    }

    @Test fun `text import accepts web quantity and section aliases`() = runBlocking {
        val result = DeckTextCodec.import(
            "Main Deck:\n2x Blue-Eyes White Dragon\n#!extra\n1 Elemental HERO Flame Wingman",
        ) { name -> cards.firstOrNull { it.card.name == name }?.card }
        assertTrue(result.isOk)
        assertEquals(
            listOf(
                DeckCard(cards[0].card, 2, DeckSection.MAIN),
                DeckCard(cards[1].card, 1, DeckSection.EXTRA),
            ),
            result.valueOrNull,
        )
    }

    @Test fun `persistence normalization aggregates duplicate card rows`() {
        val duplicates = listOf(
            DeckCard(cards[0].card, 1, DeckSection.MAIN),
            DeckCard(cards[0].card, 2, DeckSection.MAIN),
        )
        val result = normalizeImportedCards(duplicates)
        assertEquals(listOf(DeckCard(cards[0].card, 3, DeckSection.MAIN)), result.valueOrNull)
    }

    @Test fun `persistence normalization rejects more than three copies`() {
        val duplicates = listOf(
            DeckCard(cards[0].card, 2, DeckSection.MAIN),
            DeckCard(cards[0].card, 2, DeckSection.MAIN),
        )
        val result = normalizeImportedCards(duplicates)
        assertEquals("deck.import.quantity_invalid", result.errorOrNull?.errorKey)
        assertEquals(cards[0].card.name, result.errorOrNull?.detail)
    }

    @Test fun `text import auto-routes Fusion to Extra without markers`() = runBlocking {
        val result = DeckTextCodec.import("1 Elemental HERO Flame Wingman") { name ->
            cards.firstOrNull { it.card.name == name }?.card
        }
        assertTrue(result.isOk)
        assertEquals(
            listOf(DeckCard(cards[1].card, 1, DeckSection.EXTRA)),
            result.valueOrNull,
        )
    }
}
