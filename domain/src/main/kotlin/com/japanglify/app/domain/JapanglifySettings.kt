package com.japanglify.app.domain

import com.japanglify.app.domain.dictionary.PartOfSpeech
import com.japanglify.app.domain.dictionary.SenseSelectionPreset
import com.japanglify.app.domain.dictionary.SenseWeights

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
     * Marks an interlinear ROW where a whole line was elided as redundant
     * rather than shown duplicated or dropped silently — see [ElisionMarker]
     * and [TripleScriptRenderer]'s `buildDisplayLines` doc for the two cases.
     * Named `line`-scoped deliberately to leave room for a future
     * word/cell-scoped elision marker as a sibling field of the same
     * [ElisionMarker] type.
     */
    val lineElisionMarker: ElisionMarker = ElisionMarker.DITTO,
    /**
     * The glyph used when [lineElisionMarker] is [ElisionMarker.CUSTOM] —
     * resolve through [effectiveLineElisionSymbol], which takes just its first
     * Unicode codepoint (a single character, per the write-in's intent) and
     * treats blank as "no marker." Ignored for any non-custom preset.
     */
    val customLineElisionMarker: String = "",
    /**
     * How the interlinear romaji row joins a word to a directly-abutting bound
     * copula/auxiliary (です/だ/ました …) — a middle dot (mora boundary) or a
     * space (word break). See [MoraSeamStyle]. Width-neutral, so it never
     * disturbs column alignment.
     */
    val moraSeamStyle: MoraSeamStyle = MoraSeamStyle.DOT,
    /**
     * Max interlinear line width in **full-width kana units** (one あ ≈ 1).
     * Columns wrap to the next triple-line block when exceeded.
     * Default 14 ≈ a couple of short words plus particles (e.g. 日本語を勉強).
     * Use 0 for no wrap (single long block).
     */
    val maxLineWidthFullwidth: Int = DEFAULT_MAX_LINE_WIDTH_FULLWIDTH,
    /**
     * How many halfwidth (Latin/romaji/PAD) units a fullwidth CJK glyph
     * occupies, for [TripleScriptRenderer.INTERLINEAR]'s plain-text column
     * padding. Default 2 is the traditional "CJK is exactly double-width"
     * assumption, which real host fonts don't always honor -- a host
     * falling back to a non-CJK-aware font for kanji/kana specifically can
     * render them at a meaningfully different ratio, which is what breaks
     * this format's column alignment in practice (see
     * [com.japanglify.app.clipboard.HostFontProfiler], which measures this
     * empirically per host via on-device OCR when available and feeds the
     * result back in here per copy). Never affects [OutputFormat.HTML_RUBY]
     * or the rasterized image output, which measure real glyph widths
     * directly and have no need for this approximation at all.
     */
    val cjkDisplayWidthUnits: Int = DEFAULT_CJK_DISPLAY_WIDTH_UNITS,
    /**
     * Which weighting [com.japanglify.app.domain.dictionary.SenseSelector]
     * uses to pick a headword's gloss among its candidate JMdict senses
     * (see its doc for why "always sense 0" is a poor default). [CUSTOM]
     * reads [customSenseRichnessWeight]/[customSensePositionWeight]/
     * [customSenseDatedWeight] instead of a fixed preset.
     */
    val senseSelectionPreset: SenseSelectionPreset = SenseSelectionPreset.MODERN,
    val customSenseRichnessWeight: Double = SenseSelectionPreset.MODERN.weights!!.richness,
    val customSensePositionWeight: Double = SenseSelectionPreset.MODERN.weights!!.position,
    val customSenseDatedWeight: Double = SenseSelectionPreset.MODERN.weights!!.dated,
    /**
     * Max character length of a word's shown gloss before
     * [com.japanglify.app.domain.dictionary.GlossAnnotator] stops adding
     * more of its '/'-joined synonyms -- found live: with every synonym
     * always shown, a word's own interlinear column had to stretch wide
     * enough to fit its full English gloss underneath, visibly ballooning
     * the gap to the next word for something like "abrupt/sudden/unexpected"
     * even though the Japanese word itself is short. A length budget (not a
     * fixed synonym *count*) means a short, genuinely distinguishing set
     * like さん's "Mr/Mrs/Miss" survives intact -- it easily fits -- while a
     * long same-meaning chain like "do/to carry out/to perform" trims down
     * near-automatically, without needing to special-case which parts of
     * speech are "allowed" more synonyms the way an earlier, count-based
     * version of this had to. The first synonym is always kept in full even
     * if it alone exceeds the budget (never truncated mid-word).
     */
    val maxGlossLength: Int = DEFAULT_MAX_GLOSS_LENGTH,
    /**
     * Which [ImageColorScheme] the rasterized "Copy image" output paints with
     * (its semantic per-role colors — background/base/furigana/romaji/gloss).
     * Affects ONLY the image path; the plain-text interlinear output has no
     * color. Resolve through [effectiveImageColorScheme], not this id
     * directly, so the contrast guarantee is always applied.
     */
    val imageColorSchemeId: String = ImageColorScheme.DEFAULT.id,
    /**
     * Per-role ARGB colors used when [imageColorSchemeId] is
     * [ImageColorScheme.CUSTOM_ID]. Default to [ImageColorScheme.STANDARD]'s
     * colors so a freshly-selected "Custom" starts looking like Standard
     * until edited. Always run through [effectiveImageColorScheme], which
     * applies the same legibility guarantee to custom colors as to presets —
     * so a hand-picked scheme can't be made illegible either.
     */
    val customImageBackgroundColor: Int = ImageColorScheme.STANDARD.color(ImageColorRole.BACKGROUND),
    val customImageBaseColor: Int = ImageColorScheme.STANDARD.color(ImageColorRole.BASE),
    val customImageFuriganaColor: Int = ImageColorScheme.STANDARD.color(ImageColorRole.FURIGANA),
    val customImageRomajiColor: Int = ImageColorScheme.STANDARD.color(ImageColorRole.ROMAJI),
    val customImageGlossColor: Int = ImageColorScheme.STANDARD.color(ImageColorRole.GLOSS)
) {
    /** Resolves [senseSelectionPreset] to actual weights, following [SenseSelectionPreset.CUSTOM] through to this settings object's own fields. */
    val effectiveSenseWeights: SenseWeights
        get() = senseSelectionPreset.weights ?: SenseWeights(
            richness = customSenseRichnessWeight,
            position = customSensePositionWeight,
            dated = customSenseDatedWeight
        )

    /**
     * The chosen image color scheme with its legibility guarantee applied
     * (see [ImageColorScheme.withGuaranteedContrast]) — the value the image
     * renderer should actually paint with.
     */
    val effectiveImageColorScheme: ImageColorScheme
        get() = if (imageColorSchemeId == ImageColorScheme.CUSTOM_ID) {
            ImageColorScheme.custom(
                background = customImageBackgroundColor,
                base = customImageBaseColor,
                furigana = customImageFuriganaColor,
                romaji = customImageRomajiColor,
                gloss = customImageGlossColor
            ).withGuaranteedContrast()
        } else {
            ImageColorScheme.fromId(imageColorSchemeId).withGuaranteedContrast()
        }

    /**
     * The actual elided-row marker glyph to draw, or null for no marker.
     * Resolves [ElisionMarker.CUSTOM] through [customLineElisionMarker] —
     * taking only its first Unicode codepoint so a stray multi-character
     * paste still yields a single-character marker, and treating blank as
     * "none" — while every fixed preset just uses its own [ElisionMarker.symbol].
     */
    val effectiveLineElisionSymbol: String?
        get() = if (lineElisionMarker == ElisionMarker.CUSTOM) {
            customLineElisionMarker.takeIf { it.isNotBlank() }
                ?.let { it.substring(0, Character.charCount(it.codePointAt(0))) }
        } else {
            lineElisionMarker.symbol
        }

    companion object {
        const val DEFAULT_MAX_LINE_WIDTH_FULLWIDTH = 14
        const val DEFAULT_CJK_DISPLAY_WIDTH_UNITS = 2
        /** Comfortably fits "Mr/Mrs/Miss" (11 chars) whole; see [maxGlossLength]. */
        const val DEFAULT_MAX_GLOSS_LENGTH = 12
        val DEFAULT = JapanglifySettings()
    }
}
