package com.japanglify.app.domain

/**
 * Turns raw selected text into a list of [AnnotatedSegment]s carrying
 * surface form, hiragana furigana, and romaji.
 *
 * Uses an optional [ReadingProvider] (typically Kuromoji on device) for
 * kanji readings. When no provider is available, pure-kana text is still
 * fully annotated; kanji is left without furigana.
 */
class JapaneseAnalyzer(
    private val readingProvider: ReadingProvider? = null
) {

    fun interface ReadingProvider {
        /**
         * Tokenize [text] into surface/reading pairs.
         * [reading] should be katakana or hiragana; use null/"*" for unknown.
         */
        fun tokenize(text: String): List<SurfaceReading>
    }

    data class SurfaceReading(
        val surface: String,
        val reading: String?,
        /** True for an auxiliary-verb/conjugation-ending token (e.g. ました, ない). */
        val isBoundToPrevious: Boolean = false,
        /** True for a grammatical particle (は/を/の/に/…). */
        val isParticle: Boolean = false
    )

    fun annotate(text: String, settings: JapanglifySettings): List<AnnotatedSegment> {
        if (text.isEmpty()) return emptyList()

        val romanizer = Romanizer(settings.romanizationSystem)
        val tokens = readingProvider?.tokenize(text) ?: fallbackTokenize(text)

        return tokens.map { token -> toSegment(token, settings, romanizer) }
    }

    private fun toSegment(
        token: SurfaceReading,
        settings: JapanglifySettings,
        romanizer: Romanizer
    ): AnnotatedSegment {
        val surface = token.surface

        // Punctuation / symbols: keep on base, map to Latin on the romaji line
        if (KanaConverter.isMostlyPunctuation(surface)) {
            val roma = if (settings.includeRomaji) {
                KanaConverter.punctuationToRomaji(surface).ifBlank { surface }
            } else null
            // Mirror punctuation on the furigana row so interlinear columns stay
            // furigana-style (ruby row carries the same mark above the base).
            val furi = if (settings.includeFurigana) surface else null
            return AnnotatedSegment(
                surface = surface,
                furigana = furi,
                romaji = roma,
                needsFurigana = false,
                isBoundToPrevious = token.isBoundToPrevious,
                isParticle = token.isParticle
            )
        }

        val hasKanji = KanaConverter.containsKanji(surface)
        val rawReading = token.reading
            ?.takeIf { KanaConverter.isValidReading(it) }
            ?.let { KanaConverter.toHiragana(it) }

        // Prefer provider reading; for pure kana use the surface itself.
        val readingHira: String? = when {
            rawReading != null -> rawReading
            KanaConverter.isMostlyKana(surface) ->
                KanaConverter.toHiragana(surface.filter {
                    KanaConverter.isKana(it) || it == 'ー'
                }).ifEmpty { null }
            else -> null
        }

        val needsFurigana = hasKanji && readingHira != null

        val furigana: String? = when {
            !settings.includeFurigana -> null
            needsFurigana -> readingHira
            // Optionally show furigana on kana-only spans (identity reading)
            !settings.furiganaKanjiOnly && readingHira != null &&
                KanaConverter.isMostlyKana(surface) -> readingHira
            else -> null
        }

        val romajiSource = readingHira
            ?: surface.takeIf { KanaConverter.isMostlyKana(it) }?.let {
                KanaConverter.toHiragana(it.filter { ch ->
                    KanaConverter.isKana(ch) || ch == 'ー'
                })
            }

        val romaji: String? = when {
            !settings.includeRomaji -> null
            romajiSource.isNullOrBlank() -> {
                // Surface may mix kana + punctuation (e.g. 「はい」)
                val stripped = surface.filter {
                    KanaConverter.isKana(it) || it == 'ー'
                }
                if (stripped.isNotEmpty()) {
                    val r = romanizer.romanize(KanaConverter.toHiragana(stripped))
                    capitalizeIf(settings, r)
                } else if (surface.any { KanaConverter.isPunctuation(it) }) {
                    KanaConverter.punctuationToRomaji(surface)
                } else if (surface.isNotBlank()) {
                    // Non-Japanese passthrough (e.g. "Wi-Fi", a brand name, a
                    // fullwidth-typed number): the romaji line should still
                    // carry it rather than leave a blank hole under it.
                    KanaConverter.fullwidthToHalfwidth(surface)
                } else null
            }
            else -> capitalizeIf(settings, romanizer.romanize(romajiSource))
        }

        return AnnotatedSegment(
            surface = surface,
            furigana = furigana,
            romaji = romaji,
            needsFurigana = needsFurigana,
            isBoundToPrevious = token.isBoundToPrevious,
            isParticle = token.isParticle
        )
    }

    private fun capitalizeIf(settings: JapanglifySettings, r: String): String =
        if (settings.capitalizeRomaji) r.replaceFirstChar {
            if (it.isLowerCase()) it.titlecase() else it.toString()
        } else r

    /**
     * Greedy character-class runs when no morphological analyzer is bound.
     * Good enough for pure kana and mixed Latin/punctuation; kanji runs
     * will lack readings until a [ReadingProvider] is supplied.
     */
    private fun fallbackTokenize(text: String): List<SurfaceReading> {
        if (text.isEmpty()) return emptyList()
        val result = ArrayList<SurfaceReading>()
        val buf = StringBuilder()
        var kind = kindOf(text[0])

        fun flush() {
            if (buf.isNotEmpty()) {
                val s = buf.toString()
                val reading = when (kind) {
                    Kind.KANA -> KanaConverter.toHiragana(s)
                    Kind.PUNCT -> s
                    else -> null
                }
                result += SurfaceReading(s, reading)
                buf.clear()
            }
        }

        for (c in text) {
            val k = kindOf(c)
            if (k != kind) {
                flush()
                kind = k
            }
            buf.append(c)
        }
        flush()
        return result
    }

    private enum class Kind { KANA, KANJI, PUNCT, OTHER }

    private fun kindOf(c: Char): Kind = when {
        KanaConverter.isKana(c) || c == 'ー' -> Kind.KANA
        KanaConverter.isKanji(c) -> Kind.KANJI
        KanaConverter.isPunctuation(c) -> Kind.PUNCT
        else -> Kind.OTHER
    }
}
