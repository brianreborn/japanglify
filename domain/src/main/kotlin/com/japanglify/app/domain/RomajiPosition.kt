package com.japanglify.app.domain

/**
 * Where romaji annotations sit relative to the base Japanese text.
 *
 * [BELOW] is the default: it matches double-sided ruby conventions
 * (furigana over, romaji under) and gives romaji maximum horizontal room,
 * which improves visibility because Latin spellings are typically longer
 * than kana.
 */
enum class RomajiPosition(val id: String, val displayName: String) {
    /** Romaji under the base (max visibility; default). */
    BELOW(id = "below", displayName = "Below base (maximum visibility)"),

    /** Romaji above the base, peer-style with furigana. */
    ABOVE(id = "above", displayName = "Above base"),

    /** Inline after the base, parenthetical style. */
    AFTER(id = "after", displayName = "After base (inline)"),

    /** Inline before the base. */
    BEFORE(id = "before", displayName = "Before base (inline)");

    companion object {
        fun fromId(id: String?): RomajiPosition =
            entries.firstOrNull { it.id == id } ?: BELOW
    }
}
