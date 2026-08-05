package com.japanglify.app.domain

/**
 * One annotated span of Japanese text ready for triple-script rendering.
 *
 * @property surface Original characters as they appear in the selection.
 * @property furigana Hiragana reading, or null when none is needed/known
 *   (pure kana already readable, punctuation, Latin, unknown kanji, etc.).
 * @property romaji Latin phoneticization of [furigana] (or of [surface]
 *   when it is already kana), or null when disabled / unavailable.
 * @property needsFurigana True when the surface contains kanji (or mixed
 *   forms) that benefit from a reading annotation.
 */
data class AnnotatedSegment(
    val surface: String,
    val furigana: String? = null,
    val romaji: String? = null,
    val needsFurigana: Boolean = false
) {
    val hasFurigana: Boolean get() = !furigana.isNullOrBlank()
    val hasRomaji: Boolean get() = !romaji.isNullOrBlank()
}
