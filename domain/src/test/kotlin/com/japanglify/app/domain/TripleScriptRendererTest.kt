package com.japanglify.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TripleScriptRendererTest {

    private val renderer = TripleScriptRenderer()

    private val nihongo = listOf(
        AnnotatedSegment(
            surface = "日本語",
            furigana = "にほんご",
            romaji = "nihongo",
            needsFurigana = true
        )
    )

    @Test
    fun furiganaInlineReadable() {
        val settings = JapanglifySettings(
            outputFormat = OutputFormat.FURIGANA_INLINE,
            romajiPosition = RomajiPosition.BELOW
        )
        val out = renderer.render(nihongo, settings)
        assertEquals("日本語《にほんご》\nnihongo", out)
    }

    @Test
    fun parentheticalDefault() {
        val settings = JapanglifySettings(
            outputFormat = OutputFormat.PARENTHETICAL,
            romajiPosition = RomajiPosition.BELOW
        )
        val out = renderer.render(nihongo, settings)
        assertEquals("日本語（にほんご / nihongo）", out)
    }

    @Test
    fun parentheticalRomajiBefore() {
        val settings = JapanglifySettings(
            outputFormat = OutputFormat.PARENTHETICAL,
            romajiPosition = RomajiPosition.BEFORE
        )
        val out = renderer.render(nihongo, settings)
        assertEquals("（nihongo / にほんご）日本語", out)
    }

    @Test
    fun interlinearFuriganaOverRomajiUnder() {
        val settings = JapanglifySettings(
            outputFormat = OutputFormat.INTERLINEAR,
            romajiPosition = RomajiPosition.BELOW
        )
        val out = renderer.render(nihongo, settings)
        val lines = out.lines()
        assertEquals(3, lines.size)
        // Readings over kanji (hiragana / compact readings)
        assertTrue(lines[0].contains("に") || lines[0].contains("ほん") || lines[0].contains("ご") || lines[0].contains("ﾆ"))
        assertTrue(lines[1].contains("日") && lines[1].contains("本") && lines[1].contains("語"))
        assertTrue(lines[2].contains("nihongo"))
        // All three lines must share the same display width (aligned columns)
        val w = renderer.displayWidth(lines[0])
        assertEquals(w, renderer.displayWidth(lines[1]))
        assertEquals(w, renderer.displayWidth(lines[2]))
        assertTrue(lines[1].contains("日"))
        assertTrue(lines[2].contains("nihongo"))
    }

    @Test
    fun interlinearMultiSegmentColumnsAlign() {
        val segments = listOf(
            AnnotatedSegment("日本語", "にほんご", "nihongo", true),
            AnnotatedSegment("を", null, "o", false),
            AnnotatedSegment("勉強", "べんきょう", "benkyou", true),
            AnnotatedSegment("する", null, "suru", false)
        )
        val settings = JapanglifySettings(
            outputFormat = OutputFormat.INTERLINEAR,
            romajiPosition = RomajiPosition.BELOW,
            furiganaKanjiOnly = true,
            maxLineWidthFullwidth = 0 // unlimited for this alignment check
        )
        val out = renderer.render(segments, settings)
        val lines = out.lines().filter { it.isNotEmpty() }
        assertEquals(3, lines.size)
        val w0 = renderer.displayWidth(lines[0])
        assertEquals("furi/base display width", w0, renderer.displayWidth(lines[1]))
        assertEquals("base/roma display width", w0, renderer.displayWidth(lines[2]))
        assertTrue(!lines[1].contains('\u00A0'))
        // Cell padding AND the word-gap must both use PAD (U+2800), never ' ' --
        // Discord and similar chat UIs collapse/strip runs of ASCII space,
        // desyncing column widths between the three rows.
        for (line in lines) {
            assertTrue("cell padding leaked ASCII space into: '$line'", !line.contains(' '))
        }
        // Furigana-style: top line has readings, not particles "を"/"する"
        assertTrue(lines[0].contains("に") || lines[0].contains("べん") || lines[0].contains("ﾆ"))
        assertTrue(!lines[0].contains("を"))
        assertTrue(!lines[0].contains("する"))
    }

    @Test
    fun interlinearLeadingPadSurvivesDiscordStyleTrim() {
        // First furigana cell empty over pure-kana "を" would create "leading"
        // blanks — must not be U+0020/NBSP (Discord strips those).
        val segments = listOf(
            AnnotatedSegment("を", null, "o", false),
            AnnotatedSegment("食べる", "たべる", "taberu", true)
        )
        val settings = JapanglifySettings(
            outputFormat = OutputFormat.INTERLINEAR,
            romajiPosition = RomajiPosition.BELOW,
            furiganaKanjiOnly = true
        )
        val out = renderer.render(segments, settings)
        val furi = out.lines().first()
        // After Discord-style trim of whitespace, pad must remain
        val discordTrimmed = furi.trimStart { it == ' ' || it == '\u00A0' || it == '\u3000' }
        assertEquals(
            "expected the line to start with PAD (U+2800) after Discord-style trim, got: " +
                discordTrimmed.map { it.code }.joinToString(),
            TripleScriptRenderer.PAD[0],
            discordTrimmed.firstOrNull()
        )
        // Line display widths still match
        val lines = out.lines()
        val w = renderer.displayWidth(lines[0])
        assertEquals(w, renderer.displayWidth(lines[1]))
        assertEquals(w, renderer.displayWidth(lines[2]))
    }

    @Test
    fun interlinearWrapsAtMaxLineWidth() {
        val segments = listOf(
            AnnotatedSegment("日本語", "にほんご", "nihongo", true),
            AnnotatedSegment("を", null, "o", false),
            AnnotatedSegment("勉強", "べんきょう", "benkyou", true),
            AnnotatedSegment("する", null, "suru", false),
            AnnotatedSegment("。", "。", ".", false)
        )
        val settings = JapanglifySettings(
            outputFormat = OutputFormat.INTERLINEAR,
            romajiPosition = RomajiPosition.BELOW,
            maxLineWidthFullwidth = 8 // force at least one wrap for this phrase
        )
        val out = renderer.render(segments, settings)
        // Multiple triple-line blocks separated by blank line
        assertTrue("expected wrap into multiple blocks:\n$out", out.contains("\n\n"))
        val blocks = out.split("\n\n")
        assertTrue(blocks.size >= 2)
        // Each block has equal-width lines
        for (block in blocks) {
            val lines = block.lines().filter { it.isNotEmpty() }
            if (lines.size < 2) continue
            val w = renderer.displayWidth(lines[0])
            for (line in lines) {
                assertEquals(w, renderer.displayWidth(line))
            }
        }
    }

    @Test
    fun interlinearPunctuationHiddenFromFuriganaRowByDefault() {
        // Punctuation has no reading, so by default (FuriganaPunctuationStyle.NONE)
        // it should not appear on the furigana row at all — and since nothing
        // else in this phrase needs furigana either, the row is fully blank
        // and gets dropped, leaving just base + romaji.
        val segments = listOf(
            AnnotatedSegment("こんにちは", null, "konnichiha", false),
            AnnotatedSegment("。", "。", ".", false)
        )
        val settings = JapanglifySettings(
            outputFormat = OutputFormat.INTERLINEAR,
            romajiPosition = RomajiPosition.BELOW
        )
        assertEquals(FuriganaPunctuationStyle.NONE, settings.furiganaPunctuationStyle)
        val out = renderer.render(segments, settings)
        val lines = out.lines()
        assertEquals(2, lines.size)
        assertTrue(lines[0].contains("こんにちは") && lines[0].contains("。"))
        assertTrue(lines[1].contains("."))
    }

    @Test
    fun interlinearPunctuationOriginalStyleOnFuriganaRow() {
        val segments = listOf(
            AnnotatedSegment("こんにちは", null, "konnichiha", false),
            AnnotatedSegment("。", "。", ".", false)
        )
        val settings = JapanglifySettings(
            outputFormat = OutputFormat.INTERLINEAR,
            romajiPosition = RomajiPosition.BELOW,
            furiganaPunctuationStyle = FuriganaPunctuationStyle.ORIGINAL
        )
        val out = renderer.render(segments, settings)
        val lines = out.lines()
        assertEquals(3, lines.size)
        assertTrue(lines[0].contains("。"))
        assertTrue(lines[2].contains("."))
    }

    @Test
    fun interlinearPunctuationRomanStyleOnFuriganaRow() {
        val segments = listOf(
            AnnotatedSegment("こんにちは", null, "konnichiha", false),
            AnnotatedSegment("。", "。", ".", false)
        )
        val settings = JapanglifySettings(
            outputFormat = OutputFormat.INTERLINEAR,
            romajiPosition = RomajiPosition.BELOW,
            furiganaPunctuationStyle = FuriganaPunctuationStyle.ROMAN
        )
        val out = renderer.render(segments, settings)
        val lines = out.lines()
        assertEquals(3, lines.size)
        assertTrue("expected Latin '.' on furigana row, got: ${lines[0]}", lines[0].contains("."))
        assertTrue(!lines[0].contains("。"))
    }

    @Test
    fun htmlDoubleSidedRuby() {
        val settings = JapanglifySettings(
            outputFormat = OutputFormat.HTML_RUBY,
            romajiPosition = RomajiPosition.BELOW
        )
        val out = renderer.render(nihongo, settings)
        assertTrue(out.contains("<ruby>"))
        assertTrue(out.contains("<rb>日本語</rb>"))
        assertTrue(out.contains("<rt>にほんご</rt>"))
        assertTrue(out.contains("<rtc"))
        assertTrue(out.contains("nihongo"))
        assertTrue(out.contains("ruby-position:under"))
    }

    @Test
    fun compactBrackets() {
        val settings = JapanglifySettings(outputFormat = OutputFormat.COMPACT)
        val out = renderer.render(nihongo, settings)
        assertEquals("日本語〔にほんご〕[nihongo]", out)
    }

    @Test
    fun furiganaOnly() {
        val settings = JapanglifySettings(
            includeRomaji = false,
            outputFormat = OutputFormat.PARENTHETICAL
        )
        assertEquals("日本語（にほんご）", renderer.render(nihongo, settings))
    }

    @Test
    fun verticalHtmlUsesWritingMode() {
        val settings = JapanglifySettings(
            outputFormat = OutputFormat.HTML_RUBY,
            writingOrientation = WritingOrientation.VERTICAL
        )
        val out = renderer.render(nihongo, settings)
        assertTrue(out.contains("writing-mode:vertical-rl"))
    }
}
