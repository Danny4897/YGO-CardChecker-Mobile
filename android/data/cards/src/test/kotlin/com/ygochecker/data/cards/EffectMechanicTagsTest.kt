package com.ygochecker.data.cards

import com.ygochecker.core.model.EffectMechanicTags
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EffectMechanicTagsTest {
    @Test
    fun zombieMasterStyleScript_tagsSsFromGyNotSelfRevive() {
        val tags = EffectMechanicTags.fromScriptJson(
            timings = listOf("activate", "special_summon"),
            stepsWhen = listOf("ignition"),
            produces = listOf("gy_body", "ss_from_gy"),
            actions = listOf(
                EffectMechanicTags.ScriptAction("discard", "hand", "gy"),
                EffectMechanicTags.ScriptAction("ss", "gy", "monster", "monster in GY"),
            ),
        )
        assertTrue(tags.contains("ss_from_gy"))
        assertTrue(tags.contains("special_summons"))
        assertTrue(tags.contains("hand_to_gy") || tags.contains("discards"))
        assertFalse(tags.contains("revives_from_gy"))
    }

    @Test
    fun superbiaStyleScript_ssFromGyWithoutGyActivationOrSelfRevive() {
        // Darklord Superbia: when SS'd from GY → SS Fairy from GY (field trigger, not GY effect).
        val tags = EffectMechanicTags.fromScriptJson(
            timings = listOf("special_summon"),
            stepsWhen = listOf("activate"),
            produces = listOf("ss_from_gy"),
            actions = listOf(
                EffectMechanicTags.ScriptAction("ss", "gy", "monster", "Fairy monster"),
            ),
        )
        assertTrue(tags.contains("ss_from_gy"))
        assertTrue(tags.contains("special_summons"))
        assertFalse(tags.contains("revives_from_gy"))
        assertFalse(tags.contains("gy_effect"))
    }

    @Test
    fun plaguespreaderStyleScript_selfReviveFromGy() {
        val tags = EffectMechanicTags.fromScriptJson(
            timings = listOf("gy", "special_summon"),
            stepsWhen = listOf("gy"),
            produces = listOf("revives_from_gy", "gy_effect"),
            actions = listOf(
                EffectMechanicTags.ScriptAction("ss", "gy", "monster", "this card"),
            ),
        )
        assertTrue(tags.contains("gy_effect"))
        assertTrue(tags.contains("revives_from_gy"))
        assertTrue(tags.contains("ss_from_gy"))
        assertTrue(tags.contains("special_summons"))
    }

    @Test
    fun mezukiStyleScript_gySsOtherNotSelfRevive() {
        val tags = EffectMechanicTags.fromScriptJson(
            timings = listOf("gy", "special_summon"),
            stepsWhen = listOf("gy"),
            produces = listOf("ss_from_gy", "gy_effect"),
            actions = listOf(
                EffectMechanicTags.ScriptAction("ss", "gy", "monster", "Zombie monster"),
            ),
        )
        assertTrue(tags.contains("gy_effect"))
        assertTrue(tags.contains("ss_from_gy"))
        assertFalse(tags.contains("revives_from_gy"))
    }

    @Test
    fun relatedTags_whitelistAndLevelChange_skipsRevives() {
        val tags = EffectMechanicTags.fromRelatedTags(
            listOf(
                "gy_effect",
                "format:hat",
                "mention:s_level_becomes_that_monster",
                "searches_deck",
                "revives_from_gy",
            ),
        )
        assertTrue(tags.contains("gy_effect"))
        assertTrue(tags.contains("searches_deck"))
        assertTrue(tags.contains("changes_level"))
        assertFalse(tags.contains("format:hat"))
        assertFalse(tags.contains("revives_from_gy"))
    }

    @Test
    fun displayLabel_capitalizesFirstLetter() {
        assertTrue(EffectMechanicTags.displayLabel("ss_from_gy") == "Ss from gy")
        assertTrue(EffectMechanicTags.displayLabel("revives_from_gy").startsWith("R"))
    }
}
