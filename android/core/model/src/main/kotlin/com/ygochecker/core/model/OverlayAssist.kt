package com.ygochecker.core.model

enum class TipSeverity { INFO, WARN, CRITICAL }

enum class TurnOwner { UNKNOWN, YOU, OPPONENT }

data class AssistTip(
    val severity: TipSeverity,
    /** String resource name without package, e.g. overlay_tip_hand_cl1 */
    val messageKey: String,
    val relatedFlowId: String? = null,
    /** Optional free-text (ruling notes from roles). */
    val detail: String? = null,
)

data class ResourceCount(
    val cardId: Int,
    val label: String,
    val remaining: Int,
    val max: Int,
)

/**
 * HAT-focused live tips for the MDPRO overlay.
 * Pure: no Android deps — messageKey resolved by UI strings.
 */
object TimingRuleEngine {
    // HAT staples
    const val FIRE_HAND = 68535320
    const val ICE_HAND = 95929069
    const val ARTIFACT_SANCTUM = 12444060
    const val ARTIFACT_IGNITION = 21497230
    const val ARTIFACT_MORALLTACH = 20292186
    const val WIRETAP = 34507039
    const val FIENDISH_CHAIN = 50078509
    const val BREAKTHROUGH_SKILL = 78474168
    const val TRAPTRIX_TRAP_HOLE = 29616900
    const val MST = 5318639

    val KEY_TRACK_IDS: List<Int> = listOf(
        ARTIFACT_MORALLTACH,
        ARTIFACT_SANCTUM,
        TRAPTRIX_TRAP_HOLE,
        FIRE_HAND,
        ICE_HAND,
        WIRETAP,
        FIENDISH_CHAIN,
        BREAKTHROUGH_SKILL,
    )

    val KEY_LABELS: Map<Int, String> = mapOf(
        ARTIFACT_MORALLTACH to "Moralltach",
        ARTIFACT_SANCTUM to "Sanctum",
        TRAPTRIX_TRAP_HOLE to "TTH",
        FIRE_HAND to "Fire Hand",
        ICE_HAND to "Ice Hand",
        WIRETAP to "Wiretap",
        FIENDISH_CHAIN to "Fiendish",
        BREAKTHROUGH_SKILL to "BTS",
    )

    fun tipsFor(
        cardId: Int,
        format: GameFormat,
        roles: List<FormatCardRole>,
        whoseTurn: TurnOwner,
    ): List<AssistTip> {
        if (format != GameFormat.HAT) return emptyList()
        val out = ArrayList<AssistTip>(4)

        when (cardId) {
            FIRE_HAND, ICE_HAND -> {
                out += AssistTip(
                    TipSeverity.CRITICAL,
                    "overlay_tip_hand_cl1",
                    "hat-hand-bait",
                )
            }
            MST -> {
                out += AssistTip(
                    TipSeverity.WARN,
                    "overlay_tip_mst_hand",
                    "hat-hand-bait",
                )
            }
            ARTIFACT_SANCTUM -> {
                out += AssistTip(
                    TipSeverity.WARN,
                    "overlay_tip_sanctum_wiretap",
                    "hat-artifact-op-turn",
                )
                if (whoseTurn == TurnOwner.OPPONENT || whoseTurn == TurnOwner.UNKNOWN) {
                    out += AssistTip(
                        TipSeverity.INFO,
                        "overlay_tip_sanctum_window",
                        "hat-artifact-op-turn",
                    )
                }
            }
            ARTIFACT_IGNITION, ARTIFACT_MORALLTACH -> {
                out += AssistTip(
                    TipSeverity.INFO,
                    "overlay_tip_artifact_op",
                    "hat-artifact-op-turn",
                )
            }
            WIRETAP -> {
                out += AssistTip(TipSeverity.INFO, "overlay_tip_wiretap_sanctum", "hat-trap-priority")
            }
            FIENDISH_CHAIN, BREAKTHROUGH_SKILL, TRAPTRIX_TRAP_HOLE -> {
                out += AssistTip(TipSeverity.INFO, "overlay_tip_trap_priority", "hat-trap-priority")
            }
        }

        if (whoseTurn == TurnOwner.OPPONENT) {
            val interrupts = roles.filter {
                it.role == HatCardRole.INTERRUPT || it.role == HatCardRole.TRAP_LINE
            }
            if (interrupts.isNotEmpty() && out.none { it.messageKey == "overlay_tip_opp_hold" }) {
                out += AssistTip(TipSeverity.INFO, "overlay_tip_opp_hold", "hat-trap-priority")
            }
        }

        roles.firstOrNull { !it.rulingNotes.isNullOrBlank() }?.rulingNotes?.let { note ->
            if (out.size < 3) {
                out += AssistTip(
                    severity = TipSeverity.INFO,
                    messageKey = "overlay_tip_ruling_notes",
                    detail = note,
                )
            }
        }

        return out.distinctBy { it.messageKey }.take(3)
    }

    fun opponentPriorityTips(whoseTurn: TurnOwner): List<AssistTip> {
        if (whoseTurn != TurnOwner.OPPONENT) return emptyList()
        return listOf(
            AssistTip(TipSeverity.INFO, "overlay_tip_opp_priority", "hat-trap-priority"),
        )
    }
}

/**
 * Session resource tracker for HAT key cards.
 * Seed with format max copies; user taps to mark a copy spent (OCR does not auto-spend).
 */
class LiveResourceTracker {
    private val remaining = linkedMapOf<Int, Int>()
    private val maxima = linkedMapOf<Int, Int>()

    fun seed(maxById: Map<Int, Int>) {
        remaining.clear()
        maxima.clear()
        for ((id, max) in maxById) {
            if (max <= 0) continue
            maxima[id] = max
            remaining[id] = max
        }
    }

    fun spend(cardId: Int) {
        val cur = remaining[cardId] ?: return
        remaining[cardId] = (cur - 1).coerceAtLeast(0)
    }

    fun reset(cardId: Int) {
        val max = maxima[cardId] ?: return
        remaining[cardId] = max
    }

    fun snapshot(): List<ResourceCount> =
        TimingRuleEngine.KEY_TRACK_IDS.mapNotNull { id ->
            val max = maxima[id] ?: return@mapNotNull null
            val left = remaining[id] ?: max
            ResourceCount(
                cardId = id,
                label = TimingRuleEngine.KEY_LABELS[id] ?: id.toString(),
                remaining = left,
                max = max,
            )
        }.filter { it.max > 0 }
}
