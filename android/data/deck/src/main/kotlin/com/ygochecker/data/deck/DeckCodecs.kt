package com.ygochecker.data.deck

import com.ygochecker.core.common.AppError
import com.ygochecker.core.common.AppResult
import com.ygochecker.core.model.Card
import com.ygochecker.core.model.DeckCard
import com.ygochecker.core.model.DeckSection
import com.ygochecker.core.model.resolveImportedSection
import com.ygochecker.core.model.sortDeckCards
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Base64

fun normalizeImportedCards(cards: List<DeckCard>): AppResult<List<DeckCard>> {
    val grouped = cards.groupBy { it.card.id to it.section }
    val normalized = grouped.values.map { entries ->
        val quantity = entries.sumOf(DeckCard::quantity)
        if (quantity > 3) {
            return AppResult.Err(AppError("deck.import.quantity_invalid", entries.first().card.name))
        }
        entries.first().copy(quantity = quantity)
    }
    return AppResult.Ok(sortDeckCards(normalized))
}

object YdkeCodec {
    private const val PREFIX = "ydke://"
    private val extractStrict = Regex(
        """ydke://[A-Za-z0-9+/=_-]*?![A-Za-z0-9+/=_-]*?![A-Za-z0-9+/=_-]*?!""",
        RegexOption.IGNORE_CASE,
    )
    private val extractLoose = Regex("""ydke://[!A-Za-z0-9+/=_-]*""", RegexOption.IGNORE_CASE)

    fun export(cards: List<DeckCard>): String {
        // Preserve deck order within each section (web / MDPRO-friendly), not sortDeckCards.
        return PREFIX + DeckSection.entries.joinToString("!") { section ->
            val passcodes = cards.filter { it.section == section }
                .flatMap { item -> List(item.quantity) { item.card.id } }
            encodePasscodes(passcodes)
        } + "!"
    }
    fun import(uri: String): AppResult<List<DeckCard>> {
        return try {
            val normalized = normalizeInput(uri)
            if (!normalized.startsWith(PREFIX, ignoreCase = true)) {
                return AppResult.Err(AppError("deck.import.ydke_invalid"))
            }
            val parts = normalized.substring(PREFIX.length).split("!").toMutableList()
            while (parts.size < 3) parts += ""
            val cards = DeckSection.entries.flatMapIndexed { index, section ->
                decodePasscodes(parts[index]).groupingBy { it }.eachCount().map { (id, quantity) ->
                    DeckCard(Card(id, id.toString(), "Unknown"), quantity, section)
                }
            }
            AppResult.Ok(cards)
        } catch (_: IllegalArgumentException) {
            AppResult.Err(AppError("deck.import.ydke_invalid"))
        }
    }

    fun resolve(decoded: List<DeckCard>, catalog: List<Card>): AppResult<List<DeckCard>> {
        val byId = catalog.associateBy(Card::id)
        val unknownIds = decoded.map { it.card.id }.filterNot(byId::containsKey).distinct().sorted()
        if (unknownIds.isNotEmpty()) {
            return AppResult.Err(AppError("deck.import.ydke_card_not_found", unknownIds.joinToString()))
        }
        return AppResult.Ok(decoded.map { it.copy(card = byId.getValue(it.card.id)) })
    }

    /** Mirrors web `normalizeYdkeInput` / clipboard sanitization. */
    fun normalizeInput(input: String): String {
        var text = sanitizeClipboard(input)
        extractUrl(text)?.let { return it }
        if (text.contains('!') && !text.contains(PREFIX, ignoreCase = true)) {
            return PREFIX + text
        }
        return text
    }

    fun extractUrl(text: String): String? {
        val normalized = sanitizeClipboard(text)
        extractStrict.find(normalized)?.value?.let { return it }
        return extractLoose.find(normalized)?.value
    }

    private fun sanitizeClipboard(input: String): String {
        var text = input.trim().removePrefix("\uFEFF")
        if ('%' in text) {
            runCatching { text = java.net.URLDecoder.decode(text, Charsets.UTF_8) }
        }
        return text.replace(Regex("\\s+"), "")
            .replace(Regex("""^[\s"'\[<(]+"""), "")
            .replace(Regex("""[\s"'\]>).,;]+$"""), "")
    }

    private fun encodePasscodes(passcodes: List<Int>): String {
        if (passcodes.isEmpty()) return ""
        val bytes = ByteBuffer.allocate(passcodes.size * 4).order(ByteOrder.LITTLE_ENDIAN)
        passcodes.forEach { bytes.putInt(it) }
        // Padded Base64 matches web `btoa` / ydke npm — MDPRO3 rejects unpadded segments.
        return Base64.getEncoder().encodeToString(bytes.array())
    }

    private fun decodePasscodes(segment: String): List<Int> {
        val normalized = normalizeBase64Segment(segment)
        if (normalized.isEmpty()) return emptyList()
        val bytes = Base64.getDecoder().decode(normalized)
        val usable = bytes.size - (bytes.size % 4)
        if (usable == 0) return emptyList()
        val buffer = ByteBuffer.wrap(bytes, 0, usable).order(ByteOrder.LITTLE_ENDIAN)
        return buildList(usable / 4) {
            while (buffer.remaining() >= 4) add(buffer.int)
        }
    }

    private fun normalizeBase64Segment(segment: String): String {
        val cleaned = segment.trim().replace('-', '+').replace('_', '/')
        if (cleaned.isEmpty()) return ""
        val pad = cleaned.length % 4
        return if (pad == 0) cleaned else cleaned + "=".repeat(4 - pad)
    }
}

object DeckTextCodec {
    fun export(cards: List<DeckCard>): String {
        val sorted = sortDeckCards(cards)
        return DeckSection.entries.joinToString("\n") { section ->
            "#${section.name.lowercase()}\n" + sorted.filter { it.section == section }
                .joinToString("\n") { "${it.quantity} ${it.card.name}" }
        }.trim()
    }

    suspend fun import(text: String, resolve: suspend (String) -> Card?): AppResult<List<DeckCard>> {
        var section = DeckSection.MAIN
        val cards = mutableListOf<DeckCard>()
        text.lineSequence().map(String::trim).filter(String::isNotBlank).forEach { line ->
            if (line.startsWith("#") || line.endsWith("Deck:", ignoreCase = true)) {
                section = when (line.lowercase().replace("!", "")) {
                    "#main", "main deck:" -> DeckSection.MAIN
                    "#extra", "extra deck:" -> DeckSection.EXTRA
                    "#side", "side deck:" -> DeckSection.SIDE
                    else -> return AppResult.Err(AppError("deck.import.section_invalid", line))
                }
            } else {
                val match = Regex("^(\\d+)\\s*[x×]?\\s+(.+)$", RegexOption.IGNORE_CASE).matchEntire(line)
                    ?: return AppResult.Err(AppError("deck.import.line_invalid", line))
                val quantity = match.groupValues[1].toIntOrNull()?.takeIf { it in 1..3 }
                    ?: return AppResult.Err(AppError("deck.import.quantity_invalid", line))
                val card = resolve(match.groupValues[2])
                    ?: return AppResult.Err(AppError("deck.import.card_not_found", match.groupValues[2]))
                val target = resolveImportedSection(section, card.type)
                cards += DeckCard(card, quantity, target)
            }
        }
        return AppResult.Ok(cards)
    }
}

/** Classic YGOPRO / EDOpro `.ydk` (section headers + bare passcodes). */
object YdkCodec {
    fun export(cards: List<DeckCard>): String {
        val sorted = sortDeckCards(cards)
        fun lines(section: DeckSection) = sorted
            .filter { it.section == section }
            .flatMap { item -> List(item.quantity) { item.card.id.toString() } }
        return buildString {
            appendLine("#created by YGOChecker")
            appendLine("#main")
            lines(DeckSection.MAIN).forEach { appendLine(it) }
            appendLine("#extra")
            lines(DeckSection.EXTRA).forEach { appendLine(it) }
            appendLine("!side")
            lines(DeckSection.SIDE).forEach { appendLine(it) }
        }.trimEnd() + "\n"
    }

    fun import(text: String): AppResult<List<DeckCard>> {
        var section = DeckSection.MAIN
        val cards = mutableListOf<DeckCard>()
        text.lineSequence().map(String::trim).filter { it.isNotEmpty() }.forEach { line ->
            val lower = line.lowercase()
            when {
                lower.startsWith("#created") || lower.startsWith("#generated") -> Unit
                lower == "#main" || lower == "#main deck" -> section = DeckSection.MAIN
                lower == "#extra" || lower == "#extra deck" -> section = DeckSection.EXTRA
                lower == "!side" || lower == "#side" || lower == "#side deck" -> section = DeckSection.SIDE
                line.startsWith("#") || line.startsWith("!") -> Unit
                else -> {
                    val id = line.toIntOrNull()?.takeIf { it > 0 }
                        ?: return AppResult.Err(AppError("deck.import.line_invalid", line))
                    cards += DeckCard(Card(id, id.toString(), "Unknown"), 1, section)
                }
            }
        }
        if (cards.isEmpty()) return AppResult.Err(AppError("deck.import.ydke_invalid"))
        return normalizeImportedCards(cards)
    }
}
