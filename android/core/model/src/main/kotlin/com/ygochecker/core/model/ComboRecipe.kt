package com.ygochecker.core.model

/**
 * A single written step in a curated combo line — "with this card, do this".
 * Hand-authored, never computed: [ComboRecipe] is a scouting-report entry, not a solver output.
 */
data class RecipeStep(
    val order: Int,
    val cardId: Int,
    /** Free text action, e.g. "Special Summon, then Link off into...". Author's words, not a template key. */
    val action: String,
    val note: String = "",
)

/**
 * A hand-written combo line for a starter (or starter combo). Matched against a deck's card
 * pool by [matchRecipes] — never generated, never optimized. Curated content, credited to
 * [source] (a guide title/URL) or left blank for in-house seeds.
 */
data class ComboRecipe(
    val id: String,
    val archetype: String,
    val title: String,
    /** Cards you need in hand for this line to apply. All must be in the pool for a match. */
    val starterCardIds: List<Int>,
    val steps: List<RecipeStep>,
    val source: String = "",
    val format: GameFormat = GameFormat.TCG,
)

/**
 * Recipes whose full starter combo is covered by [cardIds] (typically the active deck's card
 * ids — matches the SEGOC lesson convention of reasoning over deck composition, not a live
 * drawn hand, since the app has no hand-draw simulator yet).
 */
fun matchRecipes(cardIds: Collection<Int>, recipes: List<ComboRecipe>, format: GameFormat? = null): List<ComboRecipe> {
    val pool = cardIds.toSet()
    return recipes.filter { recipe ->
        recipe.starterCardIds.isNotEmpty() &&
            recipe.starterCardIds.all { it in pool } &&
            (format == null || recipe.format == format)
    }
}
