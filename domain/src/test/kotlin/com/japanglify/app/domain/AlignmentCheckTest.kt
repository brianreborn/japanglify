package com.japanglify.app.domain

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Offline alignment regression guard: renders a batch of diverse real
 * sentences with the device's actual settings and asserts that, for every
 * interlinear cell, the base glyph(s) and the furigana reading center on the
 * same column (within a tolerance that permits per-glyph bucketing of a
 * multi-kanji compound but not the gross drift the alignment bugs produced).
 *
 * Faithful now that the app and [DemoMain] share one
 * [KuromojiReadingProvider] -- so this JVM check genuinely predicts on-device
 * output, catching alignment-logic regressions here instead of by eyeballing
 * device screenshots. Each bug fixed while building this (extra-width
 * distribution, per-group wrap protection, per-glyph compound centering,
 * odd-pad tie-break direction, single-numeral readings) showed up as a cell
 * drifting well past [MAX_DRIFT] before its fix.
 */
class AlignmentCheckTest {

    /**
     * Max tolerated |center(base) - center(furigana)| in display columns.
     * A multi-kanji compound centered per-glyph (see
     * [TripleScriptRenderer.padCenterPerGlyph]) can legitimately shift its
     * WHOLE-cell centroid by up to ~1 unit while every individual glyph still
     * sits over its own reading; the alignment bugs this guards against drove
     * it to 2.5-3.0 (a reading floating entirely off its kanji).
     */
    private val MAX_DRIFT = 1.5

    private val analyzer = JapaneseAnalyzer(DemoMain.buildKuromojiProvider())
    private val renderer = TripleScriptRenderer()
    private val settings = JapanglifySettings(
        outputFormat = OutputFormat.INTERLINEAR,
        includeGlosses = true,
        includeEmoji = true,
        maxLineWidthFullwidth = 11
    )

    private fun width(s: String) = renderer.displayWidth(s)

    /**
     * Display width with ONLY the trailing elision annotation removed -- when
     * a row elides a redundant line, the marker is appended as `PAD + symbol`
     * to exactly one of the other lines (see [ElisionMarker] and
     * buildDisplayLines' two elision cases), making that line legitimately
     * wider than its siblings by that trailing annotation, which hangs off
     * the right end past the cell grid and shifts no column. Structural
     * trailing pads (a row-final kana cell has blank furigana, so the
     * furigana line ends in the grid pads for those columns) are NOT stripped
     * -- they ARE part of the grid width and must count, or an honest
     * width mismatch would be masked.
     */
    private fun contentWidth(text: String): Int {
        val s = text.replace("⁠", "")
        val elisionGlyphs = ElisionMarker.entries.mapNotNull { it.symbol?.singleOrNull() }.toSet()
        if (s.length >= 2 && s.last() in elisionGlyphs && s[s.length - 2] == '⠀') {
            return width(s.dropLast(2))
        }
        return width(s)
    }

    /** The substring of [line] covering display columns [startCol, endCol). */
    private fun sliceCols(line: String, startCol: Int, endCol: Int): String {
        val sb = StringBuilder()
        var col = 0
        var i = 0
        while (i < line.length) {
            val cp = line.codePointAt(i)
            val cc = Character.charCount(cp)
            val w = width(line.substring(i, i + cc))
            if (col >= startCol && col < endCol) sb.append(line, i, i + cc)
            col += w
            i += cc
        }
        return sb.toString()
    }

    /** Columns [lo, hi] (local to the slice) occupied by non-pad, non-joiner ink, or null if blank. */
    private fun inkSpan(slice: String): Pair<Int, Int>? {
        var col = 0
        var lo = Int.MAX_VALUE
        var hi = Int.MIN_VALUE
        var i = 0
        while (i < slice.length) {
            val cp = slice.codePointAt(i)
            val cc = Character.charCount(cp)
            val w = width(slice.substring(i, i + cc))
            val isInk = cp != 0x2800 && cp != 0x2060 && !Character.isWhitespace(cp)
            if (isInk) {
                if (col < lo) lo = col
                if (col + w - 1 > hi) hi = col + w - 1
            }
            col += w
            i += cc
        }
        return if (lo == Int.MAX_VALUE) null else lo to hi
    }

    private fun center(span: Pair<Int, Int>) = (span.first + span.second) / 2.0

    private val battery = listOf(
        "彼女は毎朝七時に起きて、コーヒーを飲みながら新聞を読む。",
        "お忙しいところご機嫌よう。田中さんは日本語を勉強する。",
        "昨日の夜、突然大きな地震が起きて、家族みんなでとても驚いた。",
        "眩しい朝日を浴びながら、恐ろしい速さで走る電車に乗り遅れないよう急いだ。",
        "図書館で借りた本を返しに行った帰り道、旧友と偶然出会った。",
        "来年の三月には東京の大学を卒業する予定です。",
        "２５歳の誕生日に十二本のバラをもらった。",
        "彼は毎日ジョギングとストレッチを欠かさない健康志向の人だ。",
        "会議は明日の午後三時から始まると部長がおっしゃいました。",
        "難しい問題を一生懸命考えたが、結局解けなかった。"
    )

    @Test
    fun everyCellCentersFuriganaOnItsBase() {
        val sentences = battery
        var problems = 0
        for (sentence in sentences) {
            println("\n=== $sentence ===")
            val segments = analyzer.annotate(sentence, settings)
            val measuredRows = renderer.buildMeasuredRows(segments, settings)
            val displayRows = renderer.buildInterlinearDisplayRows(segments, settings)
            require(measuredRows.size == displayRows.size)
            for ((rowIdx, cells) in measuredRows.withIndex()) {
                val lines = displayRows[rowIdx].lines
                val furiLine = lines.firstOrNull { it.role == TripleScriptRenderer.InterlinearLineRole.FURIGANA }
                    ?.text?.replace("⁠", "") ?: continue
                val baseLine = lines.firstOrNull { it.role == TripleScriptRenderer.InterlinearLineRole.BASE }
                    ?.text?.replace("⁠", "") ?: continue
                // Walk cells, tracking each cell's [start,end) columns: WORD_GAP
                // (2) precedes a new word-group's first cell (isWordStart, after
                // the first group); cells within a group abut.
                var col = 0
                var firstGroup = true
                for (cell in cells) {
                    if (cell.isWordStart) {
                        if (!firstGroup) col += 2
                        firstGroup = false
                    }
                    val start = col
                    col += cell.width
                    val end = col
                    if (cell.furigana.isEmpty() || cell.base.isEmpty()) continue
                    val baseSpan = inkSpan(sliceCols(baseLine, start, end)) ?: continue
                    val furiSpan = inkSpan(sliceCols(furiLine, start, end)) ?: continue
                    val drift = center(baseSpan) - center(furiSpan)
                    val flag = if (kotlin.math.abs(drift) > MAX_DRIFT) "   <-- DRIFT ${"%.1f".format(drift)}" else ""
                    if (flag.isNotEmpty()) problems++
                    println("  [${cell.base}] w=${cell.width} cols[$start,$end) base=$baseSpan furi[${cell.furigana}]=$furiSpan$flag")
                }
            }
        }
        println("\n>>> TOTAL DRIFTING CELLS: $problems")
        assertEquals("cells whose furigana drifts off its base by more than $MAX_DRIFT columns", 0, problems)
    }

    /**
     * Necessary condition for ANY column stacking: within one rendered row,
     * every present line (furigana/base/romaji/gloss/emoji) must have the
     * exact same display width. If they don't, no per-cell centering can save
     * the alignment -- the whole grid shears. Checked across both romaji
     * positions since ABOVE/BELOW take different assembly paths.
     */
    @Test
    fun everyRowsLinesHaveEqualDisplayWidth() {
        var mismatches = 0
        for (romajiPosition in listOf(RomajiPosition.BELOW, RomajiPosition.ABOVE)) {
            val cfg = settings.copy(romajiPosition = romajiPosition)
            for (sentence in battery) {
                val segments = analyzer.annotate(sentence, cfg)
                val displayRows = renderer.buildInterlinearDisplayRows(segments, cfg)
                for ((rowIdx, row) in displayRows.withIndex()) {
                    val widths = row.lines.map { it.role to contentWidth(it.text) }
                    val distinct = widths.map { it.second }.distinct()
                    if (distinct.size > 1) {
                        mismatches++
                        println("MISMATCH [$romajiPosition] \"$sentence\" row $rowIdx: $widths")
                    }
                }
            }
        }
        assertEquals("rows whose lines disagree on display width", 0, mismatches)
    }
}
