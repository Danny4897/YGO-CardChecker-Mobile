package com.ygochecker.core.model

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EffectTextProfilerTest {
    @Test
    fun stratos_profilesAsHeroStarterSearch() {
        val card = Card(
            id = 1,
            name = "Elemental HERO Stratos",
            type = "Effect Monster",
            description = "If this card is Normal or Special Summoned: You can add 1 \"HERO\" monster from your Deck to your hand.",
        )
        val p = EffectTextProfiler.profile(card)
        assertTrue(p.archetypes.contains("hero"))
        assertTrue(p.tags.contains("searches_deck"))
        assertTrue(p.roles.contains("starter") || p.roles.contains("extender"))
        assertTrue(p.packagePartners.any { it.contains("hero") })
    }

    @Test
    fun junkWarrior_requiresTuner() {
        val card = Card(
            id = 2,
            name = "Junk Warrior",
            type = "Synchro Monster",
            description = "\"Junk Synchron\" + 1 or more non-Tuner monsters",
        )
        val p = EffectTextProfiler.profile(card)
        assertTrue(p.isSynchroMonster)
        assertTrue(p.requiresTuner)
        assertTrue(p.isExtraMonster)
    }

    @Test
    fun armityle_requiresNamedMaterials() {
        val card = Card(
            id = 9,
            name = "Armityle the Chaos Phantom",
            type = "Fusion Monster",
            description = "\"Uria, Lord of Searing Flames\" + \"Hamon, Lord of Striking Thunder\" + \"Raviel, Lord of Phantasms\"",
        )
        val p = EffectTextProfiler.profile(card)
        assertTrue(p.requiresNamedMaterials)
        assertTrue(p.namedMaterials.size >= 3)
    }

    @Test
    fun darkRulerDeal_listsBerserkPartner() {
        val card = Card(
            id = 3,
            name = "A Deal with Dark Ruler",
            type = "Quick-Play Spell",
            description = "Activate only when you Special Summon \"Dark Ruler Ha Des\".",
        )
        val p = EffectTextProfiler.profile(card)
        assertTrue(p.packagePartners.any { it.contains("berserk") || it.contains("dark ruler") })
        assertFalse(p.isTuner)
    }

    @Test
    fun zombieWorld_extractsZombieRaceAndTypeChange() {
        val card = Card(
            id = 4064256,
            name = "Zombie World",
            type = "Spell Card",
            description = "All monsters on the field and in the GYs become Zombie monsters. " +
                "Neither player can Tribute Summon monsters, except Zombie monsters.",
        )
        val p = EffectTextProfiler.profile(card)
        assertTrue(p.mentionedRaces.contains("Zombie"))
        assertTrue(p.archetypes.contains("zombie"))
        assertTrue(p.isTypeChangeField)
        assertTrue(p.packagePartners.any { it.contains("paladin") || it.contains("plaguespreader") })
    }

    @Test
    fun paladin_mentionsZombieRace() {
        val card = Card(
            id = 68670547,
            name = "Paladin of the Cursed Dragon",
            type = "Effect Monster",
            race = "Zombie",
            description = "Once per turn: You can target 1 Level 4 or lower Zombie monster in your opponent's GY, " +
                "that was destroyed by battle; Special Summon that target.",
        )
        val p = EffectTextProfiler.profile(card)
        assertTrue(p.mentionedRaces.contains("Zombie"))
        assertTrue(p.tags.contains("ss_from_gy") || p.tags.contains("special_summons"))
    }
}
