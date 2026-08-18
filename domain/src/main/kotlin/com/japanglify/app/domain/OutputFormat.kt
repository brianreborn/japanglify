package com.japanglify.app.domain

/**
 * How the three scripts (base, furigana, romaji) are serialized for
 * process-text replacement / clipboard output.
 *
 * Plain-text hosts (Discord, X, …) cannot render true CSS ruby “small kana
 * above kanji”. Unicode has no full small-hiragana alphabet for that either.
 * [FURIGANA_INLINE] (Aozora-style 《》) is the most readable plain-text
 * furigana convention; [INTERLINEAR] uses a compact halfwidth top line.
 */
enum class OutputFormat(val id: String, val displayName: String) {
    /**
     * Plain-text furigana (Aozora-style) + romaji line — best for Discord/chat:
     * `日本語《にほんご》を勉強《べんきょう》する`
     * `nihongo o benkyou suru`
     */
    FURIGANA_INLINE(
        id = "furigana_inline",
        displayName = "Furigana 《よみ》 + romaji (readable)"
    ),

    /**
     * Educational / dictionary plain-text convention:
     * `漢字（かんじ / kanji）`
     */
    PARENTHETICAL(
        id = "parenthetical",
        displayName = "Parenthetical (漢字（かんじ / kanji）)"
    ),

    /**
     * Three-line block: compact halfwidth readings over base, romaji under.
     * Closest plain-text “ruby row” look; uses Discord-safe blank padding.
     */
    INTERLINEAR(
        id = "interlinear",
        displayName = "Interlinear (compact readings)"
    ),

    /**
     * HTML fragment: furigana in `<ruby><rt>`, romaji (and gloss/emoji)
     * as smaller stacked block `<span>`s. Not `<rb>`/`<rtc>` — those
     * were dropped from HTML and do not position in current browsers.
     * Only useful in HTML hosts — Discord/X will show raw tags.
     */
    HTML_RUBY(
        id = "html_ruby",
        displayName = "HTML ruby (browsers only)"
    ),

    /**
     * Compact brackets:
     * `漢字〔かんじ〕[kanji]`
     */
    COMPACT(
        id = "compact",
        displayName = "Compact brackets"
    );

    companion object {
        fun fromId(id: String?): OutputFormat =
            entries.firstOrNull { it.id == id } ?: INTERLINEAR
    }
}
