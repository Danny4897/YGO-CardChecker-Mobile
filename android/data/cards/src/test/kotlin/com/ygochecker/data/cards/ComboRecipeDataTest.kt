package com.ygochecker.data.cards

import com.ygochecker.core.model.GameFormat
import org.junit.Assert.assertEquals
import org.junit.Test

class ComboRecipeDataTest {
    @Test fun `parses a well-formed recipe`() {
        val json = """
            [
              {
                "id": "example-line",
                "archetype": "Generic",
                "title": "Example",
                "starterCardIds": [1, 2],
                "steps": [
                  {"order": 0, "cardId": 1, "action": "Do the thing"},
                  {"order": 1, "cardId": 2, "action": "Do the other thing", "note": "watch missed timing"}
                ],
                "source": "example",
                "format": "HAT"
              }
            ]
        """.trimIndent()
        val recipes = parseRecipes(json)
        assertEquals(1, recipes.size)
        val recipe = recipes.single()
        assertEquals(listOf(1, 2), recipe.starterCardIds)
        assertEquals(2, recipe.steps.size)
        assertEquals(GameFormat.HAT, recipe.format)
        assertEquals("watch missed timing", recipe.steps[1].note)
    }

    @Test fun `skips a recipe with no starters or no steps`() {
        val json = """
            [
              {"id": "no-starters", "archetype": "X", "title": "X", "starterCardIds": [], "steps": [{"order": 0, "cardId": 1, "action": "a"}]},
              {"id": "no-steps", "archetype": "X", "title": "X", "starterCardIds": [1], "steps": []}
            ]
        """.trimIndent()
        assertEquals(0, parseRecipes(json).size)
    }

    @Test fun `defaults format to TCG when absent`() {
        val json = """[{"id": "x", "archetype": "X", "title": "X", "starterCardIds": [1], "steps": [{"order": 0, "cardId": 1, "action": "a"}]}]"""
        assertEquals(GameFormat.TCG, parseRecipes(json).single().format)
    }
}
