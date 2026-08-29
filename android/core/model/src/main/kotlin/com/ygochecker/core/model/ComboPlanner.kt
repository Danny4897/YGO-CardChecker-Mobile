package com.ygochecker.core.model

/**
 * Bounded, generic combo-line generator: chains a card that fills the GY into a card that
 * spends the GY, using only the mechanic tags every card already has (script or text derived).
 * Unlike [ComboRecipe] this is COMPUTED, not curated — it finds lines nobody wrote by hand, for
 * any archetype the deck's actual cards support. Deliberately conservative:
 * - reasons over deck composition (cards present), not a simulated drawn hand — same convention
 *   as [matchRecipes] and the SEGOC lesson engine, since the app has no hand-draw simulator;
 * - capped at 2 core steps + 1 optional follow-up, not an open-ended game-state search, so a
 *   line stays something a player can sanity-check rather than a black box;
 * - always surfaced as generated/unverified, never merged into the curated [ComboRecipe] library.
 */
enum class GeneratedStepKind { FILL_GY, SS_FROM_GY, FOLLOW_UP }

data class GeneratedComboStep(val order: Int, val cardId: Int, val kind: GeneratedStepKind)

data class GeneratedComboLine(val cardIds: List<Int>, val steps: List<GeneratedComboStep>) {
    fun describe(namesById: Map<Int, String>): List<String> = steps.map { step ->
        val name = namesById[step.cardId] ?: "Card ${step.cardId}"
        when (step.kind) {
            GeneratedStepKind.FILL_GY -> "$name sends a monster to the GY"
            GeneratedStepKind.SS_FROM_GY -> "Special Summon $name from the GY"
            GeneratedStepKind.FOLLOW_UP -> "$name follows up (search / Special Summon)"
        }
    }
}

object ComboPlanner {
    private val FILLER_TAGS = setOf("hand_to_gy", "sends_to_gy", "mills", "discards")
    private val USER_TAGS = setOf("ss_from_gy", "revives_from_gy")
    private val FOLLOW_UP_TAGS = setOf("searches_deck", "special_summons")

    /** [cardTags]: deck card id -> its mechanic tags. Only cards actually in the deck are considered. */
    fun plan(cardTags: Map<Int, Set<String>>, maxLines: Int = 12): List<GeneratedComboLine> {
        val fillers = cardTags.filterValues { tags -> tags.any { it in FILLER_TAGS } }.keys
        val users = cardTags.filterValues { tags -> tags.any { it in USER_TAGS } }.keys
        val out = mutableListOf<GeneratedComboLine>()
        for (filler in fillers) {
            for (user in users) {
                if (filler == user) continue
                val steps = mutableListOf(
                    GeneratedComboStep(1, filler, GeneratedStepKind.FILL_GY),
                    GeneratedComboStep(2, user, GeneratedStepKind.SS_FROM_GY),
                )
                if (cardTags[user].orEmpty().any { it in FOLLOW_UP_TAGS }) {
                    steps += GeneratedComboStep(3, user, GeneratedStepKind.FOLLOW_UP)
                }
                out += GeneratedComboLine(cardIds = listOf(filler, user), steps = steps)
                if (out.size >= maxLines) return out
            }
        }
        return out
    }
}
