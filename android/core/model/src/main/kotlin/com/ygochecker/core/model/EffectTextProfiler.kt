package com.ygochecker.core.model

/**
 * Derives mechanic tags, roles, race/type mentions, and structural needs from
 * printed card text when LUA/AST scripts are thin or missing.
 * Used by Complete-deck + SynergyEnrichment for every card.
 */
object EffectTextProfiler {
    data class Profile(
        val tags: Set<String>,
        val roles: Set<String>,
        /** Tokens like "hero", "lightsworn", "zombie" for deck affinity. */
        val archetypes: Set<String>,
        /**
         * Monster races referenced by name or effect text (e.g. Zombie World → Zombie).
         * Canonical YGO race strings as stored on cards ("Zombie", "Dragon", …).
         */
        val mentionedRaces: Set<String>,
        val isTuner: Boolean,
        val isSynchroMonster: Boolean,
        val isExtraMonster: Boolean,
        /** Extra Synchro that needs Tuners in Main. */
        val requiresTuner: Boolean,
        /** Named partners this card is incomplete without (lowercase names). */
        val packagePartners: Set<String>,
        /** Soft score weight for completion (higher = more central). */
        val engineWeight: Double,
        /** Field/Continuous that retypes the board (Zombie World, DNA Surgery…). */
        val isTypeChangeField: Boolean,
        /**
         * Named fusion/synchro materials quoted in text (e.g. "Uria" + "Hamon" + "Raviel").
         * Extra bosses that list these should not enter a deck missing them.
         */
        val namedMaterials: Set<String> = emptySet(),
        /** True when Extra summon needs those named materials (Fusion contact / locked). */
        val requiresNamedMaterials: Boolean = false,
    )

    /** Official-ish race labels (longest first for matching "Beast-Warrior" before "Beast"). */
    val MONSTER_RACES: List<String> = listOf(
        "Beast-Warrior", "Winged Beast", "Sea Serpent", "Divine-Beast", "Creator God",
        "Spellcaster", "Thunder", "Dinosaur", "Warrior", "Psychic", "Machine",
        "Insect", "Dragon", "Zombie", "Fiend", "Fairy", "Beast", "Plant", "Rock",
        "Reptile", "Pyro", "Aqua", "Fish", "Wyrm", "Cyberse",
    )

    private val ARCHETYPE_NEEDLES = listOf(
        "elemental hero" to "hero",
        "destiny hero" to "hero",
        "masked hero" to "hero",
        "vision hero" to "hero",
        "evil hero" to "hero",
        "hero" to "hero",
        "lightsworn" to "lightsworn",
        "dragon ruler" to "dragon_ruler",
        "tellarknight" to "tellarknight",
        "harpie" to "harpie",
        "bujin" to "bujin",
        "fire fist" to "fire_fist",
        "mermail" to "mermail",
        "atlantean" to "atlantean",
        "madolche" to "madolche",
        "ghostrick" to "ghostrick",
        "battlin' boxer" to "battlin_boxer",
        "geargia" to "geargia",
        "inzektor" to "inzektor",
        "lswarm" to "lswarm",
        "constellar" to "constellar",
        "evilswarm" to "evilswarm",
        "vampire" to "vampire",
        "gravekeeper" to "gravekeeper",
        "dark world" to "dark_world",
        "blackwing" to "blackwing",
        "six samurai" to "six_samurai",
        "gladiator beast" to "gladiator_beast",
        "zombie" to "zombie",
        "light and darkness dragon" to "chaos",
        "chaos" to "chaos",
    )

    private val PACKAGE_PAIRS = listOf(
        "a deal with dark ruler" to listOf("berserk mode", "dark ruler ha des"),
        "berserk mode" to listOf("a deal with dark ruler", "dark ruler ha des"),
        "dark ruler ha des" to listOf("berserk mode", "a deal with dark ruler"),
        "zombie world" to listOf("paladin of the cursed dragon", "doomkaiser dragon", "plaguespreader zombie"),
        "paladin of the cursed dragon" to listOf("zombie world"),
    )

    fun profile(card: Card): Profile {
        val type = card.type
        val name = card.name
        val desc = card.description
        val blob = "$name\n$type\n$desc"
        val lower = blob.lowercase()
        val descLower = desc.lowercase()
        val nameLower = name.lowercase()

        val tags = linkedSetOf<String>()
        fun hit(vararg keys: String, tag: String) {
            if (keys.any { lower.contains(it) }) tags += tag
        }

        hit("from your graveyard", "from the graveyard", "in your graveyard", "in the gys", "in the gy", tag = "gy_effect")
        hit("special summon", "special summons", tag = "special_summons")
        if (Regex(
                """special summon.{0,40}(graveyard|\bgy\b)|(graveyard|\bgy\b).{0,40}special summon""",
                RegexOption.IGNORE_CASE,
            ).containsMatchIn(desc)
        ) {
            tags += "ss_from_gy"
            tags += "special_summons"
        }
        if (Regex(
                """special summon.{0,50}(this card|it).{0,30}(graveyard|\bgy\b)|(graveyard|\bgy\b).{0,40}special summon.{0,30}(this card|it)""",
                RegexOption.IGNORE_CASE,
            ).containsMatchIn(desc)
        ) {
            tags += "revives_from_gy"
            tags += "ss_from_gy"
            tags += "special_summons"
        }
        hit("from your hand", tag = "ss_from_hand")
        if (lower.contains("special summon") && lower.contains("hand")) tags += "ss_from_hand"
        hit("from your deck", tag = "ss_from_deck")
        if (lower.contains("special summon") && lower.contains("deck") && !lower.contains("extra deck")) {
            tags += "ss_from_deck"
        }
        hit("from your extra deck", tag = "ss_from_extra")
        if (lower.contains("add") && lower.contains("deck") && !lower.contains("extra deck")) {
            tags += "searches_deck"
        }
        hit("search your deck", "search your", tag = "searches_deck")
        hit("draw", "draws", tag = "draw")
        hit("destroy", "destroys", tag = "destroys")
        hit("banish", "banishes", "remove from play", tag = "banishes")
        hit("negate", "negates", tag = "negates")
        hit("discard", "discards", tag = "discards")
        if (lower.contains("send") && (lower.contains("graveyard") || lower.contains(" gy"))) {
            tags += "sends_to_gy"
        }
        hit("mill", "send the top", "excavates", tag = "mills")
        hit("during either player's", "quick effect", "quick-play", tag = "quick_effect")
        hit("when this card", "if this card", tag = "trigger_effect")
        if (lower.contains("level") && (
                lower.contains("become") || lower.contains("increase") || lower.contains("decrease")
                )
        ) {
            tags += "changes_level"
        }
        if (lower.contains("discard") && (lower.contains("hand") || lower.contains("graveyard"))) {
            tags += "hand_to_gy"
        }

        val isTuner = type.contains("Tuner", ignoreCase = true)
        val isExtra = isExtraDeckType(type)
        val isSynchro = type.contains("Synchro", ignoreCase = true)
        val requiresTuner = isSynchro || (
            isExtra && lower.contains("tuner") && (
                lower.contains("1 tuner") ||
                    lower.contains("tuner +") ||
                    lower.contains("non-tuner")
                )
            )

        val mentionedRaces = linkedSetOf<String>()
        card.race?.trim()?.takeIf { it.isNotEmpty() }?.let { mentionedRaces += it }
        for (race in MONSTER_RACES) {
            val needle = race.lowercase()
            if (nameLower.contains(needle) || descLower.contains(needle)) {
                mentionedRaces += race
            }
        }

        val isTypeChangeField = Regex(
            """become[s]?\s+[a-z\- ]*zombie|treated as .{0,20}zombie|become[s]? .{0,30}monsters""",
            RegexOption.IGNORE_CASE,
        ).containsMatchIn(desc) || (
            type.contains("Spell", ignoreCase = true) &&
                Regex("""become[s]?\s""", RegexOption.IGNORE_CASE).containsMatchIn(desc) &&
                mentionedRaces.isNotEmpty()
            )

        val roles = linkedSetOf<String>()
        when {
            tags.contains("searches_deck") || tags.contains("ss_from_deck") -> roles += "starter"
            tags.contains("ss_from_hand") || tags.contains("ss_from_gy") || tags.contains("revives_from_gy") ->
                roles += "extender"
            tags.contains("negates") && tags.contains("quick_effect") -> roles += "handtrap"
            tags.contains("destroys") || tags.contains("negates") -> roles += "board_breaker"
            isTypeChangeField -> roles += "engine"
            isExtra -> roles += "engine"
            else -> roles += "tech"
        }
        if (isTuner) roles += "extender"

        val archetypes = linkedSetOf<String>()
        for ((needle, token) in ARCHETYPE_NEEDLES) {
            if (nameLower.contains(needle) || (needle.length > 5 && descLower.contains(needle))) {
                archetypes += token
            }
        }
        if (nameLower.contains("hero") || Regex("""\bHERO\b""").containsMatchIn(desc)) {
            archetypes += "hero"
        }
        if ("Zombie" in mentionedRaces) archetypes += "zombie"

        val packagePartners = linkedSetOf<String>()
        for ((key, partners) in PACKAGE_PAIRS) {
            if (nameLower.contains(key)) packagePartners += partners
        }
        val quotedNames = linkedSetOf<String>()
        Regex("\"([^\"]{3,60})\"").findAll(desc).forEach { m ->
            val q = m.groupValues[1].lowercase()
            packagePartners += q
            quotedNames += q
        }

        // Fusion/Synchro lines with 2+ quoted names ≈ locked materials (Armityle, Flame Wingman…).
        val hasMaterialPlusLine = Regex("\"[^\"]{2,}\"\\s*\\+\\s*\"[^\"]{2,}\"").containsMatchIn(desc)
        val namedMaterials = if (
            isExtra &&
            quotedNames.size >= 2 &&
            (
                type.contains("Fusion", ignoreCase = true) ||
                    type.contains("Synchro", ignoreCase = true) ||
                    descLower.contains("fusion material") ||
                    descLower.contains("fusion summon") ||
                    hasMaterialPlusLine
                )
        ) {
            quotedNames
        } else {
            emptySet()
        }
        val requiresNamedMaterials = namedMaterials.isNotEmpty() ||
            (
                isExtra && type.contains("Fusion", ignoreCase = true) && (
                    descLower.contains("fusion summoned with the above") ||
                        descLower.contains("contact fusion") ||
                        descLower.contains("must be fusion summoned") ||
                        descLower.contains("must first be special summoned") && hasMaterialPlusLine
                    )
                )

        val engineWeight = when {
            isTypeChangeField -> 1.55
            roles.contains("starter") -> 1.35
            roles.contains("extender") -> 1.2
            isExtra -> 1.25
            roles.contains("handtrap") -> 0.85
            else -> 1.0
        }

        return Profile(
            tags = tags.filter { it in EffectMechanicTags.FILTERABLE.toSet() }.toSet(),
            roles = roles,
            archetypes = archetypes,
            mentionedRaces = mentionedRaces,
            isTuner = isTuner,
            isSynchroMonster = isSynchro,
            isExtraMonster = isExtra,
            requiresTuner = requiresTuner,
            packagePartners = packagePartners,
            engineWeight = engineWeight,
            isTypeChangeField = isTypeChangeField,
            namedMaterials = namedMaterials,
            requiresNamedMaterials = requiresNamedMaterials,
        )
    }

    fun mergeTags(scriptTags: Collection<String>, textTags: Collection<String>): List<String> =
        EffectMechanicTags.csv((scriptTags + textTags).toSet()).let(EffectMechanicTags::parseCsv)

    fun mergeRoles(scriptRoles: Collection<String>, textRoles: Collection<String>): List<String> =
        (scriptRoles.map { it.trim().lowercase() }.filter { it.isNotEmpty() } +
            textRoles.map { it.trim().lowercase() })
            .distinct()
            .sorted()
}
