package com.japanglify.app.domain

/**
 * Phoneticization systems for Japanese → Latin script.
 *
 * Hepburn is the international default; Kunrei-shiki is the cabinet-style
 * official system in Japan; Nihon-shiki is the most regular (1:1 mora mapping).
 * Wāpuro mirrors common IME keyboard input spellings.
 */
enum class RomanizationSystem(val id: String, val displayName: String) {
    /** International default (textbooks, dictionaries, most learners). */
    HEPBURN_MODIFIED(
        id = "hepburn_modified",
        displayName = "Modified Hepburn (default)"
    ),
    HEPBURN_TRADITIONAL(
        id = "hepburn_traditional",
        displayName = "Traditional Hepburn"
    ),
    KUNREI(
        id = "kunrei",
        displayName = "Kunrei-shiki"
    ),
    NIHON(
        id = "nihon",
        displayName = "Nihon-shiki"
    ),
    WAPURO(
        id = "wapuro",
        displayName = "Wāpuro (IME)"
    );

    companion object {
        fun fromId(id: String?): RomanizationSystem =
            entries.firstOrNull { it.id == id } ?: HEPBURN_MODIFIED
    }
}
