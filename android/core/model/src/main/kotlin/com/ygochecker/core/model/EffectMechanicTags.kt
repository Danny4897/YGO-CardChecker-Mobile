package com.ygochecker.core.model

/**
 * Mechanic tags aligned with web related.json / effect-script AST
 * (`produces`, timings, action ops) — not deck-building roles (starter/extender).
 *
 * Semantics (MDPro-aligned):
 * - `gy_effect`: effect activates while this card is in the GY (SetRange LOCATION_GRAVE).
 * - `ss_from_gy`: Special Summons a monster from the GY (often another card).
 * - `revives_from_gy`: Special Summons **this** card from the GY (Plaguespreader-style).
 */
object EffectMechanicTags {
    /** Curated chips shown in Search filters (order = UI order). */
    val FILTERABLE: List<String> = listOf(
        "gy_effect",
        "ss_from_gy",
        "ss_from_hand",
        "ss_from_deck",
        "ss_from_extra",
        "special_summons",
        "searches_deck",
        "draw",
        "destroys",
        "banishes",
        "negates",
        "discards",
        "sends_to_gy",
        "hand_to_gy",
        "revives_from_gy",
        "mills",
        "quick_effect",
        "trigger_effect",
        "changes_level",
    )

    private val FILTERABLE_SET = FILTERABLE.toSet()

    private val PRODUCE_ALIASES = mapOf(
        "hand_add" to "searches_deck",
        "gy_body" to "gy_effect",
        "removal" to "destroys",
        "self_ss_from_gy" to "revives_from_gy",
    )

    fun csv(tags: Collection<String>): String =
        tags.map(String::trim).filter(String::isNotEmpty).distinct().sorted().joinToString(",")

    fun parseCsv(csv: String): List<String> =
        csv.split(',').map(String::trim).filter(String::isNotEmpty)

    /**
     * Human label: spaces instead of underscores, first letter capitalized.
     * Prefer localized string resources in UI when available.
     */
    fun displayLabel(tag: String): String {
        val spaced = tag.trim().replace('_', ' ')
        if (spaced.isEmpty()) return spaced
        return spaced.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
    }

    /** Tags from a full effect-script object (HAT asset or remote scripts.json). */
    fun fromScriptJson(
        timings: List<String>,
        stepsWhen: List<String>,
        produces: List<String>,
        actions: List<ScriptAction>,
    ): Set<String> {
        val out = linkedSetOf<String>()
        var gyActivation = false
        for (t in timings) {
            when (t.lowercase()) {
                "gy" -> {
                    out += "gy_effect"
                    gyActivation = true
                }
                "special_summon" -> out += "special_summons"
                "quick" -> out += "quick_effect"
                "trigger" -> out += "trigger_effect"
            }
        }
        for (w in stepsWhen) {
            val lower = w.lowercase()
            if (lower == "gy" || lower == "grave" || lower == "graveyard") {
                out += "gy_effect"
                gyActivation = true
            }
            if (lower.contains("special_summon") || lower.contains("_ss")) out += "special_summons"
            if (lower.contains("quick")) out += "quick_effect"
            if (lower.contains("trigger")) out += "trigger_effect"
        }
        for (p in produces) {
            val key = p.trim()
            if (key in FILTERABLE_SET) out += key
            PRODUCE_ALIASES[key]?.let { out += it }
            if (key == "ss_from_gy" || key == "revives_from_gy") {
                out += "special_summons"
            }
            // Do NOT map ss_from_gy → revives_from_gy (Superbia vs Plaguespreader).
        }
        for (a in actions) {
            val op = a.op.lowercase()
            val from = a.from.lowercase()
            when (op) {
                "ss" -> {
                    out += "special_summons"
                    when (from) {
                        "gy" -> {
                            out += "ss_from_gy"
                            if (isSelfSummonFilter(a.filter) ||
                                (gyActivation && isLikelySelfRevive(a.filter, produces))
                            ) {
                                out += "revives_from_gy"
                            }
                        }
                        "hand" -> out += "ss_from_hand"
                        "deck" -> out += "ss_from_deck"
                        "extra", "extra_deck" -> out += "ss_from_extra"
                    }
                }
                "search", "add" -> out += "searches_deck"
                "draw" -> out += "draw"
                "destroy" -> out += "destroys"
                "banish" -> out += "banishes"
                "negate" -> out += "negates"
                "discard" -> {
                    out += "discards"
                    if (a.to.lowercase() == "gy" || from == "hand") out += "hand_to_gy"
                }
            }
            if (a.to.lowercase() == "gy" || (from == "gy" && op != "ss")) {
                out += "sends_to_gy"
            }
            // Being in GY as a location for non-SS actions ≠ GY activation by itself.
        }
        return out.filter { it in FILTERABLE_SET }.toSet()
    }

    /**
     * Whitelist + level-change heuristics from related.json entry tags.
     * `revives_from_gy` is script/MDPro-only — related text tags conflate it with `ss_from_gy`.
     */
    fun fromRelatedTags(tags: Collection<String>): Set<String> {
        val out = linkedSetOf<String>()
        for (raw in tags) {
            val t = raw.trim()
            if (t == "revives_from_gy") continue
            if (t in FILTERABLE_SET) out += t
            val lower = t.lowercase()
            if (lower.startsWith("mention:") && (
                    lower.contains("level_become") ||
                        lower.contains("s_level") ||
                        lower.contains("level_by") ||
                        lower.contains("level_is_increased") ||
                        lower.contains("attribute_and_level")
                    )
            ) {
                out += "changes_level"
            }
        }
        return out
    }

    private fun isSelfSummonFilter(filter: String): Boolean {
        val f = filter.lowercase()
        if (f.isBlank()) return false
        return f.contains("this card") ||
            f.contains("this monster") ||
            f == "self" ||
            f.contains("handler")
    }

    /**
     * Legacy HAT scripts sometimes used `gy_body` + `ss_from_gy` for self-revive without
     * filter "this card". Prefer race/type filters as SS-other.
     */
    private fun isLikelySelfRevive(filter: String, produces: List<String>): Boolean {
        if (produces.any { it == "revives_from_gy" || it == "self_ss_from_gy" }) return true
        val f = filter.lowercase().trim()
        if (isSelfSummonFilter(f)) return true
        // Explicit other-monster filters → not self revive.
        if (f.contains("monster") && !f.contains("this")) return false
        if (f.contains("fairy") || f.contains("zombie") || f.contains("dragon")) return false
        return false
    }

    data class ScriptAction(
        val op: String,
        val from: String = "",
        val to: String = "",
        val filter: String = "",
    )
}
