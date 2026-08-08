package com.japanglify.app.domain

/**
 * Serializes [AnnotatedSegment] lists into a single string that carries
 * base Japanese + furigana + romaji, following popular triple-script
 * conventions where possible.
 *
 * Reference conventions:
 * - Print / HTML: double-sided ruby — furigana above (or right in tategaki),
 *   romaji below (or left) for maximum room and visibility.
 * - Plain text / dictionaries: parenthetical `語（よみ / yomi）`.
 * - Learners' materials: interlinear three-line blocks.
 */
class TripleScriptRenderer {

    companion object {
        /**
         * Column pad / gap for interlinear plain text.
         *
         * Discord (and similar chat UIs) strip leading Unicode whitespace
         * including U+0020 and NBSP (U+00A0), which destroys left-aligned
         * columns. Braille Pattern Blank (U+2800) is not whitespace, so it is
         * kept at line start and between cells.
         */
        const val PAD = "\u2800"
        /** Zero-width, non-rendering, break-prohibiting \u2014 see [preventLineWrap]. */
        private const val WORD_JOINER = '\u2060'
        /** @deprecated Use [PAD]; kept so older tests/callers still resolve. */
        const val NBSP = PAD
        /** Visible gap between distinct words/particles (English-style word-spacing). */
        private val WORD_GAP = PAD + PAD
        /**
         * Romaji size relative to the base text in HTML ruby output, when
         * rendered as a plain block span rather than a ruby tier (see
         * [htmlSpan]). Matches [com.japanglify.app.clipboard.ClipboardImageRenderer
         * .FURIGANA_RELATIVE_SIZE] so annotations read as consistently
         * "smaller than the base" across every output path — domain has no
         * dependency on the app module, so the value is duplicated, not
         * shared, and should stay in sync if either changes.
         */
        private const val ROMAJI_RELATIVE_SIZE = 0.62
    }

    fun render(segments: List<AnnotatedSegment>, settings: JapanglifySettings): String {
        if (segments.isEmpty()) return ""

        val body = when (settings.outputFormat) {
            OutputFormat.FURIGANA_INLINE -> renderFuriganaInline(segments, settings)
            OutputFormat.PARENTHETICAL -> renderParenthetical(segments, settings)
            OutputFormat.INTERLINEAR -> renderInterlinear(segments, settings)
            OutputFormat.HTML_RUBY -> renderHtmlRuby(segments, settings)
            OutputFormat.COMPACT -> renderCompact(segments, settings)
        }

        return if (settings.writingOrientation == WritingOrientation.VERTICAL) {
            // Future hook: full tategaki would use a dedicated view / CSS
            // writing-mode. For plain-text PROCESS_TEXT we mark the block
            // and keep annotation order adapted for vertical reading.
            wrapVerticalPlain(body, settings)
        } else {
            body
        }
    }

    // ── Furigana inline (Aozora-style 《》) ─────────────────────────

    /**
     * Most readable plain-text furigana for chat apps.
     * True “small kana above” needs HTML/CSS ruby — Unicode cannot stack
     * arbitrary small hiragana over kanji in Discord/X plain text.
     *
     * Example:
     * ```
     * 日本語《にほんご》を勉強《べんきょう》する。
     * nihongo o benkyou suru.
     * ```
     */
    private fun renderFuriganaInline(
        segments: List<AnnotatedSegment>,
        settings: JapanglifySettings
    ): String {
        val base = buildString {
            for (seg in segments) {
                append(seg.surface)
                val f = seg.furigana?.takeIf {
                    settings.includeFurigana && it.isNotBlank() && seg.needsFurigana
                }
                // Aozora Bunko / books-for-the-blind convention for plain-text ruby
                if (f != null) append('《').append(f).append('》')
            }
        }

        val lines = mutableListOf(base)
        if (settings.includeRomaji) {
            // Spaced romaji for scanability (not letter-aligned)
            val romaParts = segments.mapNotNull { it.romaji?.takeIf { r -> r.isNotBlank() } }
            if (romaParts.isNotEmpty()) lines += romaParts.joinToString(" ")
        }
        if (settings.includeGlosses) {
            // Same shape as the romaji line — one gloss per word, space-joined.
            val glossParts = segments.mapNotNull { it.gloss?.takeIf { g -> g.isNotBlank() } }
            if (glossParts.isNotEmpty()) lines += glossParts.joinToString(" ")
        }
        if (settings.includeEmoji) {
            // Emoji has no position setting of its own — always trails as the
            // last line, same reasoning as gloss (meaning, not a reading).
            val emojiParts = segments.mapNotNull { it.emoji?.takeIf { e -> e.isNotBlank() } }
            if (emojiParts.isNotEmpty()) lines += emojiParts.joinToString(" ")
        }
        return lines.joinToString("\n")
    }

    // ── Parenthetical ────────────────────────────────────────────────

    private fun renderParenthetical(
        segments: List<AnnotatedSegment>,
        settings: JapanglifySettings
    ): String = buildString {
        for (seg in segments) {
            append(parentheticalSpan(seg, settings))
        }
    }

    private fun parentheticalSpan(seg: AnnotatedSegment, settings: JapanglifySettings): String {
        val f = seg.furigana?.takeIf { settings.includeFurigana }
        val r = seg.romaji?.takeIf { settings.includeRomaji }
        val g = seg.gloss?.takeIf { settings.includeGlosses }
        val e = seg.emoji?.takeIf { settings.includeEmoji }
        if (f == null && r == null && g == null && e == null) return seg.surface

        // Reading (furigana/romaji) order follows romajiPosition as before;
        // gloss and emoji have no position setting of their own — they're
        // meaning, not a phonetic reading, so they always trail the
        // reading(s), emoji after gloss.
        val readingParts = when {
            f != null && r != null -> when (settings.romajiPosition) {
                RomajiPosition.BEFORE, RomajiPosition.ABOVE -> listOf(r, f)
                RomajiPosition.BELOW, RomajiPosition.AFTER -> listOf(f, r)
            }
            f != null -> listOf(f)
            r != null -> listOf(r)
            else -> emptyList()
        }
        val annotation = (readingParts + listOfNotNull(g, e)).joinToString(" / ")

        return when (settings.romajiPosition) {
            RomajiPosition.BEFORE ->
                if (r != null && f == null && g == null && e == null) "（$r）${seg.surface}"
                else "（$annotation）${seg.surface}"
            else -> "${seg.surface}（$annotation）"
        }
    }

    // ── Interlinear ──────────────────────────────────────────────────

    /**
     * One display cell in the interlinear grid (furigana-style columns).
     * Furigana is only non-empty for true readings (kanji / explicit marks),
     * never a full parallel “hiragana translation” of kana particles.
     *
     * [isWordStart] marks the first cell of a morphological segment (a real
     * word or particle boundary) as opposed to a continuation cell produced
     * by splitting one word across several kanji/okurigana columns — we gap
     * the former like English word-spacing and butt the latter together like
     * the original kana had no spaces at all, so word boundaries stay legible
     * instead of reading as one long run of same-spaced syllables.
     */
    private data class InterlinearCell(
        val furigana: String,
        val base: String,
        val romaji: String,
        val isWordStart: Boolean = true,
        /**
         * Whether a line wrap may happen right before this cell. Particles
         * still get [isWordStart]'s visible gap (they're real word/particle
         * boundaries) but must never dangle alone at the start of a wrapped
         * line — matching Japanese typesetting convention (a lone は/を/の
         * starting a line reads as a mistake). Defaults to [isWordStart].
         */
        val canWrapBefore: Boolean = isWordStart,
        /**
         * True for a punctuation-only cell. Punctuation glyphs (、。！？…)
         * are conventionally drawn left-anchored in their em-square in CJK
         * fonts, not centered — so unlike every other cell, these are laid
         * out left-aligned rather than centered (see [padStartDisplay]),
         * which also sidesteps [padCenterDisplay]'s odd-width rounding
         * being an arbitrary coin-flip for the near-universal case of a
         * halfwidth romaji mark under a fullwidth base mark.
         */
        val isPunctuation: Boolean = false,
        /**
         * English dictionary gloss, word-level not sub-cell-level: only ever
         * set on a word's first cell (mirroring how split-kanji romaji only
         * appears "under the first kanji of the token" — see
         * [splitKanjiFurigana]), empty on every continuation cell.
         * Left-anchored ([padEndDisplay], never centered) within its cell's
         * width, and never truncated — but unlike furigana/base/romaji, this
         * text is usually much wider than the mora-grid those fit into, so
         * (see [buildMeasuredRows]) its width also feeds into the cell's own
         * measured width and therefore the row's wrap budget, so a long
         * gloss widens its column instead of overflowing into the next
         * word's.
         */
        val gloss: String = "",
        /**
         * Optional English→emoji annotation (see
         * [com.japanglify.app.domain.emoji.EmojiAnnotator]) — same
         * word-level, first-cell-only, width-widening treatment as [gloss],
         * and always rendered as the line *after* it (see
         * [InterlinearLineRole.EMOJI]).
         */
        val emoji: String = ""
    )

    /** Structured (non-flattened) cell, exposed so callers can lay out pixels themselves. */
    data class InterlinearCellData(
        val furigana: String,
        val base: String,
        val romaji: String,
        val isWordStart: Boolean,
        val gloss: String = "",
        val emoji: String = ""
    )

    data class InterlinearRowData(val cells: List<InterlinearCellData>)

    /**
     * Structured interlinear rows for callers that want to lay out pixels
     * themselves (e.g. rasterizing to an image) instead of trusting a host's
     * font to honor our halfwidth/fullwidth unit assumptions — see
     * [displayWidth]'s doc for why that assumption can quietly break down.
     */
    fun buildInterlinearRows(
        segments: List<AnnotatedSegment>,
        settings: JapanglifySettings
    ): List<InterlinearRowData> =
        buildMeasuredRows(segments, settings).map { row ->
            InterlinearRowData(
                row.map {
                    InterlinearCellData(it.furigana, it.base, it.romaji, it.isWordStart, it.gloss, it.emoji)
                }
            )
        }

    private fun renderInterlinear(
        segments: List<AnnotatedSegment>,
        settings: JapanglifySettings
    ): String {
        val rows = buildMeasuredRows(segments, settings)
        return rows.joinToString("\n\n") { row ->
            formatInterlinearRow(row, settings)
        }
    }

    private fun buildMeasuredRows(
        segments: List<AnnotatedSegment>,
        settings: JapanglifySettings
    ): List<List<MeasuredCell>> {
        val cells = expandToFuriganaCells(segments, settings)
        if (cells.isEmpty()) return emptyList()

        // Measure each cell (halfwidth units: fullwidth kana ≈ 2). Gloss
        // width is included here too (cell.gloss is already "" when glosses
        // are off, so this is a no-op then) -- a gloss much wider than its
        // word's furigana/base/romaji used to just overflow into whatever
        // followed on the same line, visibly colliding with the next word's
        // columns. Widening the cell (and therefore the whole row's wrap
        // budget, via packCellsIntoRows below) to fit its own gloss trades
        // that collision for short words sometimes sitting in a visually
        // sparse, wider-than-necessary column -- an accepted tradeoff for
        // "never overlaps," worth live-testing rather than tuning further
        // analytically up front (matches how furigana/romaji/HTML ruby were
        // all actually fixed this session).
        val measured = cells.map { cell ->
            val furiCompact = compactFurigana(cell.furigana)
            val width = maxOf(
                displayWidth(cell.base),
                displayWidth(furiCompact),
                displayWidth(cell.romaji),
                displayWidth(cell.gloss),
                displayWidth(cell.emoji)
            ).coerceAtLeast(1)
            MeasuredCell(
                furiCompact, cell.base, cell.romaji, width,
                cell.isWordStart, cell.canWrapBefore, cell.isPunctuation, cell.gloss, cell.emoji
            )
        }

        val maxHalf = fullwidthToHalfUnits(settings.maxLineWidthFullwidth)
        return packCellsIntoRows(measured, maxHalf, wordGapHalf = WORD_GAP_WIDTH)
    }

    private data class MeasuredCell(
        val furigana: String,
        val base: String,
        val romaji: String,
        val width: Int,
        val isWordStart: Boolean,
        val canWrapBefore: Boolean,
        val isPunctuation: Boolean,
        val gloss: String = "",
        val emoji: String = ""
    )

    private fun compactFurigana(furigana: String): String = furigana

    /** 0 = unlimited; otherwise fullwidth kana slots × 2 (halfwidth cells). */
    private fun fullwidthToHalfUnits(fullwidth: Int): Int =
        if (fullwidth <= 0) Int.MAX_VALUE else fullwidth * 2

    /** Gap between distinct words/particles — visible, like English word-spacing. */
    private val WORD_GAP_WIDTH = 2 // PAD+PAD

    private fun packCellsIntoRows(
        cells: List<MeasuredCell>,
        maxHalfWidth: Int,
        wordGapHalf: Int
    ): List<List<MeasuredCell>> {
        if (cells.isEmpty()) return emptyList()
        val rows = ArrayList<List<MeasuredCell>>()
        var row = ArrayList<MeasuredCell>()
        var used = 0
        for (cell in cells) {
            // No gap between sub-cells of the same word (e.g. split kanji/okurigana) —
            // only before a new word/particle, and only once one is already on the row.
            val gap = if (row.isEmpty() || !cell.isWordStart) 0 else wordGapHalf
            val need = cell.width + gap
            // Only ever wrap-break where explicitly allowed. Bound-to-previous
            // cells (trailing punctuation, split kanji/okurigana) and particles
            // never start a new line — a line slightly over budget reads better
            // than a lonely "！" or a dangling "は".
            if (row.isNotEmpty() && cell.canWrapBefore && used + need > maxHalfWidth) {
                rows += row
                row = ArrayList()
                used = 0
            }
            row += cell
            used += if (row.size == 1) cell.width else need
        }
        if (row.isNotEmpty()) rows += row
        return rows
    }

    /** Which interlinear row a display line came from. */
    enum class InterlinearLineRole { FURIGANA, BASE, ROMAJI, GLOSS, EMOJI }

    data class InterlinearDisplayLine(val role: InterlinearLineRole, val text: String)

    /**
     * One row's lines in on-screen order, role-tagged so a caller with its
     * own rich-text renderer (e.g. a UI layer wanting real ruby-style small
     * furigana instead of a plain third line) knows which physical line is
     * which without having to reverse-engineer it from [RomajiPosition] and
     * per-row visibility rules itself.
     */
    data class InterlinearDisplayRow(val lines: List<InterlinearDisplayLine>)

    /**
     * Structured, role-tagged alternative to the plain-text [render] output.
     * Used only for an in-app rich preview (see [preventLineWrap]) — this is
     * why, unlike [render], it's safe for this path to embed Word Joiners:
     * the result never leaves the app as copied/pasted/replaced text.
     */
    fun buildInterlinearDisplayRows(
        segments: List<AnnotatedSegment>,
        settings: JapanglifySettings
    ): List<InterlinearDisplayRow> =
        buildMeasuredRows(segments, settings).map {
            InterlinearDisplayRow(buildDisplayLines(it, settings, preventWrap = true))
        }

    private fun buildRawTripleLines(row: List<MeasuredCell>): Triple<String, String, String> {
        val base = StringBuilder()
        val furi = StringBuilder()
        val roma = StringBuilder()
        row.forEachIndexed { index, cell ->
            // Gap only before a new word/particle, never between sub-cells of the
            // same word (split kanji/okurigana) — see [InterlinearCell.isWordStart].
            if (index > 0 && cell.isWordStart) {
                base.append(WORD_GAP)
                furi.append(WORD_GAP)
                roma.append(WORD_GAP)
            }
            val pad = if (cell.isPunctuation) ::padEndDisplay else ::padCenterDisplay
            base.append(pad(cell.base, cell.width))
            furi.append(pad(cell.furigana, cell.width))
            roma.append(pad(cell.romaji, cell.width))
        }
        return Triple(base.toString(), furi.toString(), roma.toString())
    }

    /**
     * Gloss line, built separately from [buildRawTripleLines]: left-anchored
     * ([padEndDisplay], never [padCenterDisplay]) and never truncated. Each
     * cell's width already accommodates its own gloss (see
     * [buildMeasuredRows]), so this just pads out to that width rather than
     * needing to overflow past it — see [InterlinearCell.gloss].
     */
    private fun buildGlossLine(row: List<MeasuredCell>): String {
        val gloss = StringBuilder()
        row.forEachIndexed { index, cell ->
            if (index > 0 && cell.isWordStart) gloss.append(WORD_GAP)
            gloss.append(padEndDisplay(cell.gloss, cell.width))
        }
        return gloss.toString()
    }

    /** Emoji line — same left-anchored, never-truncated shape as [buildGlossLine]. */
    private fun buildEmojiLine(row: List<MeasuredCell>): String {
        val emoji = StringBuilder()
        row.forEachIndexed { index, cell ->
            if (index > 0 && cell.isWordStart) emoji.append(WORD_GAP)
            emoji.append(padEndDisplay(cell.emoji, cell.width))
        }
        return emoji.toString()
    }

    private fun buildDisplayLines(
        row: List<MeasuredCell>,
        settings: JapanglifySettings,
        preventWrap: Boolean
    ): List<InterlinearDisplayLine> {
        val (baseLine, furiLine, romaLine) = buildRawTripleLines(row)
        val glossLine = if (settings.includeGlosses) buildGlossLine(row) else ""
        val emojiLine = if (settings.includeEmoji) buildEmojiLine(row) else ""
        fun finish(line: String): String {
            val protectedLine = protectLineStart(line)
            return if (preventWrap) preventLineWrap(protectedLine) else protectedLine
        }
        val lines = mutableListOf<InterlinearDisplayLine>()
        fun addFuri() {
            if (settings.includeFurigana && hasVisibleContent(furiLine)) {
                lines += InterlinearDisplayLine(InterlinearLineRole.FURIGANA, finish(furiLine))
            }
        }
        fun addRoma() {
            if (settings.includeRomaji && hasVisibleContent(romaLine)) {
                lines += InterlinearDisplayLine(InterlinearLineRole.ROMAJI, finish(romaLine))
            }
        }
        when (settings.romajiPosition) {
            RomajiPosition.ABOVE, RomajiPosition.BEFORE -> {
                addRoma()
                addFuri()
                lines += InterlinearDisplayLine(InterlinearLineRole.BASE, finish(baseLine))
            }
            RomajiPosition.BELOW, RomajiPosition.AFTER -> {
                addFuri()
                lines += InterlinearDisplayLine(InterlinearLineRole.BASE, finish(baseLine))
                addRoma()
            }
        }
        // Gloss and emoji have no position setting of their own (they're
        // meaning, not a phonetic reading) — gloss always trails as the 4th
        // row regardless of where romaji/furigana landed, and emoji trails
        // after that as the last row of all.
        if (settings.includeGlosses && hasVisibleContent(glossLine)) {
            lines += InterlinearDisplayLine(InterlinearLineRole.GLOSS, finish(glossLine))
        }
        if (settings.includeEmoji && hasVisibleContent(emojiLine)) {
            lines += InterlinearDisplayLine(InterlinearLineRole.EMOJI, finish(emojiLine))
        }
        return lines
    }

    /**
     * Plain-text rendering. Word Joiners ARE embedded here too (see
     * [preventLineWrap]) even though this output gets copied/pasted/Cut-
     * replaced into other apps: real hosts (Discord, Keep, …) soft-wrap
     * their own text views at the pixel width of the compose box, with no
     * regard for [JapanglifySettings.maxLineWidthFullwidth] — without the
     * Word Joiners a host can still break in the middle of a furigana cell
     * (e.g. "でき" splitting into "で" / "き" on two visual lines), which is
     * worse than a few invisible zero-width characters riding along in the
     * pasted text.
     */
    private fun formatInterlinearRow(
        row: List<MeasuredCell>,
        settings: JapanglifySettings
    ): String = buildDisplayLines(row, settings, preventWrap = true).joinToString("\n") { it.text }

    /**
     * Kana/CJK characters permit a line break between any two of them by
     * default (Unicode UAX #14) — unlike Latin text, which only breaks at
     * spaces. Left unchecked, a host's own text layout can quietly wrap a
     * row we already fit to [JapanglifySettings.maxLineWidthFullwidth] right
     * in the middle of a furigana/base/romaji run, splitting one column's
     * characters onto two visual lines and destroying the alignment this
     * whole cell/padding scheme exists to produce. Word Joiner (U+2060) is a
     * zero-width, non-rendering character whose sole effect is prohibiting a
     * break at that point — inserting it between every character forces the
     * line to stay, or overflow/scroll, as one unit instead of rewrapping.
     */
    private fun preventLineWrap(line: String): String {
        if (line.length <= 1) return line
        val out = StringBuilder(line.length * 2 - 1)
        var i = 0
        while (i < line.length) {
            val cp = line.codePointAt(i)
            val charCount = Character.charCount(cp)
            if (i > 0) out.append(WORD_JOINER)
            out.appendCodePoint(cp)
            i += charCount
        }
        return out.toString()
    }

    /** True if the line has any non-pad, non-whitespace glyph. */
    private fun hasVisibleContent(line: String): Boolean =
        line.any { it != PAD_CHAR && !it.isWhitespace() }

    /**
     * Ensure the first column cannot be eaten: if the line would start with
     * pad (empty first cell), Discord still keeps U+2800; we also force a
     * single leading PAD so pure-ASCII hosts that only trim U+0020/NBSP are safe.
     */
    private fun protectLineStart(line: String): String {
        if (line.isEmpty()) return PAD
        if (line[0] == PAD_CHAR) return line
        val c = line[0]
        if (c == ' ' || c == '\u00A0' || c == '\u3000') {
            return PAD + line.substring(1)
        }
        return line
    }

    /**
     * Expand morph tokens into furigana-style cells: one column per kanji
     * (with split reading) / okurigana / particle / punctuation, so the top
     * line reads as ruby rather than a parallel hiragana sentence.
     */
    private fun expandToFuriganaCells(
        segments: List<AnnotatedSegment>,
        settings: JapanglifySettings
    ): List<InterlinearCell> {
        val out = ArrayList<InterlinearCell>()
        segments.forEachIndexed { index, seg ->
            val surface = seg.surface
            val furi = if (settings.includeFurigana) seg.furigana else null
            // Mora-hyphenated when available (see AnnotatedSegment.romajiSyllables)
            // so a multi-kanji word's romaji shows which part matches which
            // kana/kanji instead of one unbroken run; falls back to the plain
            // form for segments that never got a hyphenated variant computed.
            val roma = if (settings.includeRomaji) {
                (seg.romajiSyllables ?: seg.romaji).orEmpty()
            } else ""
            // Word-level, not sub-cell-level — see [InterlinearCell.gloss].
            val gloss = if (settings.includeGlosses) seg.gloss.orEmpty() else ""
            val emoji = if (settings.includeEmoji) seg.emoji.orEmpty() else ""

            val isWordStart = !seg.isBoundToPrevious
            // Particles get their word-gap but can never be a wrap point —
            // see [InterlinearCell.canWrapBefore]. Beyond that: a kana-only
            // word directly following another kana-only word carries no
            // visual boundary for a reader the way kanji or punctuation do,
            // so a line break there reads as a mid-word split even though
            // Kuromoji drew a token boundary. Only wrap there when the
            // preceding word actually is a particle -- a real phrase
            // boundary in Japanese -- not just any adjacent kana word.
            val prev = segments.getOrNull(index - 1)
            val kanaToKanaGap = prev != null &&
                !KanaConverter.containsKanji(surface) &&
                !KanaConverter.containsKanji(prev.surface) &&
                !KanaConverter.isMostlyPunctuation(prev.surface)
            val canWrapBefore = isWordStart && !seg.isParticle && (!kanaToKanaGap || prev?.isParticle == true)
            when {
                // Punctuation: same mark on the base row always; the furigana
                // row's copy is optional — punctuation has no reading, so a
                // bare 、 up there is visual noise by default (see
                // FuriganaPunctuationStyle). Always bound to the previous
                // word — punctuation getting its own word-gap (or worse,
                // stranded alone on a wrapped line) reads as a typo, not a
                // sentence ending.
                KanaConverter.isMostlyPunctuation(surface) -> {
                    val punctuationFurigana = if (!settings.includeFurigana) {
                        ""
                    } else when (settings.furiganaPunctuationStyle) {
                        FuriganaPunctuationStyle.NONE -> ""
                        FuriganaPunctuationStyle.ORIGINAL -> surface
                        FuriganaPunctuationStyle.ROMAN ->
                            KanaConverter.punctuationToRomaji(surface).ifBlank { surface }
                    }
                    out += InterlinearCell(
                        furigana = punctuationFurigana,
                        base = surface,
                        romaji = roma.ifEmpty {
                            if (settings.includeRomaji) {
                                KanaConverter.punctuationToRomaji(surface)
                            } else ""
                        },
                        isWordStart = false,
                        isPunctuation = true
                    )
                }

                // True furigana: keep kanji word intact when romaji is included so words don't get blown apart into scattered kanji
                seg.needsFurigana && !furi.isNullOrBlank() &&
                    KanaConverter.containsKanji(surface) -> {
                    if (settings.includeRomaji && roma.isNotBlank()) {
                        out += InterlinearCell(
                            furigana = if (settings.includeFurigana) furi else "",
                            base = surface,
                            romaji = roma,
                            isWordStart = isWordStart,
                            canWrapBefore = canWrapBefore,
                            gloss = gloss,
                            emoji = emoji
                        )
                    } else {
                        out += splitKanjiFurigana(
                            surface, furi, roma, gloss, emoji, settings, isWordStart, canWrapBefore
                        )
                    }
                }

                // Pure kana / other: base + romaji only (no identity furigana line)
                else -> {
                    out += InterlinearCell(
                        furigana = if (
                            settings.includeFurigana &&
                            !settings.furiganaKanjiOnly &&
                            !furi.isNullOrBlank()
                        ) furi else "",
                        base = surface,
                        romaji = roma,
                        isWordStart = isWordStart,
                        canWrapBefore = canWrapBefore,
                        gloss = gloss,
                        emoji = emoji
                    )
                }
            }
        }
        return out
    }

    /**
     * Split e.g. 日本語/にほんご → (日,に)(本,ほん)(語,ご) and 食べ/たべ → (食,た)(べ,べ).
     * Romaji stays under the whole original token on the first cell only so
     * the latin line is readable (not letter-scattered) — gloss and emoji
     * do too, for the same reason.
     */
    private fun splitKanjiFurigana(
        surface: String,
        reading: String,
        romaji: String,
        gloss: String,
        emoji: String,
        settings: JapanglifySettings,
        isWordStart: Boolean,
        canWrapBefore: Boolean
    ): List<InterlinearCell> {
        // Peel trailing okurigana that match the end of the reading
        var sEnd = surface.length
        var rEnd = reading.length
        while (sEnd > 0 && rEnd > 0) {
            val sc = surface[sEnd - 1]
            val rc = reading[rEnd - 1]
            if (!KanaConverter.isKana(sc) && sc != 'ー') break
            val sh = KanaConverter.toHiragana(sc.toString())
            val rh = KanaConverter.toHiragana(rc.toString())
            if (sh != rh && sc != 'ー') break
            sEnd--
            rEnd--
        }
        val kanjiPart = surface.substring(0, sEnd)
        val okuri = surface.substring(sEnd)
        val kanjiReading = reading.substring(0, rEnd)
        val okuriReading = reading.substring(rEnd)

        val cells = ArrayList<InterlinearCell>()
        val kanjiChars = kanjiPart.toList()
        if (kanjiChars.isEmpty()) {
            cells += InterlinearCell(
                furigana = if (settings.includeFurigana) reading else "",
                base = surface,
                romaji = romaji,
                isWordStart = isWordStart,
                canWrapBefore = canWrapBefore,
                gloss = gloss,
                emoji = emoji
            )
            return cells
        }

        // Glue ん to the previous mora so に+ほん+ご maps cleanly onto 日+本+語
        val moraList = glueSyllabicN(KanaConverter.morae(kanjiReading))
        val parts = distributeMorae(moraList, kanjiChars.size)
        kanjiChars.forEachIndexed { i, ch ->
            cells += InterlinearCell(
                furigana = if (settings.includeFurigana) parts.getOrElse(i) { "" } else "",
                base = ch.toString(),
                // Put full romaji only under the first kanji of the token
                romaji = if (i == 0) romaji else "",
                // Only the token's very first cell is a real word boundary —
                // the rest are sub-cells of the same word, butted together.
                isWordStart = i == 0 && isWordStart,
                canWrapBefore = i == 0 && canWrapBefore,
                // Same "first cell only" rule as romaji, for the same reason.
                gloss = if (i == 0) gloss else "",
                emoji = if (i == 0) emoji else ""
            )
        }
        if (okuri.isNotEmpty()) {
            // Okurigana: show identity kana on furi only if not kanji-only mode
            val showOkuriFuri = settings.includeFurigana && !settings.furiganaKanjiOnly
            cells += InterlinearCell(
                furigana = if (showOkuriFuri) okuriReading.ifEmpty { okuri } else "",
                base = okuri,
                romaji = "",
                isWordStart = false
            )
        }
        return cells
    }

    /** Attach ん to the preceding mora (に+ん → にん stays wrong; ほ+ん → ほん is right). */
    private fun glueSyllabicN(morae: List<String>): List<String> {
        if (morae.isEmpty()) return emptyList()
        val out = ArrayList<String>(morae.size)
        for (m in morae) {
            if (m == "ん" && out.isNotEmpty()) {
                out[out.lastIndex] = out.last() + m
            } else {
                out += m
            }
        }
        return out
    }

    /**
     * Spread mora groups across [bucketCount] kanji.
     * Remainder goes to the *later* buckets (on'yomi often lengthens toward the end:
     * べん+きょう over 勉+強, not べんきょ+う).
     */
    private fun distributeMorae(morae: List<String>, bucketCount: Int): List<String> {
        if (bucketCount <= 0) return emptyList()
        if (morae.isEmpty()) return List(bucketCount) { "" }
        if (bucketCount == 1) return listOf(morae.joinToString(""))
        val base = morae.size / bucketCount
        val rem = morae.size % bucketCount
        val out = ArrayList<String>(bucketCount)
        var idx = 0
        for (b in 0 until bucketCount) {
            // Extra morae assigned to the last `rem` buckets
            val n = base + if (b >= bucketCount - rem) 1 else 0
            if (n <= 0 || idx >= morae.size) {
                out += ""
            } else {
                val end = (idx + n).coerceAtMost(morae.size)
                out += morae.subList(idx, end).joinToString("")
                idx = end
            }
        }
        if (idx < morae.size && out.isNotEmpty()) {
            val last = out.lastIndex
            out[last] = out[last] + morae.subList(idx, morae.size).joinToString("")
        }
        return out
    }

    /**
     * Left-justify [text] into a field of [targetWidth] display cells using
     * [PAD] (U+2800). Empty cells still occupy full width so columns stack.
     */
    private fun padEndDisplay(text: String, targetWidth: Int): String {
        val w = displayWidth(text)
        if (w >= targetWidth) return text
        return text + PAD.repeat(targetWidth - w)
    }

    /**
     * Center [text] in a field of [targetWidth] display cells, distributing
     * the padding as gaps *around each character* — CSS Ruby's default
     * `ruby-align: space-around` technique for annotation text shorter than
     * the width it must fill — rather than as two lumps at the outer edges.
     * A multi-character word that needs centering (e.g. a kanji compound
     * kept whole because romaji is enabled, padded out to match a wider
     * mora-hyphenated romaji line) fills its column evenly instead of
     * clustering in the middle of empty margin.
     *
     * [text]'s `n` codepoints create `n + 1` gap positions (before the
     * first character, between each adjacent pair, after the last); each
     * gets `diff / (n + 1)` PAD units, with the `diff % (n + 1)` remainder
     * distributed one unit at a time from the outer edges inward. This
     * needs no special-casing for short text: at `n = 0` there's exactly
     * one position, so all padding lands as a single block (identical to
     * the old edge-only behavior); at `n = 1` there are exactly two edge
     * positions, again identical to before. It also degrades gracefully
     * when `diff` is small relative to `n` — with too little slack to give
     * every interior gap even one whole PAD unit, the remainder-to-edges
     * rule naturally leaves the interior gaps at zero and all padding sits
     * at the edges, same as today, rather than doing token, uneven
     * micro-spacing that wouldn't read as intentional.
     */
    private fun padCenterDisplay(text: String, targetWidth: Int): String {
        val w = displayWidth(text)
        if (w >= targetWidth) return text
        val diff = targetWidth - w

        val codePoints = ArrayList<Int>()
        var i = 0
        while (i < text.length) {
            val cp = text.codePointAt(i)
            codePoints += cp
            i += Character.charCount(cp)
        }

        val gapCount = codePoints.size + 1
        val base = diff / gapCount
        var remainder = diff - base * gapCount
        val gaps = IntArray(gapCount) { base }
        var lo = 0
        var hi = gapCount - 1
        while (remainder > 0 && lo <= hi) {
            gaps[lo] += 1
            remainder--
            if (lo != hi && remainder > 0) {
                gaps[hi] += 1
                remainder--
            }
            lo++
            hi--
        }

        return buildString {
            append(PAD.repeat(gaps[0]))
            codePoints.forEachIndexed { idx, cp ->
                appendCodePoint(cp)
                append(PAD.repeat(gaps[idx + 1]))
            }
        }
    }

    private val PAD_CHAR: Char get() = PAD[0]

    /**
     * Approximate terminal / monospace display width:
     * halfwidth (Latin, halfwidth kana, …) = 1; wide CJK / fullwidth = 2.
     */
    internal fun displayWidth(text: String): Int {
        var width = 0
        var i = 0
        while (i < text.length) {
            val cp = text.codePointAt(i)
            width += codePointDisplayWidth(cp)
            i += Character.charCount(cp)
        }
        return width
    }

    private fun codePointDisplayWidth(cp: Int): Int {
        if (cp <= 0x1F) return 0
        // Word Joiner (see preventLineWrap) is zero-width by definition.
        if (cp == 0x2060) return 0
        // Braille blank used as Discord-safe pad — count as 1 cell
        if (cp == 0x2800) return 1
        // Halfwidth Forms (halfwidth katakana etc.)
        if (cp in 0xFF61..0xFF9F) return 1
        // Latin-1 and most Western
        if (cp < 0x1100) return 1
        // Hangul Jamo leading consonants (wide)
        if (cp in 0x1100..0x115F) return 2
        // CJK radicals, punctuation, kana, ideographs, Hangul syllables, …
        if (cp in 0x2E80..0xA4CF) return 2
        if (cp in 0xAC00..0xD7A3) return 2
        if (cp in 0xF900..0xFAFF) return 2
        if (cp in 0xFE10..0xFE19) return 2
        if (cp in 0xFE30..0xFE6F) return 2
        if (cp in 0xFF01..0xFF60) return 2
        if (cp in 0xFFE0..0xFFE6) return 2
        // CJK Extension B and beyond (common SIP range)
        if (cp in 0x20000..0x3FFFD) return 2
        // Emoji / symbols: treat as 2 so columns don't crush
        if (cp in 0x1F300..0x1FAFF) return 2
        return 1
    }

    // ── HTML double-sided ruby ───────────────────────────────────────

    private fun renderHtmlRuby(
        segments: List<AnnotatedSegment>,
        settings: JapanglifySettings
    ): String = buildString {
        // Minimal document fragment; hosts that accept HTML can style further.
        if (settings.writingOrientation == WritingOrientation.VERTICAL) {
            append("""<span style="writing-mode:vertical-rl;text-orientation:upright">""")
        }
        for (seg in segments) {
            append(htmlSpan(seg, settings))
        }
        if (settings.writingOrientation == WritingOrientation.VERTICAL) {
            append("</span>")
        }
    }

    private fun htmlSpan(seg: AnnotatedSegment, settings: JapanglifySettings): String {
        val f = seg.furigana?.takeIf { settings.includeFurigana && it.isNotBlank() }
        val r = seg.romaji?.takeIf { settings.includeRomaji && it.isNotBlank() }
        val g = seg.gloss?.takeIf { settings.includeGlosses && it.isNotBlank() }
        val e = seg.emoji?.takeIf { settings.includeEmoji && it.isNotBlank() }
        val base = escapeHtml(seg.surface)

        if (f == null && r == null && g == null && e == null) return base

        // Furigana-only: plain <ruby><rt>, the one thing every browser gets
        // right — no need for the block-span stack below at all.
        if (f != null && r == null && g == null && e == null) {
            return "<ruby>$base<rt>${escapeHtml(f)}</rt></ruby>"
        }

        // Romaji-only, no furigana, no gloss, no emoji: ruby-position on
        // <rt> IS reliable for a single (non-nested) ruby tier — see the
        // block-span comment below for why that stops being true once
        // anything nests.
        if (f == null && r != null && g == null && e == null) {
            val pos = when (settings.romajiPosition) {
                RomajiPosition.ABOVE, RomajiPosition.BEFORE -> "over"
                RomajiPosition.BELOW, RomajiPosition.AFTER -> "under"
            }
            return """<ruby style="ruby-position:$pos">$base<rt>${escapeHtml(r)}</rt></ruby>"""
        }

        // Everything else (any combination of furigana/romaji/gloss beyond
        // "furigana alone" or "romaji alone"): furigana — if present — via
        // plain <ruby><rt> (over the base, the one thing every browser gets
        // right); romaji and gloss ride as ordinary smaller block-level
        // <span>s, not ruby tiers. NOT the <rb>/<rtc> "complex ruby" markup
        // this used to emit — <rb> and <rtc> were dropped from the HTML
        // Living Standard years ago, and live-tested in a real browser this
        // session, that markup left <rtc> rendering as plain unstyled inline
        // text with zero positioning. A nested second <ruby> tier (also
        // live-tested) does render as real ruby, but `ruby-position:under`
        // proved unreliable on a nested ruby's outer <rt> in that same
        // testing — it stacked on the same "over" side as the inner tier
        // instead of actually landing under the base. Plain CSS sidesteps
        // that unreliability entirely: wrapping everything in one
        // `inline-block` container with ordinary `display:block` spans
        // stacks reliably regardless of browser ruby-position support.
        // Gloss and emoji have no position setting of their own (they're
        // meaning, not a phonetic reading) — they always trail, after
        // wherever romaji landed, emoji after gloss.
        val core = if (f != null) "<ruby>$base<rt>${escapeHtml(f)}</rt></ruby>" else base
        val romajiBlock = r?.let {
            """<span style="display:block;font-size:${ROMAJI_RELATIVE_SIZE}em">${escapeHtml(it)}</span>"""
        }
        val glossBlock = g?.let {
            """<span style="display:block;font-size:${ROMAJI_RELATIVE_SIZE}em">${escapeHtml(it)}</span>"""
        }
        val emojiBlock = e?.let {
            """<span style="display:block;font-size:${ROMAJI_RELATIVE_SIZE}em">${escapeHtml(it)}</span>"""
        }
        val inner = when (settings.romajiPosition) {
            RomajiPosition.ABOVE, RomajiPosition.BEFORE ->
                listOfNotNull(romajiBlock, core, glossBlock, emojiBlock).joinToString("")
            RomajiPosition.BELOW, RomajiPosition.AFTER ->
                listOfNotNull(core, romajiBlock, glossBlock, emojiBlock).joinToString("")
        }
        return """<span style="display:inline-block;text-align:center;vertical-align:top">$inner</span>"""
    }

    private fun escapeHtml(s: String): String = buildString(s.length) {
        for (c in s) {
            when (c) {
                '&' -> append("&amp;")
                '<' -> append("&lt;")
                '>' -> append("&gt;")
                '"' -> append("&quot;")
                '\'' -> append("&#39;")
                else -> append(c)
            }
        }
    }

    // ── Compact brackets ─────────────────────────────────────────────

    private fun renderCompact(
        segments: List<AnnotatedSegment>,
        settings: JapanglifySettings
    ): String = buildString {
        for (seg in segments) {
            val f = seg.furigana?.takeIf { settings.includeFurigana && it.isNotBlank() }
            val r = seg.romaji?.takeIf { settings.includeRomaji && it.isNotBlank() }
            val g = seg.gloss?.takeIf { settings.includeGlosses && it.isNotBlank() }
            val e = seg.emoji?.takeIf { settings.includeEmoji && it.isNotBlank() }

            if (r != null && settings.romajiPosition == RomajiPosition.BEFORE) {
                append('[').append(r).append(']')
            }
            append(seg.surface)
            if (f != null) {
                append('〔').append(f).append('〕')
            }
            if (r != null && settings.romajiPosition != RomajiPosition.BEFORE) {
                append('[').append(r).append(']')
            }
            // Gloss and emoji have no position setting — always trail, same
            // reasoning as PARENTHETICAL: meaning, not a phonetic reading.
            // Emoji rides directly after the surface/brackets, unbracketed —
            // it's already a self-delimiting glyph, not text needing {}.
            if (g != null) {
                append('{').append(g).append('}')
            }
            if (e != null) {
                append(e)
            }
        }
    }

    // ── Vertical plain-text marker ───────────────────────────────────

    private fun wrapVerticalPlain(body: String, settings: JapanglifySettings): String {
        // Plain-text cannot truly set vertical-rl. We prefix a marker and,
        // for interlinear, re-join with ideographic spaces as a hint.
        // HTML_RUBY already emitted writing-mode CSS above.
        if (settings.outputFormat == OutputFormat.HTML_RUBY) return body
        return "｜$body".replace("\n", "\n｜")
    }
}
