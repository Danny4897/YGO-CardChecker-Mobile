package com.ygochecker.core.model

enum class ComboActionKind {
    /** Missing piece required to run the line. */
    ADD_PARTNER,
    /** Engine card present but under target copies (consistency). */
    BOOST_COPIES,
    /** Card that recovers after a choke / miss-timing. */
    ADD_RECOVERY,
}

data class ComboAction(
    val kind: ComboActionKind,
    val cardId: Int,
    val suggestedCopies: Int,
    val currentCopies: Int,
    val reason: String,
    val flowId: String,
    val flowTitle: String,
)

data class ComboChokeAdvice(
    val nodeId: String,
    val stepIndex: Int,
    val title: String,
    val severity: FlowRisk,
    val reason: String,
    val recoveryCardIds: List<Int>,
    val suggestedCopies: Int,
)

/**
 * One curated combo line evaluated against a deck (or a seed card + partial deck).
 * Product copy: "Vuoi giocare X? Per combo N servono queste carte; al choke 2 aggiungi Y."
 */
data class ComboLineAdvice(
    val flowId: String,
    val title: String,
    val tags: List<String>,
    val kind: FlowKind,
    val keyCardIds: List<Int>,
    val presentKeyCardIds: List<Int>,
    val missingKeyCardIds: List<Int>,
    val chokes: List<ComboChokeAdvice>,
    val actions: List<ComboAction>,
) {
    val presentKeyCount: Int get() = presentKeyCardIds.size
    val keyCount: Int get() = keyCardIds.size
    val coverageRatio: Double
        get() = if (keyCount == 0) 0.0 else presentKeyCount.toDouble() / keyCount

    /** Higher = more relevant to show first (incomplete valuable lines beat finished ones). */
    val priorityScore: Double
        get() {
            val missBoost = missingKeyCardIds.size * 1.4
            val chokeBoost = chokes.sumOf {
                when (it.severity) {
                    FlowRisk.HIGH -> 1.2
                    FlowRisk.MED -> 0.7
                    FlowRisk.LOW -> 0.3
                }
            }
            val kindBoost = when (kind) {
                FlowKind.OPENING -> 1.0
                FlowKind.ARTIFACT_ENGINE -> 0.9
                FlowKind.BAIT_REACTION -> 0.85
                FlowKind.TRAP_LINE -> 0.7
            }
            val coverage = coverageRatio
            // Prefer lines you already started but haven't finished.
            val partial = if (presentKeyCount > 0 && missingKeyCardIds.isNotEmpty()) 2.0 else 0.0
            return kindBoost + missBoost + chokeBoost + coverage + partial
        }
}

data class DeckComboReport(
    val lines: List<ComboLineAdvice>,
    val actions: List<ComboAction>,
)

/**
 * Pure deck-building assist: map Flow graphs + roles → combo partners, chokes, recovery copies.
 * No UI / no IO.
 */
object ComboAssistEngine {
    private const val STARTER_TARGET_COPIES = 3

    fun adviseForCard(
        seedCardId: Int,
        deckCounts: Map<Int, Int>,
        graphs: List<FlowGraph>,
        roles: List<FormatCardRole> = emptyList(),
    ): List<ComboLineAdvice> {
        val partnerIds = roles
            .filter { it.cardId == seedCardId }
            .flatMap { it.partnerIds }
            .toSet()
        val matched = graphs.filter { graph ->
            val keys = graph.keyCardIds()
            seedCardId in keys || keys.any { it in partnerIds }
        }
        return matched
            .map { buildAdvice(it, deckCounts, roles) }
            .sortedByDescending { it.priorityScore }
    }

    fun adviseForDeck(
        deckCounts: Map<Int, Int>,
        graphs: List<FlowGraph>,
        roles: List<FormatCardRole> = emptyList(),
    ): DeckComboReport {
        val lines = graphs.mapNotNull { graph ->
            val keys = graph.keyCardIds()
            if (keys.isEmpty()) return@mapNotNull null
            val hits = keys.count { (deckCounts[it] ?: 0) > 0 }
            if (hits == 0) return@mapNotNull null
            buildAdvice(graph, deckCounts, roles)
        }.sortedByDescending { it.priorityScore }

        val actions = lines
            .flatMap { it.actions }
            .distinctBy { Triple(it.kind, it.cardId, it.flowId) }
            .sortedWith(
                compareBy<ComboAction> {
                    when (it.kind) {
                        ComboActionKind.ADD_PARTNER -> 0
                        ComboActionKind.ADD_RECOVERY -> 1
                        ComboActionKind.BOOST_COPIES -> 2
                    }
                }.thenByDescending { it.suggestedCopies - it.currentCopies },
            )
        return DeckComboReport(lines = lines, actions = actions)
    }

    private fun buildAdvice(
        graph: FlowGraph,
        deckCounts: Map<Int, Int>,
        roles: List<FormatCardRole>,
    ): ComboLineAdvice {
        val keys = graph.keyCardIds()
        val present = keys.filter { (deckCounts[it] ?: 0) > 0 }
        val missing = keys.filter { (deckCounts[it] ?: 0) <= 0 }
        val chokes = resolveChokes(graph)
        val actions = mutableListOf<ComboAction>()

        for (id in missing) {
            actions += ComboAction(
                kind = ComboActionKind.ADD_PARTNER,
                cardId = id,
                suggestedCopies = suggestedPartnerCopies(id, roles),
                currentCopies = 0,
                reason = "Needed for «${graph.title}».",
                flowId = graph.id,
                flowTitle = graph.title,
            )
        }

        for (choke in chokes) {
            for (recoveryId in choke.recoveryCardIds) {
                val current = deckCounts[recoveryId] ?: 0
                if (current < choke.suggestedCopies) {
                    actions += ComboAction(
                        kind = ComboActionKind.ADD_RECOVERY,
                        cardId = recoveryId,
                        suggestedCopies = choke.suggestedCopies,
                        currentCopies = current,
                        reason = "Recovery after choke «${choke.title}»: ${choke.reason}",
                        flowId = graph.id,
                        flowTitle = graph.title,
                    )
                }
            }
        }

        for (id in present) {
            val role = roles.filter { it.cardId == id }
                .maxByOrNull { it.priority }
            if (role?.role == HatCardRole.ENGINE_STARTER && role.priority >= 8) {
                val current = deckCounts[id] ?: 0
                if (current in 1 until STARTER_TARGET_COPIES) {
                    actions += ComboAction(
                        kind = ComboActionKind.BOOST_COPIES,
                        cardId = id,
                        suggestedCopies = STARTER_TARGET_COPIES,
                        currentCopies = current,
                        reason = "Engine starter for «${graph.title}» — under $STARTER_TARGET_COPIES the line bricks often.",
                        flowId = graph.id,
                        flowTitle = graph.title,
                    )
                }
            }
        }

        return ComboLineAdvice(
            flowId = graph.id,
            title = graph.title,
            tags = graph.roleTags,
            kind = graph.kind,
            keyCardIds = keys,
            presentKeyCardIds = present,
            missingKeyCardIds = missing,
            chokes = chokes,
            actions = actions,
        )
    }

    private fun resolveChokes(graph: FlowGraph): List<ComboChokeAdvice> {
        val authored = graph.chokePoints.associateBy { it.nodeId }
        val out = linkedMapOf<String, ComboChokeAdvice>()
        for ((nodeId, choke) in authored) {
            val node = graph.nodes[nodeId]
            out[nodeId] = ComboChokeAdvice(
                nodeId = nodeId,
                stepIndex = node?.sortIndex ?: 0,
                title = node?.title ?: nodeId,
                severity = choke.severity,
                reason = choke.reason,
                recoveryCardIds = choke.recoveryCardIds,
                suggestedCopies = choke.suggestedCopies,
            )
        }
        for (node in graph.nodes.values) {
            if (node.timingWindow.isNullOrBlank()) continue
            if (node.id in out) continue
            out[node.id] = ComboChokeAdvice(
                nodeId = node.id,
                stepIndex = node.sortIndex,
                title = node.title,
                severity = FlowRisk.HIGH,
                reason = node.body?.takeIf { it.isNotBlank() }
                    ?: "Timing-sensitive step (${node.timingWindow}).",
                recoveryCardIds = emptyList(),
                suggestedCopies = 1,
            )
        }
        return out.values.sortedBy { it.stepIndex }
    }

    private fun suggestedPartnerCopies(cardId: Int, roles: List<FormatCardRole>): Int {
        val role = roles.filter { it.cardId == cardId }.maxByOrNull { it.priority } ?: return 2
        return when (role.role) {
            HatCardRole.ENGINE_STARTER, HatCardRole.SEARCHER -> 3
            HatCardRole.EXTENDER, HatCardRole.BAIT -> 2
            HatCardRole.INTERRUPT, HatCardRole.TRAP_LINE -> 2
            HatCardRole.REMOVAL, HatCardRole.RECYCLE -> 1
            HatCardRole.SIDE_TECH -> 2
        }
    }
}
