package com.ygochecker.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ComboRecipeTest {
    private val recipe = ComboRecipe(
        id = "kashtira-fenrir-line",
        archetype = "Kashtira",
        title = "Fenrir into Ariseheart",
        starterCardIds = listOf(1, 2),
        steps = listOf(
            RecipeStep(0, 1, "Banish Fenrir from hand, Special Summon it"),
            RecipeStep(1, 2, "Normal Summon, tribute into Ariseheart"),
        ),
    )

    @Test fun `matches when all starter cards are in the pool`() {
        val result = matchRecipes(listOf(1, 2, 3), listOf(recipe))
        assertEquals(listOf(recipe), result)
    }

    @Test fun `does not match when a starter card is missing`() {
        val result = matchRecipes(listOf(1, 3), listOf(recipe))
        assertTrue(result.isEmpty())
    }

    @Test fun `does not match a recipe with no starters`() {
        val empty = recipe.copy(starterCardIds = emptyList())
        val result = matchRecipes(listOf(1, 2), listOf(empty))
        assertTrue(result.isEmpty())
    }

    @Test fun `filters by format when requested`() {
        val edison = recipe.copy(id = "edison-only", format = GameFormat.EDISON)
        val result = matchRecipes(listOf(1, 2), listOf(recipe, edison), format = GameFormat.EDISON)
        assertEquals(listOf(edison), result)
    }
}
