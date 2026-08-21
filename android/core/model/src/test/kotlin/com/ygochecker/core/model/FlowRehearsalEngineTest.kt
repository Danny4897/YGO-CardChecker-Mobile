package com.ygochecker.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FlowRehearsalEngineTest {
    private val handFlow = FlowGraph(
        id = "hat-hand-bait",
        formatId = "hat",
        title = "Hand under backrow",
        kind = FlowKind.BAIT_REACTION,
        roleTags = listOf("Control", "BoardBreak"),
        metrics = FlowMetrics(steps = 3, handCost = 1, risk = FlowRisk.MED, payoff = FlowPayoff.BOARD),
        startNodeId = "h0",
        nodes = mapOf(
            "h0" to FlowNode(id = "h0", sortIndex = 0, title = "Set bait context"),
            "h1" to FlowNode(
                id = "h1",
                sortIndex = 1,
                title = "Destroy Fire Hand as CL1",
                timingWindow = FlowRehearsalEngine.TIMING_HAND_DESTRUCTION_CL1,
                cardIds = listOf(68535320),
            ),
            "h2" to FlowNode(id = "h2", sortIndex = 2, title = "Resolve SS + destroy S/T"),
            "h_alt" to FlowNode(id = "h_alt", sortIndex = 3, title = "Missed timing — pass"),
        ),
        edges = listOf(
            FlowEdge("e0", "h0", "h1", "Go for Hand", FlowEdgeCondition.ALWAYS, isDefault = true),
            FlowEdge("e1", "h1", "h2", "CL1 — keep timing", FlowEdgeCondition.ALWAYS, isDefault = true),
            FlowEdge("e2", "h1", "h_alt", "Would be CL2+", FlowEdgeCondition.USER_CHOICE, isDefault = false),
        ),
    )

    @Test
    fun `default path reaches payoff node`() {
        val eng = FlowRehearsalEngine(handFlow)
        assertEquals("h0", eng.currentId)
        eng.advanceDefault()
        assertEquals("h1", eng.currentId)
        eng.advanceDefault(TimingAnswer(chainLink = 1))
        assertEquals("h2", eng.currentId)
        assertTrue(eng.finished)
    }

    @Test
    fun `hand timing gate rejects chain link 2+`() {
        val eng = FlowRehearsalEngine(handFlow)
        eng.advanceDefault()
        assertFalse(eng.mustPassTimingGate(TimingAnswer(chainLink = 2)))
        assertTrue(eng.mustPassTimingGate(TimingAnswer(chainLink = 1)))
    }

    @Test
    fun `user can pick non-default branch`() {
        val eng = FlowRehearsalEngine(handFlow)
        eng.advanceDefault()
        val miss = eng.options().first { it.to == "h_alt" }
        eng.choose(miss, TimingAnswer(chainLink = 1))
        assertEquals("h_alt", eng.currentId)
    }

    @Test
    fun `toSummary keeps metrics`() {
        val s = handFlow.toSummary()
        assertEquals("hat-hand-bait", s.id)
        assertEquals(3, s.metrics.steps)
        assertEquals(FlowKind.BAIT_REACTION, s.kind)
    }
}
