package com.ygochecker.data.cards

import android.content.Context
import com.ygochecker.core.domain.ComboRecipeRepository
import com.ygochecker.core.model.ComboRecipe
import com.ygochecker.core.model.GameFormat
import com.ygochecker.core.model.RecipeStep
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Loads hand-authored combo recipes from the bundled `combo-recipes/recipes.json` asset.
 * Never fetched remotely, never computed — a curated seed the app ships with; see
 * `docs/superpowers/specs/2026-08-22-segoc-tmm-field-design.md` for why (auto-solve is
 * intractable: optional effects, targets, hidden opponent information).
 */
@Singleton
class AssetComboRecipeRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) : ComboRecipeRepository {
    private val mutex = Mutex()
    private var cached: List<ComboRecipe>? = null

    override suspend fun all(): List<ComboRecipe> = mutex.withLock {
        cached ?: load().also { cached = it }
    }

    private fun load(): List<ComboRecipe> = try {
        val json = context.assets.open("combo-recipes/recipes.json").bufferedReader().use { it.readText() }
        parseRecipes(json)
    } catch (_: Exception) {
        emptyList()
    }
}

internal fun parseRecipes(json: String): List<ComboRecipe> {
    val array = JSONArray(json)
    val out = mutableListOf<ComboRecipe>()
    for (i in 0 until array.length()) {
        val obj = array.optJSONObject(i) ?: continue
        val starters = obj.optJSONArray("starterCardIds")?.toIntList().orEmpty()
        val steps = obj.optJSONArray("steps")?.toStepList().orEmpty()
        if (starters.isEmpty() || steps.isEmpty()) continue
        out += ComboRecipe(
            id = obj.optString("id"),
            archetype = obj.optString("archetype"),
            title = obj.optString("title"),
            starterCardIds = starters,
            steps = steps,
            source = obj.optString("source", ""),
            format = runCatching { GameFormat.valueOf(obj.optString("format", "TCG")) }.getOrDefault(GameFormat.TCG),
        )
    }
    return out
}

private fun JSONArray.toIntList(): List<Int> = (0 until length()).map { getInt(it) }

private fun JSONArray.toStepList(): List<RecipeStep> = (0 until length()).mapNotNull { i ->
    val obj = optJSONObject(i) ?: return@mapNotNull null
    RecipeStep(
        order = obj.optInt("order", i),
        cardId = obj.optInt("cardId"),
        action = obj.optString("action"),
        note = obj.optString("note", ""),
    )
}
