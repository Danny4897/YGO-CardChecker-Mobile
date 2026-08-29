package com.ygochecker.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ComboPlannerTest {
    @Test
    fun fillerAndUser_produceTwoStepLine() {
        val lines = ComboPlanner.plan(
            mapOf(
                1 to setOf("hand_to_gy"),
                2 to setOf("ss_from_gy"),
            ),
        )
        assertEquals(1, lines.size)
        val line = lines.single()
        assertEquals(listOf(1, 2), line.cardIds)
        assertEquals(2, line.steps.size)
        assertEquals(GeneratedStepKind.FILL_GY, line.steps[0].kind)
        assertEquals(1, line.steps[0].cardId)
        assertEquals(GeneratedStepKind.SS_FROM_GY, line.steps[1].kind)
        assertEquals(2, line.steps[1].cardId)
    }

    @Test
    fun userWithFollowUpTag_getsThirdStep() {
        val lines = ComboPlanner.plan(
            mapOf(
                1 to setOf("mills"),
                2 to setOf("ss_from_gy", "searches_deck"),
            ),
        )
        val line = lines.single()
        assertEquals(3, line.steps.size)
        assertEquals(GeneratedStepKind.FOLLOW_UP, line.steps[2].kind)
        assertEquals(2, line.steps[2].cardId)
    }

    @Test
    fun cardCannotChainWithItself() {
        val lines = ComboPlanner.plan(mapOf(1 to setOf("hand_to_gy", "ss_from_gy")))
        assertTrue(lines.isEmpty())
    }

    @Test
    fun noFillerOrNoUser_producesNoLines() {
        assertTrue(ComboPlanner.plan(mapOf(1 to setOf("hand_to_gy"))).isEmpty())
        assertTrue(ComboPlanner.plan(mapOf(1 to setOf("ss_from_gy"))).isEmpty())
        assertTrue(ComboPlanner.plan(mapOf(1 to setOf("negates"), 2 to setOf("destroys"))).isEmpty())
    }

    @Test
    fun respectsMaxLines() {
        val onlyFillers = (1..10).associateWith { setOf("hand_to_gy") }
        assertTrue(ComboPlanner.plan(onlyFillers, maxLines = 3).isEmpty())

        val fillers = (1..5).associateWith { setOf("hand_to_gy") }
        val users = (6..10).associateWith { setOf("ss_from_gy") }
        val lines = ComboPlanner.plan(fillers + users, maxLines = 3)
        assertEquals(3, lines.size)
    }

    @Test
    fun describe_usesNamesAndFallsBackToId() {
        val line = GeneratedComboLine(
            cardIds = listOf(1, 2),
            steps = listOf(
                GeneratedComboStep(1, 1, GeneratedStepKind.FILL_GY),
                GeneratedComboStep(2, 2, GeneratedStepKind.SS_FROM_GY),
            ),
        )
        val described = line.describe(mapOf(1 to "Foolish Burial"))
        assertTrue(described[0].contains("Foolish Burial"))
        assertTrue(described[1].contains("Card 2")) // no name given for id 2 -> fallback label
    }
}
