package com.japanglify.app.domain

import com.japanglify.app.domain.dictionary.PartOfSpeech

/**
 * Immutable snapshot of user preferences that drive annotation + rendering.
 * Loaded from SharedPreferences on the settings screen; applied silently
 * during PROCESS_TEXT handling.
 */
data class JapanglifySettings(
    /** Modified Hepburn — most widely used romaji system internationally. */
    val romanizationSystem: RomanizationSystem = RomanizationSystem.HEPBURN_MODIFIED,
    val romajiPosition: RomajiPosition = RomajiPosition.BELOW,
    /** Three-line furigana / base / romaji layout. */
    val outputFormat: OutputFormat = OutputFormat.INTERLINEAR,
    val writingOrientation: WritingOrientation = WritingOrientation.HORIZONTAL,
    val includeFurigana: Boolean = true,
    val includeRomaji: Boolean = true,
    /**
     * Word/particle English glosses via a downloaded dictionary (see
     * [com.japanglify.app.domain.dictionary.GlossAnnotator]). Off by
     * default — no dictionary is bundled, this is purely opt-in.
     */
    val includeGlosses: Boolean = false,
    /**
     * Optional English→emoji annotation layered on top of [includeGlosses]
     * (see [com.japanglify.app.domain.emoji.EmojiAnnotator]) — has no
     * effect unless glosses are also on, since it matches against a
     * token's already-resolved gloss text, not the Japanese word itself.
     */
    val includeEmoji: Boolean = false,
    /** Skip eliding the English gloss word when a precise emoji match is found — show both. */
    val emojiAlwaysShowBoth: Boolean = false,
    /** Which parts of speech are eligible for emoji lookup. Default: all (user's explicit choice). */
    val emojiPosScope: Set<PartOfSpeech> = PartOfSpeech.entries.toSet(),
    val emojiPrecisionTier: EmojiPrecisionTier = EmojiPrecisionTier.STRICT,
    /** When true, attach furigana only to spans that contain kanji. */
    val furiganaKanjiOnly: Boolean = true,
    /** Capitalize the first letter of each romaji word/segment. */
    val capitalizeRomaji: Boolean = false,
    /** How punctuation is mirrored onto the furigana row (default: not at all). */
    val furiganaPunctuationStyle: FuriganaPunctuationStyle = FuriganaPunctuationStyle.NONE,
    /**
     * Max interlinear line width in **full-width kana units** (one あ ≈ 1).
     * Columns wrap to the next triple-line block when exceeded.
     * Default 14 ≈ a couple of short words plus particles (e.g. 日本語を勉強).
     * Use 0 for no wrap (single long block).
     */
    val maxLineWidthFullwidth: Int = DEFAULT_MAX_LINE_WIDTH_FULLWIDTH
) {
    companion object {
        const val DEFAULT_MAX_LINE_WIDTH_FULLWIDTH = 14
        val DEFAULT = JapanglifySettings()
    }
}
