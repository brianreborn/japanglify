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
        /** @deprecated Use [PAD]; kept so older tests/callers still resolve. */
        const val NBSP = PAD
        /** Visible gap between distinct words/particles (English-style word-spacing). */
        private val WORD_GAP = PAD + PAD
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
        if (!settings.includeRomaji) return base

        val romaParts = segments.mapNotNull { seg ->
            seg.romaji?.takeIf { it.isNotBlank() }
        }
        if (romaParts.isEmpty()) return base

        // Second line: spaced romaji for scanability (not letter-aligned)
        val romaLine = romaParts.joinToString(" ")
        return "$base\n$romaLine"
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
        if (f == null && r == null) return seg.surface

        val annotation = when {
            f != null && r != null -> when (settings.romajiPosition) {
                RomajiPosition.BEFORE -> "$r / $f"
                RomajiPosition.ABOVE -> "$r / $f" // plain text cannot stack; order hints role
                RomajiPosition.BELOW, RomajiPosition.AFTER -> "$f / $r"
            }
            f != null -> f
            else -> r!!
        }

        return when (settings.romajiPosition) {
            RomajiPosition.BEFORE ->
                if (r != null && f == null) "（$r）${seg.surface}"
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
        val canWrapBefore: Boolean = isWordStart
    )

    /** Structured (non-flattened) cell, exposed so callers can lay out pixels themselves. */
    data class InterlinearCellData(
        val furigana: String,
        val base: String,
        val romaji: String,
        val isWordStart: Boolean
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
                row.map { InterlinearCellData(it.furigana, it.base, it.romaji, it.isWordStart) }
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

        // Measure each cell (halfwidth units: fullwidth kana ≈ 2).
        val measured = cells.map { cell ->
            val furiCompact = compactFurigana(cell.furigana)
            val width = maxOf(
                displayWidth(cell.base),
                displayWidth(furiCompact),
                displayWidth(cell.romaji)
            ).coerceAtLeast(1)
            MeasuredCell(furiCompact, cell.base, cell.romaji, width, cell.isWordStart, cell.canWrapBefore)
        }

        // Wrap into rows by full-width capacity (1 fullwidth unit = 2 half units).
        val maxHalf = fullwidthToHalfUnits(settings.maxLineWidthFullwidth)
        return packCellsIntoRows(measured, maxHalf, wordGapHalf = WORD_GAP_WIDTH)
    }

    private data class MeasuredCell(
        val furigana: String,
        val base: String,
        val romaji: String,
        val width: Int,
        val isWordStart: Boolean,
        val canWrapBefore: Boolean
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

    private fun formatInterlinearRow(
        row: List<MeasuredCell>,
        settings: JapanglifySettings
    ): String {
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
            base.append(padCenterDisplay(cell.base, cell.width))
            furi.append(padCenterDisplay(cell.furigana, cell.width))
            roma.append(padCenterDisplay(cell.romaji, cell.width))
        }
        val baseLine = base.toString()
        val furiLine = furi.toString()
        val romaLine = roma.toString()
        val lines = mutableListOf<String>()
        fun addFuri() {
            if (settings.includeFurigana && hasVisibleContent(furiLine)) {
                lines += protectLineStart(furiLine)
            }
        }
        fun addRoma() {
            if (settings.includeRomaji && hasVisibleContent(romaLine)) {
                lines += protectLineStart(romaLine)
            }
        }
        when (settings.romajiPosition) {
            RomajiPosition.ABOVE, RomajiPosition.BEFORE -> {
                addRoma()
                addFuri()
                lines += protectLineStart(baseLine)
            }
            RomajiPosition.BELOW, RomajiPosition.AFTER -> {
                addFuri()
                lines += protectLineStart(baseLine)
                addRoma()
            }
        }
        return lines.joinToString("\n")
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
        for (seg in segments) {
            val surface = seg.surface
            val furi = if (settings.includeFurigana) seg.furigana else null
            val roma = if (settings.includeRomaji) seg.romaji.orEmpty() else ""

            val isWordStart = !seg.isBoundToPrevious
            // Particles get their word-gap but can never be a wrap point —
            // see [InterlinearCell.canWrapBefore].
            val canWrapBefore = isWordStart && !seg.isParticle
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
                        isWordStart = false
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
                            canWrapBefore = canWrapBefore
                        )
                    } else {
                        out += splitKanjiFurigana(surface, furi, roma, settings, isWordStart, canWrapBefore)
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
                        canWrapBefore = canWrapBefore
                    )
                }
            }
        }
        return out
    }

    /**
     * Split e.g. 日本語/にほんご → (日,に)(本,ほん)(語,ご) and 食べ/たべ → (食,た)(べ,べ).
     * Romaji stays under the whole original token on the first cell only so
     * the latin line is readable (not letter-scattered).
     */
    private fun splitKanjiFurigana(
        surface: String,
        reading: String,
        romaji: String,
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
                canWrapBefore = canWrapBefore
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
                canWrapBefore = i == 0 && canWrapBefore
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

    private fun padCenterDisplay(text: String, targetWidth: Int): String {
        val w = displayWidth(text)
        if (w >= targetWidth) return text
        val diff = targetWidth - w
        val left = diff / 2
        val right = diff - left
        // Discord (and similar chat UIs) strip plain U+0020 spaces, which
        // silently destroys column alignment — pad with PAD (U+2800) instead,
        // same as padEndDisplay.
        return PAD.repeat(left) + text + PAD.repeat(right)
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
        val base = escapeHtml(seg.surface)

        if (f == null && r == null) return base

        // Double-sided ruby convention:
        //   <ruby><rb>BASE</rb><rt>furigana</rt><rtc>romaji</rtc></ruby>
        // ruby-position controlled via inline style for hosts without stylesheets.
        val rb = "<rb>$base</rb>"
        val rt = f?.let { "<rt>${escapeHtml(it)}</rt>" }.orEmpty()

        val rtc = r?.let {
            val pos = when (settings.romajiPosition) {
                RomajiPosition.ABOVE, RomajiPosition.BEFORE -> "over"
                RomajiPosition.BELOW, RomajiPosition.AFTER -> "under"
            }
            """<rtc style="ruby-position:$pos">${escapeHtml(it)}</rtc>"""
        }.orEmpty()

        // When only romaji is present, still use ruby so position applies.
        if (f == null && r != null) {
            val pos = when (settings.romajiPosition) {
                RomajiPosition.ABOVE, RomajiPosition.BEFORE -> "over"
                RomajiPosition.BELOW, RomajiPosition.AFTER -> "under"
            }
            return """<ruby style="ruby-position:$pos">$rb<rt>${escapeHtml(r)}</rt></ruby>"""
        }

        return "<ruby>$rb$rt$rtc</ruby>"
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
