package com.ygochecker.data.cards

import com.ygochecker.core.model.Card
import com.ygochecker.core.model.EffectScriptSummary
import com.ygochecker.core.model.EffectTextProfiler
import com.ygochecker.core.model.RelatedCardRef

/**
 * Effect / engine synergy bridges beyond static related.json edges.
 * Always applies printed-text profiling (races, packages, archetypes) for every card.
 */
internal object SynergyEnrichment {
    private val HIGH_VALUE_TAGS = listOf(
        "mills", "sends_to_gy", "hand_to_gy", "gy_effect", "banishes",
        "ss_from_gy", "revives_from_gy", "searches_deck", "ss_from_deck",
        "ss_from_hand", "discards", "draw",
    )

    suspend fun enrich(
        sourceId: Int,
        source: Card?,
        script: EffectScriptSummary?,
        dao: CardDao,
        limit: Int,
    ): List<RelatedCardRef> {
        val text = source?.let(EffectTextProfiler::profile)
        val tags = (
            script?.tags.orEmpty().map { it.lowercase() } +
                text?.tags.orEmpty().map { it.lowercase() }
            ).toSet()
        val archetypes = text?.archetypes.orEmpty()
        val races = text?.mentionedRaces.orEmpty()
        val out = linkedMapOf<Int, RelatedCardRef>()

        // 1) Shared mechanic tags (effect synergy).
        for (tag in HIGH_VALUE_TAGS) {
            if (tag !in tags) continue
            dao.cardsWithEffectTag(tag, sourceId, 8).forEach { row ->
                val score = 1.05 + if (tag in setOf("mills", "banishes", "gy_effect", "ss_from_gy")) 0.2 else 0.0
                merge(out, row.id, row.name, "effect_synergy", score)
            }
        }

        // 2) Named engine bridges from context.
        for ((relation, needle, bonus) in engineNeedles(source, tags, archetypes, races, text)) {
            dao.cardsMatchingNeedle(needle, sourceId, 10).forEach { row ->
                merge(out, row.id, row.name, relation, bonus)
            }
        }

        // 3) Package partners from printed text / known pairs.
        text?.packagePartners.orEmpty().forEach { partner ->
            dao.cardsMatchingNeedle(partner, sourceId, 6).forEach { row ->
                merge(out, row.id, row.name, "package_pair", 2.5)
            }
        }

        // 4) Race bridges from card race + races mentioned in effect text (Zombie World → Zombies).
        for (race in races) {
            val raceBonus = if (text?.isTypeChangeField == true) 2.1 else 1.45
            dao.cardsWithRace(race, sourceId, 14).forEach { row ->
                merge(out, row.id, row.name, "race_synergy", raceBonus)
            }
            // Support cards that name the race in text (Paladin of the Cursed Dragon, Book of Life…).
            dao.cardsMatchingNeedle(race, sourceId, 12).forEach { row ->
                merge(out, row.id, row.name, "race_mention", raceBonus - 0.15)
            }
        }

        // 5) Tuner ↔ Synchro bridge.
        if (text?.isTuner == true || source?.type?.contains("Tuner", ignoreCase = true) == true) {
            dao.cardsMatchingNeedle("Synchro Monster", sourceId, 6).forEach { row ->
                merge(out, row.id, row.name, "tuner_synchro", 1.7)
            }
        }
        if (text?.isSynchroMonster == true || text?.requiresTuner == true) {
            dao.cardsMatchingNeedle("Tuner", sourceId, 8).forEach { row ->
                merge(out, row.id, row.name, "synchro_tuner", 1.85)
            }
        }

        // 6) Same attribute + race peers.
        val attr = source?.attribute?.trim().orEmpty()
        val race = source?.race?.trim().orEmpty()
        if (attr.isNotEmpty() && race.isNotEmpty()) {
            dao.cardsWithAttributeRace(attr, race, sourceId, 6).forEach { row ->
                merge(out, row.id, row.name, "type_peer", 1.0)
            }
        }

        // 7) Archetype name peers.
        for (token in archetypes) {
            val needle = when (token) {
                "hero" -> "HERO"
                "dragon_ruler" -> "Dragon Ruler"
                "lightsworn" -> "Lightsworn"
                "zombie" -> "Zombie"
                else -> token.replace('_', ' ')
            }
            dao.cardsMatchingNeedle(needle, sourceId, 10).forEach { row ->
                merge(out, row.id, row.name, "archetype", 1.65)
            }
        }

        return out.values.sortedByDescending { it.score }.take(limit.coerceAtLeast(24))
    }

    private fun engineNeedles(
        source: Card?,
        tags: Set<String>,
        archetypes: Set<String>,
        races: Set<String>,
        text: EffectTextProfiler.Profile?,
    ): List<Triple<String, String, Double>> {
        if (source == null) return emptyList()
        val attr = source.attribute?.uppercase().orEmpty()
        val race = source.race?.uppercase().orEmpty()
        val type = source.type.uppercase()
        val name = source.name
        val gyish = tags.any { it.contains("gy") || it == "mills" || it == "sends_to_gy" || it == "banishes" }
        val out = mutableListOf<Triple<String, String, Double>>()

        if (gyish && (race.contains("DRAGON") || type.contains("DRAGON"))) {
            out += Triple("mill_synergy", "Lightsworn", 1.55)
            out += Triple("banish_synergy", "Dragon Ruler", 1.55)
            out += Triple("banish_synergy", "Chaos", 1.25)
        }
        if (gyish && attr == "LIGHT") {
            out += Triple("mill_synergy", "Lightsworn", 1.45)
            out += Triple("banish_synergy", "Chaos", 1.3)
        }
        if ("hero" in archetypes || name.contains("HERO", ignoreCase = true)) {
            out += Triple("engine_synergy", "Elemental HERO", 1.55)
            out += Triple("engine_synergy", "HERO", 1.4)
            out += Triple("engine_synergy", "Polymerization", 1.5)
            out += Triple("engine_synergy", "Fusion", 1.2)
        }
        if ("lightsworn" in archetypes) {
            out += Triple("mill_synergy", "Lightsworn", 1.6)
            out += Triple("mill_synergy", "Judgment Dragon", 1.7)
        }
        // Zombie engine (Field + GY enablers) — text-driven for Zombie World and friends.
        if ("zombie" in archetypes || "Zombie" in races || text?.isTypeChangeField == true && "Zombie" in races) {
            out += Triple("engine_synergy", "Zombie World", 2.2)
            out += Triple("engine_synergy", "Paladin of the Cursed Dragon", 2.15)
            out += Triple("engine_synergy", "Plaguespreader Zombie", 1.9)
            out += Triple("engine_synergy", "Mezuki", 1.85)
            out += Triple("engine_synergy", "Book of Life", 1.7)
            out += Triple("engine_synergy", "Doomkaiser Dragon", 1.75)
            out += Triple("engine_synergy", "Zombie Master", 1.7)
            out += Triple("engine_synergy", "Goblin Zombie", 1.55)
            out += Triple("engine_synergy", "Pyramid Turtle", 1.55)
        }
        val desc = source.description
        if (desc.contains("banish", ignoreCase = true) && race.contains("DRAGON")) {
            out += Triple("banish_synergy", "Dragon Ruler", 1.5)
        }
        return out.distinctBy { it.first to it.second }
    }

    private fun merge(
        map: LinkedHashMap<Int, RelatedCardRef>,
        id: Int,
        name: String,
        relation: String,
        score: Double,
    ) {
        val prev = map[id]
        if (prev == null || score > prev.score) {
            map[id] = RelatedCardRef(id = id, name = name, relation = relation, score = score)
        }
    }
}
