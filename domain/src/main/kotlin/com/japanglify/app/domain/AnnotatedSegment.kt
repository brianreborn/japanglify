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
 * @property isBoundToPrevious True for a conjugation ending / auxiliary verb
 *   (e.g. ました, ない) that completes the previous token's inflected word
 *   rather than being its own word or particle — these render flush against
 *   the previous cell instead of getting a word-gap.
 * @property isParticle True for a grammatical particle (は/を/の/に/…). These
 *   still get their own word-gap (they mark a real word boundary), but — like
 *   real Japanese typesetting convention — must never be the first thing on a
 *   wrapped line: a lone は dangling at a line start reads as a mistake, not
 *   a sentence continuing.
 * @property romajiSyllables Same phoneticization as [romaji], but with a
 *   hyphen at each mora boundary (see [Romanizer.romanizeSyllables]) — null
 *   unless populated by a mora-aware caller. Used only for the interlinear
 *   romaji row so a multi-kanji word's reading isn't one unbroken run
 *   hiding which part matches which kana/kanji; every other renderer keeps
 *   using [romaji] as-is.
 * @property gloss English dictionary gloss (e.g. "n. paper"), formatted
 *   ready to render by [com.japanglify.app.domain.dictionary.GlossAnnotator]
 *   — null when glosses are off, no dictionary is downloaded, no entry was
 *   found for this token, or [emoji] made it redundant (see [emoji]).
 * @property emoji A single, precise emoji for this token's gloss (see
 *   [com.japanglify.app.domain.emoji.EmojiAnnotator]) — null when the
 *   emoji feature is off, no emoji data is downloaded, or no unique match
 *   was found. When non-null and elision applies (the "always show both"
 *   setting is off), [gloss] is left null instead of the now-redundant
 *   English word — [emoji] is shown on its own line either way.
 */
data class AnnotatedSegment(
    val surface: String,
    val furigana: String? = null,
    val romaji: String? = null,
    val needsFurigana: Boolean = false,
    val isBoundToPrevious: Boolean = false,
    val isParticle: Boolean = false,
    val romajiSyllables: String? = null,
    val gloss: String? = null,
    val emoji: String? = null
) {
    val hasFurigana: Boolean get() = !furigana.isNullOrBlank()
    val hasRomaji: Boolean get() = !romaji.isNullOrBlank()
    val hasGloss: Boolean get() = !gloss.isNullOrBlank()
    val hasEmoji: Boolean get() = !emoji.isNullOrBlank()
}
