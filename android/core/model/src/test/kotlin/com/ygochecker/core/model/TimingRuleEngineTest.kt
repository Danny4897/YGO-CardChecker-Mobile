package com.ygochecker.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TimingRuleEngineTest {
    @Test
    fun `fire hand emits critical CL1 tip in HAT`() {
        val tips = TimingRuleEngine.tipsFor(
            cardId = TimingRuleEngine.FIRE_HAND,
            format = GameFormat.HAT,
            roles = emptyList(),
            whoseTurn = TurnOwner.UNKNOWN,
        )
        assertTrue(tips.any { it.messageKey == "overlay_tip_hand_cl1" })
        assertEquals(TipSeverity.CRITICAL, tips.first().severity)
    }

    @Test
    fun `non-hat format yields no tips`() {
        val tips = TimingRuleEngine.tipsFor(
            cardId = TimingRuleEngine.FIRE_HAND,
            format = GameFormat.TCG,
            roles = emptyList(),
            whoseTurn = TurnOwner.UNKNOWN,
        )
        assertTrue(tips.isEmpty())
    }

    @Test
    fun `sanctum warns about wiretap`() {
        val tips = TimingRuleEngine.tipsFor(
            cardId = TimingRuleEngine.ARTIFACT_SANCTUM,
            format = GameFormat.HAT,
            roles = emptyList(),
            whoseTurn = TurnOwner.OPPONENT,
        )
        assertTrue(tips.any { it.messageKey == "overlay_tip_sanctum_wiretap" })
    }

    @Test
    fun `resource tracker spend and snapshot`() {
        val tracker = LiveResourceTracker()
        tracker.seed(
            mapOf(
                TimingRuleEngine.ARTIFACT_MORALLTACH to 3,
                TimingRuleEngine.ARTIFACT_SANCTUM to 2,
            ),
        )
        tracker.spend(TimingRuleEngine.ARTIFACT_MORALLTACH)
        val snap = tracker.snapshot()
        val moral = snap.first { it.cardId == TimingRuleEngine.ARTIFACT_MORALLTACH }
        assertEquals(2, moral.remaining)
        assertEquals(3, moral.max)
    }
}
