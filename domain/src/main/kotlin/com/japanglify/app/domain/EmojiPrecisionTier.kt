package com.japanglify.app.domain

/**
 * How strict a match against CLDR's emoji names has to be before the
 * optional English→emoji annotation fires. [STRICT] (exact match against
 * an emoji's single canonical short name, unique across the whole dataset)
 * always applies first. [MEDIUM] additionally falls back to a real WordNet
 * synonym expansion (e.g. "automobile" matching via its synonym "car") when
 * STRICT finds no match -- requires the separate "Synonyms (English)"
 * download; see [com.japanglify.app.domain.emoji.EmojiAnnotator] and
 * `SqliteEmojiProvider`. CLDR's own broader keyword-list annotations were
 * tried for this first and abandoned: CLDR assigns a `tts` to virtually
 * every emoji already, so a keyword-derived candidate almost always
 * collides with an emoji some other word already owns via STRICT, and "no
 * symbol reused for a different word" correctly drops it -- verified live,
 * real coverage was ~0. [LOOSE] is named and selectable now so the
 * setting's shape is stable, but currently behaves identically to [STRICT]
 * — a named backlog item, not an oversight.
 */
enum class EmojiPrecisionTier(val id: String, val displayName: String) {
    STRICT(id = "strict", displayName = "Strict (exact match only)"),
    MEDIUM(id = "medium", displayName = "Medium (also matches real synonyms)"),
    LOOSE(id = "loose", displayName = "Loose (behaves like Strict for now)");

    companion object {
        fun fromId(id: String?): EmojiPrecisionTier =
            entries.firstOrNull { it.id == id } ?: STRICT
    }
}
