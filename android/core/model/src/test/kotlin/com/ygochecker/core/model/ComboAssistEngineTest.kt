package com.ygochecker.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ComboAssistEngineTest {
    private val myrmeleo = 91812389
    private val nightmare = 29616900
    private val bottomless = 29401950
    private val soulCharge = 54447022
    private val fireHand = 68535320
    private val iceHand = 95929069

    private val traptrixFlow = FlowGraph(
        id = "hat-myrmeleo-opening",
        formatId = "hat",
        title = "Traptrix Myrmeleo — opening line",
        archetype = "Traptrix",
        kind = FlowKind.OPENING,
        roleTags = listOf("Starter", "Control"),
        metrics = FlowMetrics(steps = 3, handCost = 1, risk = FlowRisk.LOW, payoff = FlowPayoff.CONTROL),
        startNodeId = "m0",
        nodes = mapOf(
            "m0" to FlowNode(id = "m0", sortIndex = 0, title = "NS Myrmeleo", cardIds = listOf(myrmeleo)),
            "m1" to FlowNode(id = "m1", sortIndex = 1, title = "Add Hole", cardIds = listOf(nightmare)),
            "m2" to FlowNode(id = "m2", sortIndex = 2, title = "Set and pass"),
        ),
        edges = listOf(
            FlowEdge("e0", "m0", "m1", "Search", isDefault = true),
            FlowEdge("e1", "m1", "m2", "Set", isDefault = true),
        ),
        chokePoints = listOf(
            FlowChokePoint(
                nodeId = "m1",
                severity = FlowRisk.MED,
                reason = "Search can be Debunk'd — line dies without a second Hole.",
                recoveryCardIds = listOf(bottomless),
                suggestedCopies = 2,
            ),
        ),
    )

    private val handFlow = FlowGraph(
        id = "hat-hand-bait",
        formatId = "hat",
        title = "Fire / Ice Hand — bait",
        kind = FlowKind.BAIT_REACTION,
        roleTags = listOf("BoardBreak"),
        metrics = FlowMetrics(steps = 3, risk = FlowRisk.HIGH, payoff = FlowPayoff.BOARD),
        startNodeId = "h0",
        nodes = mapOf(
            "h0" to FlowNode(id = "h0", sortIndex = 0, title = "Commit Hand", cardIds = listOf(fireHand, iceHand)),
            "h1" to FlowNode(
                id = "h1",
                sortIndex = 1,
                title = "Destruction CL1",
                timingWindow = FlowRehearsalEngine.TIMING_HAND_DESTRUCTION_CL1,
                body = "Must be CL1 or Hand misses timing.",
                cardIds = listOf(fireHand),
            ),
            "h2" to FlowNode(id = "h2", sortIndex = 2, title = "Resolve pop"),
        ),
        edges = listOf(
            FlowEdge("e0", "h0", "h1", "They destroy", isDefault = true),
            FlowEdge("e1", "h1", "h2", "CL1", isDefault = true),
        ),
        chokePoints = listOf(
            FlowChokePoint(
                nodeId = "h1",
                severity = FlowRisk.HIGH,
                reason = "Miss-timing if destruction is CL2+.",
                recoveryCardIds = listOf(soulCharge),
                suggestedCopies = 1,
            ),
        ),
    )

    private val roles = listOf(
        FormatCardRole(myrmeleo, "hat", HatCardRole.ENGINE_STARTER, priority = 10, partnerIds = listOf(nightmare)),
        FormatCardRole(fireHand, "hat", HatCardRole.BAIT, priority = 9, partnerIds = listOf(iceHand)),
    )

    @Test
    fun `for card lists combos and missing key partners`() {
        val advice = ComboAssistEngine.adviseForCard(
            seedCardId = myrmeleo,
            deckCounts = mapOf(myrmeleo to 3),
            graphs = listOf(traptrixFlow, handFlow),
            roles = roles,
        )
        assertEquals(1, advice.size)
        val line = advice.first()
        assertEquals("hat-myrmeleo-opening", line.flowId)
        assertEquals(listOf(myrmeleo, nightmare), line.keyCardIds)
        assertEquals(listOf(nightmare), line.missingKeyCardIds)
        assertTrue(line.actions.any { it.kind == ComboActionKind.ADD_PARTNER && it.cardId == nightmare })
    }

    @Test
    fun `choke recovery suggests copies when recovery missing`() {
        val advice = ComboAssistEngine.adviseForCard(
            seedCardId = fireHand,
            deckCounts = mapOf(fireHand to 2, iceHand to 2),
            graphs = listOf(handFlow),
            roles = roles,
        ).single()
        assertTrue(advice.chokes.any { it.nodeId == "h1" && it.severity == FlowRisk.HIGH })
        val recovery = advice.actions.filter { it.kind == ComboActionKind.ADD_RECOVERY }
        assertTrue(recovery.any { it.cardId == soulCharge && it.suggestedCopies == 1 })
    }

    @Test
    fun `auto choke from timingWindow when authored absent`() {
        val bare = handFlow.copy(chokePoints = emptyList())
        val advice = ComboAssistEngine.adviseForCard(
            seedCardId = fireHand,
            deckCounts = mapOf(fireHand to 1),
            graphs = listOf(bare),
            roles = roles,
        ).single()
        assertTrue(advice.chokes.any { it.nodeId == "h1" })
        assertTrue(advice.chokes.first { it.nodeId == "h1" }.reason.contains("CL1") ||
            advice.chokes.first { it.nodeId == "h1" }.reason.contains("timing"))
    }

    @Test
    fun `engine starter under 3 copies gets consistency boost tip`() {
        val advice = ComboAssistEngine.adviseForCard(
            seedCardId = myrmeleo,
            deckCounts = mapOf(myrmeleo to 1, nightmare to 2),
            graphs = listOf(traptrixFlow),
            roles = roles,
        ).single()
        assertTrue(
            advice.actions.any {
                it.kind == ComboActionKind.BOOST_COPIES &&
                    it.cardId == myrmeleo &&
                    it.suggestedCopies == 3 &&
                    it.currentCopies == 1
            },
        )
    }

    @Test
    fun `deck report ranks incomplete lines and flattens actions`() {
        val report = ComboAssistEngine.adviseForDeck(
            deckCounts = mapOf(myrmeleo to 2, fireHand to 2),
            graphs = listOf(traptrixFlow, handFlow),
            roles = roles,
        )
        assertEquals(2, report.lines.size)
        assertTrue(report.lines.any { it.flowId == "hat-myrmeleo-opening" && it.missingKeyCardIds.contains(nightmare) })
        assertTrue(report.actions.any { it.cardId == nightmare })
        assertTrue(report.actions.any { it.cardId == iceHand || it.cardId == soulCharge })
    }

    @Test
    fun `unrelated card yields empty advice`() {
        val advice = ComboAssistEngine.adviseForCard(
            seedCardId = 99999999,
            deckCounts = emptyMap(),
            graphs = listOf(traptrixFlow, handFlow),
            roles = roles,
        )
        assertTrue(advice.isEmpty())
    }
}
