package com.ygochecker.core.model

/**
 * Derives a card's functional deck-building role(s) from its mechanic tags + printed stats,
 * for every card in the catalog — not just the ones with a curated [FormatCardRole] entry.
 * [FormatCardRole] (hand-authored, format-specific) stays the higher-confidence source where
 * it exists; this classifier fills the gap everywhere else so gap analysis / suggestions work
 * on any archetype, not only HAT.
 *
 * A card can carry more than one role (e.g. a monster can be both SEARCHER and EXTENDER).
 * Deliberately conservative: an unclear card gets no role rather than a guessed one.
 */
object CardRoleClassifier {
    private const val BAIT_MAX_ATK = 1500

    fun classify(card: Card, tags: Collection<String>): Set<HatCardRole> {
        val t = tags.toSet()
        val out = linkedSetOf<HatCardRole>()
        val isMonster = card.type.contains("Monster", ignoreCase = true)
        val isExtra = isExtraDeckType(card.type)

        if ("negates" in t) out += HatCardRole.INTERRUPT
        if ("destroys" in t || "banishes" in t) {
            out += HatCardRole.REMOVAL
            if ("quick_effect" in t) out += HatCardRole.INTERRUPT
        }
        if ("searches_deck" in t) out += HatCardRole.SEARCHER
        val ssFrom = setOf("ss_from_hand", "ss_from_deck", "ss_from_gy", "revives_from_gy")
        if ("special_summons" in t && ssFrom.any { it in t }) out += HatCardRole.EXTENDER
        if ("revives_from_gy" in t) out += HatCardRole.RECYCLE

        if (isMonster && !isExtra) {
            val atk = card.attack
            val isSmallStat = (atk != null && atk in 0..BAIT_MAX_ATK) || card.defense == 0
            if (isSmallStat && ("gy_effect" in t || "destroys" in t || "negates" in t || "searches_deck" in t)) {
                out += HatCardRole.BAIT
            }
            if ("searches_deck" in t || "special_summons" in t || "ss_from_hand" in t) {
                out += HatCardRole.ENGINE_STARTER
            }
        }
        if (isTrapCard(card.type)) out += HatCardRole.TRAP_LINE

        return out
    }
}
