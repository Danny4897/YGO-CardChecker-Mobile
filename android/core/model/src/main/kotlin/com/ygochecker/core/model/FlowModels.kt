package com.ygochecker.core.model

enum class FlowKind {
    OPENING,
    BAIT_REACTION,
    ARTIFACT_ENGINE,
    TRAP_LINE,
}

enum class FlowEdgeCondition {
    ALWAYS,
    IF_BACKROW,
    IF_HAND_LIVE,
    IF_ARTIFACT_SET,
    USER_CHOICE,
}

enum class FlowRisk { LOW, MED, HIGH }

enum class FlowPayoff { BOARD, INTERRUPT, DRAW, RECYCLE, CONTROL }

data class FlowMetrics(
    val steps: Int,
    val handCost: Int = 0,
    val fieldCost: Int = 0,
    val risk: FlowRisk = FlowRisk.MED,
    val payoff: FlowPayoff = FlowPayoff.CONTROL,
)

data class FlowNode(
    val id: String,
    val sortIndex: Int,
    val title: String,
    val body: String? = null,
    /** e.g. chain_link_1_only — Hand destruction must be CL1. */
    val timingWindow: String? = null,
    val cardIds: List<Int> = emptyList(),
    val zoneHint: String? = null,
)

data class FlowEdge(
    val id: String,
    val from: String,
    val to: String,
    val label: String,
    val condition: FlowEdgeCondition = FlowEdgeCondition.ALWAYS,
    val isDefault: Boolean = false,
)

/** Authored interruption / brick point inside a Flow — drives deck-assist recovery tips. */
data class FlowChokePoint(
    val nodeId: String,
    val severity: FlowRisk = FlowRisk.HIGH,
    val reason: String,
    val recoveryCardIds: List<Int> = emptyList(),
    /** Target copies of each recovery card for consistency after this choke. */
    val suggestedCopies: Int = 1,
)

data class FlowGraph(
    val id: String,
    val formatId: String,
    val title: String,
    val archetype: String? = null,
    val kind: FlowKind,
    val roleTags: List<String> = emptyList(),
    val metrics: FlowMetrics,
    val nodes: Map<String, FlowNode>,
    val edges: List<FlowEdge>,
    val startNodeId: String,
    val chokePoints: List<FlowChokePoint> = emptyList(),
) {
    fun successors(nodeId: String): List<FlowEdge> =
        edges.filter { it.from == nodeId }.sortedWith(
            compareByDescending(FlowEdge::isDefault).thenBy(FlowEdge::label),
        )

    fun defaultEdge(nodeId: String): FlowEdge? {
        val opts = successors(nodeId)
        return opts.firstOrNull { it.isDefault } ?: opts.singleOrNull()
    }

    /** Passcodes referenced by nodes, stable order by node sortIndex then id. */
    fun keyCardIds(): List<Int> =
        nodes.values
            .sortedWith(compareBy(FlowNode::sortIndex).thenBy(FlowNode::id))
            .flatMap { it.cardIds }
            .distinct()
}

data class FlowSummary(
    val id: String,
    val formatId: String,
    val title: String,
    val archetype: String?,
    val kind: FlowKind,
    val roleTags: List<String>,
    val metrics: FlowMetrics,
)

fun FlowGraph.toSummary(): FlowSummary =
    FlowSummary(
        id = id,
        formatId = formatId,
        title = title,
        archetype = archetype,
        kind = kind,
        roleTags = roleTags,
        metrics = metrics,
    )

/** Answer supplied when a node requires a timing check before advancing. */
data class TimingAnswer(
    val chainLink: Int = 1,
)

/**
 * Interactive walk of a [FlowGraph].
 * Tap default = [advanceDefault]; multi-branch = [choose].
 */
class FlowRehearsalEngine(
    val graph: FlowGraph,
    startId: String = graph.startNodeId,
) {
    var currentId: String = startId
        private set

    val current: FlowNode
        get() = graph.nodes.getValue(currentId)

    val finished: Boolean
        get() = graph.successors(currentId).isEmpty()

    fun options(): List<FlowEdge> = graph.successors(currentId)

    fun mustPassTimingGate(answer: TimingAnswer): Boolean {
        val window = current.timingWindow ?: return true
        return when (window) {
            TIMING_CHAIN_LINK_1_ONLY,
            TIMING_HAND_DESTRUCTION_CL1,
            -> answer.chainLink == 1
            else -> true
        }
    }

    fun choose(edge: FlowEdge, timing: TimingAnswer = TimingAnswer()): FlowNode {
        require(edge.from == currentId) { "Edge does not leave current node" }
        require(mustPassTimingGate(timing)) { "Timing gate failed for ${current.timingWindow}" }
        currentId = edge.to
        return current
    }

    fun advanceDefault(timing: TimingAnswer = TimingAnswer()): FlowNode? {
        val edge = graph.defaultEdge(currentId) ?: return null
        return choose(edge, timing)
    }

    companion object {
        const val TIMING_CHAIN_LINK_1_ONLY = "chain_link_1_only"
        const val TIMING_HAND_DESTRUCTION_CL1 = "hand_destruction_cl1"
    }
}

/** Tactical role for a card inside a format (HAT-oriented). */
enum class HatCardRole {
    ENGINE_STARTER,
    SEARCHER,
    REMOVAL,
    BAIT,
    INTERRUPT,
    TRAP_LINE,
    SIDE_TECH,
    EXTENDER,
    RECYCLE,
}

data class FormatCardRole(
    val cardId: Int,
    val formatId: String,
    val role: HatCardRole,
    val priority: Int = 0,
    val rulingNotes: String? = null,
    val partnerIds: List<Int> = emptyList(),
)

enum class SegocEffectType { ACTIVATE, IGNITION, TRIGGER, QUICK, CONTINUOUS, NONE }

enum class TriggerEvent {
    DESTROYED, TO_GRAVE, REMOVED, LEAVES_FIELD,
    SUMMON_SUCCESS, FLIP_SUMMON_SUCCESS, SPECIAL_SUMMON_SUCCESS,
    DISCARDED, DRAWN, DAMAGE, CONTROL_CHANGED, BATTLE_DESTROYED,
    OTHER,
}

/** Whole-catalog SEGOC/timing profile for one card, extracted from real EDOPro Lua.
 * [spellSpeed] is null for vanilla cards with no effect block — it is never itself read from
 * Lua (not a real Lua constant), it's derived from card type + [effectType] on the TS side. */
data class SegocProfileSummary(
    val cardId: Int,
    val effectType: SegocEffectType,
    val spellSpeed: Int?,
    val missedTimingRisk: Boolean,
    val triggerEvents: List<TriggerEvent>,
)
