package com.ygochecker.core.model

/**
 * Curated profile avatar monsters (passcode → display hint).
 * Users pick from this list instead of typing passcodes.
 */
object ProfileAvatarPresets {
    data class Entry(val cardId: Int, val labelEn: String)

    val all: List<Entry> = listOf(
        Entry(89631139, "Blue-Eyes White Dragon"),
        Entry(46986414, "Dark Magician"),
        Entry(84012625, "Number 39: Utopia"),
        Entry(44508094, "Stardust Dragon"),
        Entry(93717133, "Galaxy-Eyes Photon Dragon"),
        Entry(65192027, "Black Luster Soldier - Envoy of the Beginning"),
        Entry(88264978, "Judgment Dragon"),
        Entry(74677422, "Red-Eyes Black Dragon"),
        Entry(23995346, "Blue-Eyes Ultimate Dragon"),
        Entry(11082056, "The Winged Dragon of Ra"),
        Entry(10000000, "Obelisk the Tormentor"),
        Entry(10000020, "Slifer the Sky Dragon"),
        Entry(16195942, "Dark Rebellion Xyz Dragon"),
        Entry(73580471, "Black Rose Dragon"),
        Entry(25862681, "Ancient Fairy Dragon"),
        Entry(35952884, "Shooting Quasar Dragon"),
        Entry(99267150, "Five-Headed Dragon"),
        Entry(31876635, "Number 101: Silent Honor ARK"),
        Entry(50321796, "Trishula, Dragon of the Ice Barrier"),
    ).distinctBy { it.cardId }
}
