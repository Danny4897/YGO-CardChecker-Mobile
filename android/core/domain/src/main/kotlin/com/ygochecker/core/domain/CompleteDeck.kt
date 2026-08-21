package com.ygochecker.core.domain

import com.ygochecker.core.common.AppError
import com.ygochecker.core.common.AppResult
import com.ygochecker.core.model.Card
import com.ygochecker.core.model.DeckSection
import com.ygochecker.core.model.EffectTextProfiler
import com.ygochecker.core.model.FormatCardRole
import com.ygochecker.core.model.FormatExtraStaples
import com.ygochecker.core.model.GameFormat
import com.ygochecker.core.model.HatCardRole
import com.ygochecker.core.model.isExtraDeckType
import kotlinx.coroutines.flow.first
import javax.inject.Inject

data class DeckCompletionPlan(
    val addedMain: List<Pair<Card, Int>>,
    val addedExtra: List<Pair<Card, Int>>,
    val addedSide: List<Pair<Card, Int>>,
    val mainTotal: Int,
    val extraTotal: Int,
    val sideTotal: Int,
)

/**
 * Cascading synergy autocomplete:
 * archetype / text coherence, Tuner↔Synchro gates, package pairs,
 * and a diversified Extra (1-of tools: Synchro + Xyz + Fusion + Link).
 *
 * [extraStapleNames] — optional format Extra toolbox (101, Exciton, …).
 * Empty set skips staples; prefer [FormatExtraStaples.defaultNames].
 */
fun interface CompleteDeck {
    suspend fun invoke(
        deckId: Long,
        targetMain: Int,
        targetExtra: Int,
        targetSide: Int,
        format: GameFormat,
        extraStapleNames: Set<String>,
    ): AppResult<DeckCompletionPlan>
}

suspend fun CompleteDeck.invoke(
    deckId: Long,
    targetMain: Int,
    targetExtra: Int,
    targetSide: Int,
    format: GameFormat,
): AppResult<DeckCompletionPlan> = invoke(
    deckId,
    targetMain,
    targetExtra,
    targetSide,
    format,
    FormatExtraStaples.defaultNames,
)

private enum class ExtraKind { SYNCHRO, XYZ, FUSION, LINK, OTHER }

class SynergyCompleteDeck @Inject constructor(
    private val decks: DeckRepository,
    private val cards: CardRepository,
    private val related: GetRelatedCards,
    private val scripts: GetEffectScripts,
    private val legality: EvaluateDeckLegality,
    private val resolve: ResolveCardById,
    private val flowCatalog: FlowCatalog,
) : CompleteDeck {
    override suspend fun invoke(
        deckId: Long,
        targetMain: Int,
        targetExtra: Int,
        targetSide: Int,
        format: GameFormat,
        extraStapleNames: Set<String>,
    ): AppResult<DeckCompletionPlan> {
        val deck = decks.observeDeck(deckId).first()
            ?: return AppResult.Err(AppError("deck.not_found"))
        val mainTarget = targetMain.coerceIn(20, 60)
        val extraTarget = targetExtra.coerceIn(0, 15)
        val sideTarget = targetSide.coerceIn(0, 15)

        var mainQty = deck.cards.filter { it.section == DeckSection.MAIN }.sumOf { it.quantity }
        var extraQty = deck.cards.filter { it.section == DeckSection.EXTRA }.sumOf { it.quantity }
        var sideQty = deck.cards.filter { it.section == DeckSection.SIDE }.sumOf { it.quantity }
        val qtyById = deck.cards
            .groupBy { it.card.id to it.section }
            .mapValues { (_, rows) -> rows.sumOf { it.quantity } }
            .toMutableMap()
        val copiesAcross = deck.cards
            .groupBy { it.card.id }
            .mapValues { (_, rows) -> rows.sumOf { it.quantity } }
            .toMutableMap()

        val profileCache = linkedMapOf<Int, EffectTextProfiler.Profile>()
        suspend fun profileOf(card: Card): EffectTextProfiler.Profile {
            profileCache[card.id]?.let { return it }
            val full = cards.get(card.id) ?: card
            val text = EffectTextProfiler.profile(full)
            val script = scripts.invoke(listOf(card.id)).firstOrNull()
            val merged = text.copy(
                tags = EffectTextProfiler.mergeTags(script?.tags.orEmpty(), text.tags).toSet(),
                roles = EffectTextProfiler.mergeRoles(script?.roles.orEmpty(), text.roles).toSet(),
            )
            profileCache[card.id] = merged
            return merged
        }

        val seedCards = deck.cards.map { it.card }
        val seedProfiles = seedCards.associate { it.id to profileOf(it) }
        val deckArchetypes = seedProfiles.values.flatMap { it.archetypes }.toMutableSet()
        var hasTuner = seedProfiles.values.any { it.isTuner } ||
            seedCards.any { it.type.contains("Tuner", ignoreCase = true) }
        var hasSynchroExtra = seedProfiles.values.any { it.isSynchroMonster } ||
            deck.cards.any {
                it.section == DeckSection.EXTRA && it.card.type.contains("Synchro", ignoreCase = true)
            }
        val namesInDeck = deck.cards.map { it.card.name.lowercase() }.toMutableSet()

        // Main monster levels → Xyz ranks we can make (2+ bodies of same level).
        val levelCounts = linkedMapOf<Int, Int>()
        fun noteMainLevel(card: Card, qty: Int) {
            if (isExtraDeckType(card.type)) return
            if (!card.type.contains("Monster", ignoreCase = true)) return
            val lv = card.level ?: return
            if (lv !in 1..12) return
            levelCounts[lv] = (levelCounts[lv] ?: 0) + qty
        }
        deck.cards.filter { it.section == DeckSection.MAIN }.forEach { noteMainLevel(it.card, it.quantity) }
        fun xyzRanksReady(): Set<Int> =
            levelCounts.filter { it.value >= 2 }.keys

        val extraKindCounts = mutableMapOf(
            ExtraKind.SYNCHRO to 0,
            ExtraKind.XYZ to 0,
            ExtraKind.FUSION to 0,
            ExtraKind.LINK to 0,
            ExtraKind.OTHER to 0,
        )
        fun noteExtraKind(card: Card, delta: Int = 1) {
            val kind = extraKindOf(card)
            extraKindCounts[kind] = (extraKindCounts[kind] ?: 0) + delta
        }
        deck.cards.filter { it.section == DeckSection.EXTRA }.forEach { noteExtraKind(it.card, it.quantity) }

        val scores = linkedMapOf<Int, Double>()
        val expanded = mutableSetOf<Int>()
        val pendingExpand = ArrayDeque<Pair<Int, Double>>()

        fun seedWeight(cardId: Int): Double {
            val q = copiesAcross[cardId] ?: 1
            val eng = profileCache[cardId]?.engineWeight ?: 1.0
            return (1.0 + q * 0.15) * eng
        }

        suspend fun expand(cardId: Int, weight: Double) {
            if (!expanded.add(cardId)) return
            related.invoke(cardId, format, RELATED_LIMIT).forEach { ref ->
                if (ref.id == cardId) return@forEach
                scores[ref.id] = (scores[ref.id] ?: 0.0) + ref.score * weight
            }
        }

        suspend fun drainPending(max: Int = 12) {
            var n = 0
            while (pendingExpand.isNotEmpty() && n < max) {
                val (id, w) = pendingExpand.removeFirst()
                expand(id, w)
                n++
            }
        }

        /** Inject Extra toolbox needles (Xyz/Fusion/Link/Synchro) into the score frontier. */
        suspend fun boostExtraToolbox() {
            val needles = buildList {
                if (hasTuner) add("Synchro Monster" to 1.6)
                xyzRanksReady().forEach { rank ->
                    add("Rank $rank" to 1.85)
                    add("Xyz" to 1.5)
                }
                if (deckArchetypes.contains("hero") || namesInDeck.any { it.contains("hero") }) {
                    add("Fusion Monster" to 1.7)
                    add("Polymerization" to 1.4)
                }
                add("Xyz Monster" to 1.35)
                add("Link Monster" to 1.2)
                // Soft Fusion nudge only when deck already plays Fusion lines / poly.
                if (namesInDeck.any { it.contains("polymer") || it.contains("fusion") } ||
                    deckArchetypes.contains("hero")
                ) {
                    add("Fusion Monster" to 1.25)
                }
            }
            for ((needle, w) in needles.distinctBy { it.first }) {
                for (seed in seedCards.take(10)) {
                    related.invoke(seed.id, format, 24).forEach { ref ->
                        val card = resolve.invoke(ref.id) ?: return@forEach
                        if (!isExtraDeckType(card.type)) return@forEach
                        val typeHit = card.type.contains(needle.substringBefore(' '), ignoreCase = true) ||
                            card.name.contains(needle, ignoreCase = true) ||
                            (needle.startsWith("Rank") && card.type.contains("Xyz", ignoreCase = true) &&
                                card.level == needle.removePrefix("Rank ").trim().toIntOrNull())
                        if (typeHit || needle.contains("Xyz") && card.type.contains("Xyz", ignoreCase = true) ||
                            needle.contains("Fusion") && card.type.contains("Fusion", ignoreCase = true) ||
                            needle.contains("Link") && card.type.contains("Link", ignoreCase = true) ||
                            needle.contains("Synchro") && card.type.contains("Synchro", ignoreCase = true)
                        ) {
                            scores[ref.id] = (scores[ref.id] ?: 0.0) + ref.score * w
                        }
                    }
                }
            }
            // Named Extra staples chosen by the user (optional toolbox).
            for (name in extraStapleNames) {
                val card = cards.resolveByName(name) ?: continue
                if (!isExtraDeckType(card.type)) continue
                val kind = extraKindOf(card)
                val fit = when (kind) {
                    ExtraKind.XYZ -> {
                        val rank = card.level
                        rank != null && rank in xyzRanksReady()
                    }
                    ExtraKind.SYNCHRO -> hasTuner
                    ExtraKind.LINK -> true
                    ExtraKind.FUSION -> namesInDeck.any {
                        it.contains("polymer") || it.contains("fusion") || it.contains("hero")
                    }
                    ExtraKind.OTHER -> false
                }
                if (!fit) continue
                scores[card.id] = (scores[card.id] ?: 0.0) + 4.2
            }
        }

        deck.cards
            .sortedByDescending { it.quantity }
            .map { it.card.id }
            .distinct()
            .forEach { id -> pendingExpand.addLast(id to seedWeight(id)) }
        drainPending(24)
        boostFromMatchedFlows(format, copiesAcross.keys.toSet(), scores, pendingExpand)
        drainPending(16)

        if (extraTarget > extraQty) {
            if (!hasTuner) {
                seedCards.take(12).forEach { pendingExpand.addLast(it.id to seedWeight(it.id) * 1.4) }
                drainPending(16)
            }
            boostExtraToolbox()
        }

        val addedMain = mutableListOf<Pair<Card, Int>>()
        val addedExtra = mutableListOf<Pair<Card, Int>>()
        val addedSide = mutableListOf<Pair<Card, Int>>()

        var stalled = 0
        var steps = 0
        while (steps++ < MAX_STEPS) {
            val needMain = mainQty < mainTarget
            val needExtra = extraQty < extraTarget
            val needSide = sideQty < sideTarget
            if (!needMain && !needExtra && !needSide) break

            drainPending()
            if (needExtra && steps % 8 == 0) boostExtraToolbox()

            val pick = pickCandidate(
                scores = scores,
                needMain = needMain,
                needExtra = needExtra,
                needSide = needSide,
                mainGap = mainTarget - mainQty,
                extraGap = extraTarget - extraQty,
                sideGap = sideTarget - sideQty,
                qtyById = qtyById,
                copiesAcross = copiesAcross,
                format = format,
                deckArchetypes = deckArchetypes,
                hasTuner = hasTuner,
                hasSynchroExtra = hasSynchroExtra,
                namesInDeck = namesInDeck,
                xyzRanks = xyzRanksReady(),
                extraKindCounts = extraKindCounts,
                profileOf = ::profileOf,
                extraStapleNames = extraStapleNames,
            )
            if (pick == null) {
                val unexpanded = copiesAcross.keys.filter { it !in expanded }
                if (unexpanded.isNotEmpty()) {
                    unexpanded.take(10).forEach { pendingExpand.addLast(it to seedWeight(it)) }
                    stalled = 0
                    continue
                }
                stalled++
                if (stalled >= 3) break
                copiesAcross.keys.take(16).forEach { pendingExpand.addLast(it to seedWeight(it) * 1.1) }
                if (needExtra) boostExtraToolbox()
                continue
            }
            stalled = 0

            val (card, section, add) = pick
            val key = card.id to section
            val have = qtyById[key] ?: 0
            val next = have + add
            decks.setCard(deckId, card, next, section)
            qtyById[key] = next
            copiesAcross[card.id] = (copiesAcross[card.id] ?: 0) + add
            namesInDeck += card.name.lowercase()
            val prof = profileOf(card)
            deckArchetypes += prof.archetypes
            if (prof.isTuner) hasTuner = true
            if (section == DeckSection.MAIN) noteMainLevel(card, add)
            if (prof.isSynchroMonster || (section == DeckSection.EXTRA && card.type.contains("Synchro", ignoreCase = true))) {
                hasSynchroExtra = true
            }
            when (section) {
                DeckSection.MAIN -> {
                    mainQty += add
                    addedMain += card to add
                }
                DeckSection.EXTRA -> {
                    extraQty += add
                    addedExtra += card to add
                    noteExtraKind(card, add)
                }
                DeckSection.SIDE -> {
                    sideQty += add
                    addedSide += card to add
                }
            }
            pendingExpand.addFirst(card.id to 1.4 * prof.engineWeight)
            for (partner in prof.packagePartners) {
                cards.resolveByName(partner)?.let { p ->
                    scores[p.id] = (scores[p.id] ?: 0.0) + 3.0
                    pendingExpand.addFirst(p.id to 2.0)
                }
            }
            seedCards.take(8).forEach { pendingExpand.addLast(it.id to seedWeight(it.id) * 0.9) }
        }

        return AppResult.Ok(
            DeckCompletionPlan(
                addedMain = addedMain,
                addedExtra = addedExtra,
                addedSide = addedSide,
                mainTotal = mainQty,
                extraTotal = extraQty,
                sideTotal = sideQty,
            ),
        )
    }

    private suspend fun pickCandidate(
        scores: Map<Int, Double>,
        needMain: Boolean,
        needExtra: Boolean,
        needSide: Boolean,
        mainGap: Int,
        extraGap: Int,
        sideGap: Int,
        qtyById: Map<Pair<Int, DeckSection>, Int>,
        copiesAcross: Map<Int, Int>,
        format: GameFormat,
        deckArchetypes: Set<String>,
        hasTuner: Boolean,
        hasSynchroExtra: Boolean,
        namesInDeck: Set<String>,
        xyzRanks: Set<Int>,
        extraKindCounts: Map<ExtraKind, Int>,
        profileOf: suspend (Card) -> EffectTextProfiler.Profile,
        extraStapleNames: Set<String>,
    ): Triple<Card, DeckSection, Int>? {
        var best: Triple<Card, DeckSection, Int>? = null
        var bestScore = Double.NEGATIVE_INFINITY
        val hungriestExtra = hungriestExtraKind(extraKindCounts, hasTuner, xyzRanks)

        for ((id, rawScore) in scores) {
            val card = resolve.invoke(id) ?: continue
            val max = legality.maxCopies(card.id, format)
            if (max <= 0) continue
            val across = copiesAcross[id] ?: 0
            if (across >= max) continue
            val prof = profileOf(card)

            val section = preferredSection(
                card = card,
                prof = prof,
                needMain = needMain,
                needExtra = needExtra,
                needSide = needSide,
                haveMain = qtyById[id to DeckSection.MAIN] ?: 0,
                haveExtra = qtyById[id to DeckSection.EXTRA] ?: 0,
                haveSide = qtyById[id to DeckSection.SIDE] ?: 0,
                max = max,
                hasTuner = hasTuner,
                format = format,
                hatRoles = flowCatalog.rolesFor(id, format),
            ) ?: continue

            val room = when (section) {
                DeckSection.MAIN -> mainGap
                DeckSection.EXTRA -> extraGap
                DeckSection.SIDE -> sideGap
            }
            if (room <= 0) continue
            val have = qtyById[id to section] ?: 0
            // Extra: never stack copies — diversity over 3x same Synchro.
            if (section == DeckSection.EXTRA && have >= 1) continue
            if (have >= max) continue
            val add = minOf(
                max - have,
                room,
                preferredCopies(max, have, prof, section),
            )
            if (add <= 0) continue

            var score = rawScore * prof.engineWeight
            val hatRoles = flowCatalog.rolesFor(id, format)
            if (hatRoles.isNotEmpty()) {
                score *= 1.0 + hatRoles.maxOf { it.priority } * 0.04
                when {
                    hatRoles.any { it.role == HatCardRole.ENGINE_STARTER || it.role == HatCardRole.SEARCHER } ->
                        score *= 1.55
                    hatRoles.any { it.role == HatCardRole.TRAP_LINE || it.role == HatCardRole.INTERRUPT } ->
                        score *= 1.4
                    hatRoles.any { it.role == HatCardRole.BAIT || it.role == HatCardRole.REMOVAL } ->
                        score *= 1.35
                    hatRoles.any { it.role == HatCardRole.SIDE_TECH } && section == DeckSection.SIDE ->
                        score *= 1.7
                    hatRoles.any { it.role == HatCardRole.SIDE_TECH } && section != DeckSection.SIDE ->
                        score *= 0.55
                }
            }
            val gapBoost = when (section) {
                DeckSection.EXTRA -> 1.0 + extraGap * 0.45
                DeckSection.MAIN -> 1.0 + mainGap * 0.06
                DeckSection.SIDE -> 1.0 + sideGap * 0.1
            }
            score *= gapBoost

            if (deckArchetypes.isNotEmpty()) {
                val overlap = prof.archetypes.intersect(deckArchetypes)
                val raceOverlap = prof.mentionedRaces.any { race ->
                    deckArchetypes.contains(race.lowercase()) ||
                        deckArchetypes.contains(race.lowercase().replace('-', '_')) ||
                        deckArchetypes.contains(race.lowercase().replace(" ", "_"))
                }
                val staple = prof.roles.contains("handtrap") || prof.roles.contains("board_breaker")
                when {
                    overlap.isNotEmpty() || raceOverlap -> score *= 1.0 + overlap.size * 0.55
                    staple || section == DeckSection.SIDE -> score *= 0.85
                    prof.archetypes.isEmpty() && prof.mentionedRaces.isEmpty() -> {
                        val engineSpell = card.name.contains("Polymerization", ignoreCase = true) ||
                            card.name.contains("Fusion", ignoreCase = true) ||
                            card.name.contains("Transmodify", ignoreCase = true) ||
                            prof.packagePartners.isNotEmpty()
                        when {
                            staple || section == DeckSection.SIDE -> score *= 0.85
                            engineSpell -> score *= 1.05
                            card.type.contains("Trap", ignoreCase = true) -> continue
                            card.type.contains("Spell", ignoreCase = true) -> score *= 0.35
                            else -> score *= 0.22
                        }
                    }
                    else -> continue
                }
            }

            if (section == DeckSection.EXTRA && prof.requiresTuner && !hasTuner) continue
            if (section == DeckSection.EXTRA && !extraSummonable(card, prof, namesInDeck)) continue
            if (!hasTuner && !hasSynchroExtra && !prof.isTuner) {
                val nameL = card.name.lowercase()
                if (nameL.contains("rescue") && !nameL.contains("hero")) score *= 0.15
                if (prof.tags.contains("changes_level") && deckArchetypes.isNotEmpty()) score *= 0.45
            }

            if (prof.packagePartners.isNotEmpty() && section != DeckSection.EXTRA) {
                val partnerPresent = prof.packagePartners.any { partner ->
                    namesInDeck.any { it.contains(partner) || partner.contains(it) }
                }
                score *= if (partnerPresent) 2.2 else 0.12
            }

            if (needExtra && !hasTuner && prof.isTuner) score *= 2.6

            if (section == DeckSection.EXTRA) {
                val kind = extraKindOf(card)
                // Diversify Extra kinds — don't flood with Synchro / locked Fusion.
                score *= when (kind) {
                    hungriestExtra -> 2.4
                    ExtraKind.XYZ -> if (xyzRanks.isNotEmpty()) 1.9 else 0.55
                    ExtraKind.FUSION -> if (namesInDeck.any {
                            it.contains("polymer") || it.contains("fusion") || it.contains("hero")
                        }
                    ) {
                        1.55
                    } else {
                        0.35
                    }
                    ExtraKind.LINK -> 1.25
                    ExtraKind.SYNCHRO -> if (hasTuner) 1.35 else 0.2
                    ExtraKind.OTHER -> 0.8
                }
                // Xyz rank fit vs Main levels.
                if (kind == ExtraKind.XYZ) {
                    val rank = card.level
                    score *= when {
                        rank != null && rank in xyzRanks -> 2.6
                        xyzRanks.isNotEmpty() -> 0.45
                        else -> 0.7
                    }
                }
                if (kind == ExtraKind.SYNCHRO && hasTuner) {
                    val lv = card.level
                    if (lv != null && lv in 2..12) score *= 1.15
                }
                // Already have several of this kind → prefer something else.
                val kindHave = extraKindCounts[kind] ?: 0
                if (kindHave >= 3) score *= 0.45
                if (kindHave >= 5) score *= 0.35
                // Named staple toolbox gets a mild bump when already scored in.
                if (extraStapleNames.any { card.name.equals(it, ignoreCase = true) }) {
                    score *= 1.35
                }
            }

            if (section == DeckSection.MAIN && mainGap > 8 &&
                prof.roles.contains("tech") &&
                !prof.roles.contains("starter") &&
                !prof.roles.contains("extender") &&
                !prof.isTuner
            ) {
                score *= 0.7
            }

            if (score > bestScore) {
                bestScore = score
                best = Triple(card, section, add)
            }
        }
        return best
    }

    private fun hungriestExtraKind(
        counts: Map<ExtraKind, Int>,
        hasTuner: Boolean,
        xyzRanks: Set<Int>,
    ): ExtraKind {
        val candidates = buildList {
            if (xyzRanks.isNotEmpty()) add(ExtraKind.XYZ)
            if (hasTuner) add(ExtraKind.SYNCHRO)
            add(ExtraKind.LINK)
            // Fusion last unless already started — avoids Armityle/Barbaroid-style noise.
            add(ExtraKind.FUSION)
            if (xyzRanks.isEmpty()) add(ExtraKind.XYZ)
        }.distinct()
        return candidates.minByOrNull { counts[it] ?: 0 } ?: ExtraKind.XYZ
    }

    /**
     * Skip Extra bosses that need named materials / contact fusion the Main does not play.
     * Generic "2 monsters" / "1 Tuner + …" lines remain eligible.
     */
    private fun extraSummonable(
        card: Card,
        prof: EffectTextProfiler.Profile,
        namesInDeck: Set<String>,
    ): Boolean {
        val nameL = card.name.lowercase()
        if (HARD_SKIP_EXTRA_NAMES.any { nameL.contains(it) }) return false
        if (!prof.requiresNamedMaterials && prof.namedMaterials.isEmpty()) return true
        val materials = prof.namedMaterials.ifEmpty {
            // Synchro "\"Junk Synchron\" + …" still lists materials but is often playable with any Tuner.
            if (card.type.contains("Synchro", ignoreCase = true) && !prof.requiresNamedMaterials) {
                return true
            }
            prof.namedMaterials
        }
        if (materials.isEmpty()) {
            // Locked fusion text without parseable quotes — still skip if flagged.
            return !prof.requiresNamedMaterials
        }
        // Require at least half of named materials present (Armityle needs all 3 Sacred Beasts).
        val hits = materials.count { mat ->
            namesInDeck.any { it.contains(mat) || mat.contains(it) }
        }
        val need = when {
            materials.size >= 3 -> materials.size
            else -> (materials.size + 1) / 2
        }
        return hits >= need
    }

    private fun extraKindOf(card: Card): ExtraKind {
        val t = card.type
        return when {
            t.contains("Synchro", ignoreCase = true) -> ExtraKind.SYNCHRO
            t.contains("Xyz", ignoreCase = true) || t.contains("XYZ", ignoreCase = true) -> ExtraKind.XYZ
            t.contains("Fusion", ignoreCase = true) -> ExtraKind.FUSION
            t.contains("Link", ignoreCase = true) -> ExtraKind.LINK
            else -> ExtraKind.OTHER
        }
    }

    private suspend fun boostFromMatchedFlows(
        format: GameFormat,
        deckIds: Set<Int>,
        scores: MutableMap<Int, Double>,
        pendingExpand: ArrayDeque<Pair<Int, Double>>,
    ) {
        for (summary in flowCatalog.list(format)) {
            val graph = flowCatalog.get(summary.id) ?: continue
            val flowCards = graph.nodes.values.flatMap { it.cardIds }.toSet()
            if (flowCards.isEmpty()) continue
            val hit = flowCards.intersect(deckIds)
            if (hit.isEmpty()) continue
            val boost = 2.4 * (1.0 + hit.size * 0.35)
            for (id in flowCards) {
                scores[id] = (scores[id] ?: 0.0) + boost
                pendingExpand.addLast(id to 1.9)
            }
        }
    }

    private fun preferredSection(
        card: Card,
        prof: EffectTextProfiler.Profile,
        needMain: Boolean,
        needExtra: Boolean,
        needSide: Boolean,
        haveMain: Int,
        haveExtra: Int,
        haveSide: Int,
        max: Int,
        hasTuner: Boolean,
        format: GameFormat,
        hatRoles: List<FormatCardRole>,
    ): DeckSection? {
        val extraType = prof.isExtraMonster || isExtraDeckType(card.type)
        if (extraType) {
            if (prof.requiresTuner && !hasTuner && prof.isSynchroMonster) return null
            if (needExtra && haveExtra == 0) return DeckSection.EXTRA
            if (needSide && haveSide < max) return DeckSection.SIDE
            return null
        }
        val sideTech = hatRoles.any { it.role == HatCardRole.SIDE_TECH }
        if (sideTech && needSide && haveSide < max) return DeckSection.SIDE
        if (prof.isTuner && needMain && haveMain < max) return DeckSection.MAIN
        if (needMain && haveMain < max) return DeckSection.MAIN
        if (needSide && haveSide < max) return DeckSection.SIDE
        return null
    }

    private fun preferredCopies(
        max: Int,
        have: Int,
        prof: EffectTextProfiler.Profile,
        section: DeckSection,
    ): Int {
        if (section == DeckSection.EXTRA) return 1
        return when {
            have == 0 && (prof.roles.contains("starter") || prof.roles.contains("extender")) && max >= 3 -> 3
            have == 0 && max >= 3 -> 2
            have == 0 -> max.coerceAtMost(2)
            else -> 1
        }
    }

    private companion object {
        const val RELATED_LIMIT = 56
        const val MAX_STEPS = 260

        /** Well-known Extra bosses that need packages this auto-complete cannot guarantee. */
        val HARD_SKIP_EXTRA_NAMES = listOf(
            "armityle",
            "barbaroid",
            "phantasm emperor",
            "rainbow neos",
            "ultimate ancient gear golem",
            "chimeratech overdragon",
            "cyber end dragon",
            "five-headed dragon",
            "dragon master knight",
            "gate guardian",
        )
    }
}
