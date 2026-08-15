package com.ygochecker.core.model

/**
 * Stylized profile emblems — original silhouettes, not Konami card art.
 * Negative IDs are reserved for these presets (not YGOPRODeck passcodes).
 */
object ProfileAvatarPresets {
    enum class Style {
        AZURE_DRAKE,
        ARCANE_ORB,
        STAR_DUELIST,
        ROSE_KNIGHT,
        VOID_WARRIOR,
        GOLDEN_SHIELD,
        PHOENIX_CREST,
        ICE_SERPENT,
    }

    data class Entry(
        val id: Int,
        val labelEn: String,
        val style: Style,
    )

    val all: List<Entry> = listOf(
        Entry(-101, "Azure Drake", Style.AZURE_DRAKE),
        Entry(-102, "Arcane Orb", Style.ARCANE_ORB),
        Entry(-103, "Star Duelist", Style.STAR_DUELIST),
        Entry(-104, "Rose Knight", Style.ROSE_KNIGHT),
        Entry(-105, "Void Warrior", Style.VOID_WARRIOR),
        Entry(-106, "Golden Shield", Style.GOLDEN_SHIELD),
        Entry(-107, "Phoenix Crest", Style.PHOENIX_CREST),
        Entry(-108, "Ice Serpent", Style.ICE_SERPENT),
    )

    fun isEmblem(id: Int?): Boolean = id != null && id < 0

    fun entryFor(id: Int?): Entry? = id?.let { want -> all.firstOrNull { it.id == want } }
}
