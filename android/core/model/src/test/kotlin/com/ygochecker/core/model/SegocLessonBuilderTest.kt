package com.ygochecker.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SegocLessonBuilderTest {
    private fun trigger(
        cardId: Int,
        event: TriggerEvent,
        missed: Boolean = false,
    ) = SegocProfileSummary(
        cardId = cardId,
        effectType = SegocEffectType.TRIGGER,
        spellSpeed = 1,
        missedTimingRisk = missed,
        triggerEvents = listOf(event),
    )

    @Test
    fun `two of your triggers on the same event produce a lesson without OPP_ORDER`() {
        val profiles = mapOf(
            1 to trigger(1, TriggerEvent.DESTROYED),
            2 to trigger(2, TriggerEvent.DESTROYED),
        )
        val lessons = buildSegocLessons(listOf(1, 2), emptyList(), profiles)
        assertEquals(1, lessons.size)
        assertEquals(TriggerEvent.DESTROYED, lessons[0].event)
        assertEquals(listOf(1, 2), lessons[0].yourCardIds)
        assertTrue(lessons[0].oppCardIds.isEmpty())
        assertEquals(
            listOf(
                SegocStepKind.EVENT,
                SegocStepKind.YOUR_TRIGGERS,
                SegocStepKind.YOU_ORDER,
                SegocStepKind.RESOLVE_LIFO,
            ),
            lessons[0].steps.map { it.kind },
        )
    }

    @Test
    fun `one of yours plus one opp on the same event produces OPP_ORDER`() {
        val profiles = mapOf(
            10 to trigger(10, TriggerEvent.TO_GRAVE),
            20 to trigger(20, TriggerEvent.TO_GRAVE),
        )
        val lessons = buildSegocLessons(listOf(10), listOf(20), profiles)
        assertEquals(1, lessons.size)
        assertTrue(lessons[0].steps.any { it.kind == SegocStepKind.OPP_ORDER })
        assertEquals(listOf(20), lessons[0].oppCardIds)
    }

    @Test
    fun `OTHER event never produces a lesson`() {
        val profiles = mapOf(
            1 to trigger(1, TriggerEvent.OTHER),
            2 to trigger(2, TriggerEvent.OTHER),
        )
        assertTrue(buildSegocLessons(listOf(1, 2), emptyList(), profiles).isEmpty())
    }

    @Test
    fun `a single your trigger with no opp is not a lesson`() {
        val profiles = mapOf(1 to trigger(1, TriggerEvent.DESTROYED))
        assertTrue(buildSegocLessons(listOf(1), emptyList(), profiles).isEmpty())
    }

    @Test
    fun `ignition cards are ignored even on a shared event`() {
        val ignition = SegocProfileSummary(
            cardId = 3,
            effectType = SegocEffectType.IGNITION,
            spellSpeed = 1,
            missedTimingRisk = false,
            triggerEvents = listOf(TriggerEvent.DESTROYED),
        )
        val profiles = mapOf(
            1 to trigger(1, TriggerEvent.DESTROYED),
            3 to ignition,
        )
        assertTrue(buildSegocLessons(listOf(1, 3), emptyList(), profiles).isEmpty())
    }
}
