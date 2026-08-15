package com.ygochecker.core.model

/**
 * Optional Extra-deck format staples offered by Complete deck.
 * User can pick a subset (or none); only summonable picks are injected.
 */
object FormatExtraStaples {
    data class Entry(
        val name: String,
        /** Compact chip label in the Complete dialog. */
        val shortLabel: String,
        val kind: Kind,
    )

    enum class Kind { XYZ, SYNCHRO, LINK }

    val ALL: List<Entry> = listOf(
        Entry("Number 101: Silent Honor ARK", "101", Kind.XYZ),
        Entry("Evilswarm Exciton Knight", "Exciton", Kind.XYZ),
        Entry("Castel, the Skyblaster Musketeer", "Castel", Kind.XYZ),
        Entry("Abyss Dweller", "Dweller", Kind.XYZ),
        Entry("Tornado Dragon", "Tornado", Kind.XYZ),
        Entry("Number 41: Bagooska the Terribly Tired Tapir", "Bagooska", Kind.XYZ),
        Entry("Divine Arsenal AA-ZEUS - Sky Thunder", "Zeus", Kind.XYZ),
        Entry("Black Rose Dragon", "Black Rose", Kind.SYNCHRO),
        Entry("Ancient Fairy Dragon", "Ancient Fairy", Kind.SYNCHRO),
        Entry("Knightmare Phoenix", "Phoenix", Kind.LINK),
        Entry("Knightmare Unicorn", "Unicorn", Kind.LINK),
        Entry("I:P Masquerena", "I:P", Kind.LINK),
        Entry("Accesscode Talker", "Accesscode", Kind.LINK),
    )

    val defaultNames: Set<String> = ALL.map { it.name }.toSet()
}
