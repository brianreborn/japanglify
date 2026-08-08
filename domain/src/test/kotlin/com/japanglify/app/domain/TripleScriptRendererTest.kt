package com.japanglify.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TripleScriptRendererTest {

    private val renderer = TripleScriptRenderer()

    /**
     * Interlinear output embeds U+2060 (Word Joiner) between characters to
     * stop hosts from line-wrapping mid-cell (see [TripleScriptRenderer]'s
     * preventLineWrap). It's zero-width/invisible, so content assertions
     * strip it first rather than caring about its exact placement.
     */
    private fun String.stripWordJoiner() = replace("⁠", "")

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

    /**
     * Ground-truth pin for a real screenshot report ("日本語を勉強する" on a
     * Pixel 8 looked like the base row was indented right of furigana/romaji
     * — see NOTES.md). Confirms the PAD-count math itself is correct and
     * intentional: the cell width is dominated by the mora-hyphenated
     * romaji ("ni·ho·n·go", 10 half-units), so *center*-aligning the
     * narrower base ("日本語", 6) and furigana ("にほんご", 8) against that
     * width necessarily gives base more padding than furigana (2 PAD chars
     * vs. 1 per side) — that asymmetry is by design, not a bug. Whether
     * U+2800 (PAD)'s *real* rendered width on a given host font matches the
     * width-1 this renderer assumes for it is a separate, unverified
     * question this test can't answer (needs a live device/font, not a JVM
     * unit test) — see the NOTES.md item this backs.
     */
    @Test
    fun interlinearCenterPaddingDistributesAroundCharactersNotAtEdges() {
        val segments = listOf(
            AnnotatedSegment(
                surface = "日本語",
                furigana = "にほんご",
                romaji = "nihongo",
                romajiSyllables = "ni·ho·n·go",
                needsFurigana = true
            )
        )
        val settings = JapanglifySettings(
            outputFormat = OutputFormat.INTERLINEAR,
            romajiPosition = RomajiPosition.BELOW,
            maxLineWidthFullwidth = 0
        )
        val lines = renderer.render(segments, settings).lines().map { it.replace("⁠", "") }
        val (furiLine, baseLine, romaLine) = Triple(lines[0], lines[1], lines[2])

        // Cell width 10 (set by "ni·ho·n·go"). Romaji needs no padding.
        // Furigana (8-wide, 4 codepoints) has only 2 half-units of slack
        // spread across 5 gap positions — too little for any interior gap
        // to round up to a whole PAD unit, so it stays edge-only, same as
        // the old algorithm would have produced.
        assertEquals("ni·ho·n·go", romaLine)
        assertEquals("⠀にほんご⠀", furiLine)
        // Base (6-wide, 3 codepoints) has 4 half-units of slack spread
        // across 4 gap positions — exactly 1 PAD unit each, so it now
        // fills the column evenly instead of clustering as "⠀⠀日本語⠀⠀".
        // Its first PAD-run (1 unit) now matches furigana's (1 unit), so
        // 日 and に line up at the same horizontal start position.
        assertEquals("⠀日⠀本⠀語⠀", baseLine)

        val w = renderer.displayWidth(lines[0])
        assertEquals(w, renderer.displayWidth(lines[1]))
        assertEquals(w, renderer.displayWidth(lines[2]))
    }

    @Test
    fun padCenterDistributionDegeneratesToEdgesForSingleCharacterText() {
        // N=1 codepoint has exactly one interior-free layout (left/right
        // edge only) — must match the pre-refinement behavior exactly,
        // since this is the common case for the split-kanji-per-column path:
        // includeRomaji=false routes a kanji+okurigana word through
        // splitKanjiFurigana, giving each kanji its own single-character cell.
        val segments = listOf(
            AnnotatedSegment("食べる", "たべる", romaji = null, needsFurigana = true)
        )
        val settings = JapanglifySettings(
            outputFormat = OutputFormat.INTERLINEAR,
            romajiPosition = RomajiPosition.BELOW,
            includeRomaji = false,
            maxLineWidthFullwidth = 0
        )
        val lines = renderer.render(segments, settings).lines().map { it.replace("⁠", "") }
        // "食" (base, width 2) centered against "た" (furigana for 食, width
        // 2) — equal width, no padding needed, both flush. The follow-on
        // okurigana cell "べる" has no furigana shown (kanji-only default)
        // and needs no centering either. Nothing here should exercise
        // interior distribution; if it did, this single-codepoint cell
        // would still only ever get edge padding by construction.
        assertTrue("expected single-kanji base cell, got: ${lines[1]}", lines[1].contains("食"))
        assertTrue(!lines[1].contains("⠀食⠀⠀") && !lines[1].contains("⠀⠀食⠀"))
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
        val furiText = lines[0].stripWordJoiner()
        val baseText = lines[1].stripWordJoiner()
        val romaText = lines[2].stripWordJoiner()
        assertTrue(furiText.contains("に") || furiText.contains("ほん") || furiText.contains("ご") || furiText.contains("ﾆ"))
        assertTrue(baseText.contains("日") && baseText.contains("本") && baseText.contains("語"))
        assertTrue(romaText.contains("nihongo"))
        // All three lines must share the same display width (aligned columns)
        val w = renderer.displayWidth(lines[0])
        assertEquals(w, renderer.displayWidth(lines[1]))
        assertEquals(w, renderer.displayWidth(lines[2]))
        assertTrue(baseText.contains("日"))
        assertTrue(romaText.contains("nihongo"))
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
    fun interlinearNeverWrapsBetweenAdjacentKanaWordsWithNoParticleBetween() {
        // お+ねがい+します: three separate Kuromoji tokens, all pure kana,
        // none flagged as a particle -- with no kanji or particle boundary
        // anywhere, none of them should be a valid wrap point, even at a
        // width narrow enough that every one of them would otherwise
        // overflow. A slightly-over-budget line is the accepted tradeoff
        // (see packCellsIntoRows), not a mid-word split.
        val segments = listOf(
            AnnotatedSegment("お", null, "o", false),
            AnnotatedSegment("ねがい", null, "negai", false),
            AnnotatedSegment("します", null, "shimasu", false)
        )
        val settings = JapanglifySettings(
            outputFormat = OutputFormat.INTERLINEAR,
            romajiPosition = RomajiPosition.BELOW,
            maxLineWidthFullwidth = 1
        )
        val out = renderer.render(segments, settings)
        assertFalse("expected a single unbroken block:\n$out", out.contains("\n\n"))
    }

    @Test
    fun interlinearAllowsWrapRightAfterAParticleWithinAKanaRun() {
        // を is a real particle boundary -- wrapping right after it is fine
        // even though both sides are pure kana with no kanji involved.
        val segments = listOf(
            AnnotatedSegment("これ", null, "kore", false),
            AnnotatedSegment("を", null, "o", false, isParticle = true),
            AnnotatedSegment("ください", null, "kudasai", false)
        )
        val settings = JapanglifySettings(
            outputFormat = OutputFormat.INTERLINEAR,
            romajiPosition = RomajiPosition.BELOW,
            maxLineWidthFullwidth = 2
        )
        val out = renderer.render(segments, settings)
        assertTrue("expected a wrap after を:\n$out", out.contains("\n\n"))
        val firstBlock = out.split("\n\n").first()
        assertTrue(firstBlock.stripWordJoiner().contains("これ"))
        assertTrue(firstBlock.stripWordJoiner().contains("を"))
        assertFalse(firstBlock.stripWordJoiner().contains("ください"))
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
        assertTrue(lines[0].stripWordJoiner().contains("こんにちは") && lines[0].contains("。"))
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
        // <rb>/<rtc> were dropped from the HTML Living Standard years ago and
        // (confirmed via live browser testing) render as unstyled plain text,
        // not a positioned ruby tier — so this format must not emit them.
        // Furigana uses real <ruby><rt> (the one thing every browser gets
        // right); romaji rides along as a plain block span, sized down to
        // match, not a second ruby tier — ruby-position:under proved
        // unreliable for a nested ruby's outer tier in that same testing.
        val below = JapanglifySettings(
            outputFormat = OutputFormat.HTML_RUBY,
            romajiPosition = RomajiPosition.BELOW
        )
        val outBelow = renderer.render(nihongo, below)
        assertTrue(outBelow.contains("<ruby>日本語<rt>にほんご</rt></ruby>"))
        assertFalse(outBelow.contains("<rb>"))
        assertFalse(outBelow.contains("<rtc"))
        assertTrue(outBelow.contains("display:block"))
        assertTrue(outBelow.contains("nihongo"))
        // romaji block comes after the ruby element for BELOW
        assertTrue(
            outBelow.indexOf("</ruby>") < outBelow.indexOf("nihongo")
        )

        val above = below.copy(romajiPosition = RomajiPosition.ABOVE)
        val outAbove = renderer.render(nihongo, above)
        // romaji block comes before the ruby element for ABOVE
        assertTrue(
            outAbove.indexOf("nihongo") < outAbove.indexOf("<ruby>")
        )
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

    // ── Word/particle glosses (Phase 3 of the dictionary-lookup feature) ──

    private val nihongoWithGloss = listOf(
        AnnotatedSegment(
            surface = "日本語",
            furigana = "にほんご",
            romaji = "nihongo",
            needsFurigana = true,
            gloss = "Japanese"
        )
    )

    private val sentenceWithGloss = listOf(
        AnnotatedSegment("日本語", "にほんご", "nihongo", true, gloss = "Japanese"),
        AnnotatedSegment("を", null, "o", false, isParticle = true, gloss = "object marker"),
        AnnotatedSegment("勉強", "べんきょう", "benkyou", true, gloss = "study"),
        AnnotatedSegment("する", null, "suru", false, gloss = "to do")
    )

    @Test
    fun glossesOffByDefaultAcrossEveryFormat() {
        // includeGlosses defaults to false — no format should ever emit
        // gloss text unless it's explicitly turned on, even when every
        // segment has one attached.
        for (fmt in OutputFormat.entries) {
            val settings = JapanglifySettings(outputFormat = fmt)
            val out = renderer.render(nihongoWithGloss, settings)
            assertFalse("$fmt leaked gloss text with includeGlosses off:\n$out", out.contains("Japanese"))
        }
    }

    @Test
    fun parentheticalAppendsGlossAfterReadings() {
        val settings = JapanglifySettings(
            outputFormat = OutputFormat.PARENTHETICAL,
            romajiPosition = RomajiPosition.BELOW,
            includeGlosses = true
        )
        val out = renderer.render(nihongoWithGloss, settings)
        assertEquals("日本語（にほんご / nihongo / Japanese）", out)
    }

    @Test
    fun furiganaInlineAddsThirdGlossLine() {
        val settings = JapanglifySettings(
            outputFormat = OutputFormat.FURIGANA_INLINE,
            romajiPosition = RomajiPosition.BELOW,
            includeGlosses = true
        )
        val out = renderer.render(nihongoWithGloss, settings)
        assertEquals("日本語《にほんご》\nnihongo\nJapanese", out)
    }

    @Test
    fun compactBracketsAddsGlossBraces() {
        val settings = JapanglifySettings(outputFormat = OutputFormat.COMPACT, includeGlosses = true)
        val out = renderer.render(nihongoWithGloss, settings)
        assertEquals("日本語〔にほんご〕[nihongo]{Japanese}", out)
    }

    @Test
    fun interlinearAddsFourthGlossRow() {
        val settings = JapanglifySettings(
            outputFormat = OutputFormat.INTERLINEAR,
            romajiPosition = RomajiPosition.BELOW,
            includeGlosses = true,
            maxLineWidthFullwidth = 0
        )
        val out = renderer.render(sentenceWithGloss, settings).stripWordJoiner()
        val lines = out.lines()
        assertEquals(4, lines.size)
        val glossLine = lines[3]
        assertTrue(glossLine.contains("Japanese"))
        assertTrue(glossLine.contains("object marker"))
        assertTrue(glossLine.contains("study"))
        assertTrue(glossLine.contains("to do"))
    }

    @Test
    fun interlinearGlossRowOmittedWhenNoSegmentHasOne() {
        val settings = JapanglifySettings(
            outputFormat = OutputFormat.INTERLINEAR,
            includeGlosses = true
        )
        // nihongo (no gloss attribute set) -- includeGlosses is on, but
        // there's nothing to show, so the row must not appear as a blank line.
        val out = renderer.render(nihongo, settings)
        assertEquals(3, out.lines().size)
    }

    @Test
    fun interlinearGlossIsWordLevelNotPerCharacter() {
        // With romaji off, kanji words split into one cell per character
        // (see splitKanjiFurigana) -- gloss must still appear exactly once
        // per word, not once per kanji character.
        val settings = JapanglifySettings(
            outputFormat = OutputFormat.INTERLINEAR,
            includeRomaji = false,
            includeGlosses = true,
            maxLineWidthFullwidth = 0
        )
        val out = renderer.render(nihongoWithGloss, settings).stripWordJoiner()
        val glossLine = out.lines().last()
        assertEquals(1, Regex("Japanese").findAll(glossLine).count())
    }

    @Test
    fun htmlRubyAddsThirdBlockSpanForGloss() {
        val settings = JapanglifySettings(
            outputFormat = OutputFormat.HTML_RUBY,
            romajiPosition = RomajiPosition.BELOW,
            includeGlosses = true
        )
        val out = renderer.render(nihongoWithGloss, settings)
        assertTrue(out.contains("<ruby>日本語<rt>にほんご</rt></ruby>"))
        assertFalse(out.contains("<rtc"))
        assertTrue(out.contains("nihongo"))
        assertTrue(out.contains("Japanese"))
        // Gloss always trails, after wherever romaji landed (BELOW here).
        assertTrue(out.indexOf("nihongo") < out.indexOf("Japanese"))
    }

    @Test
    fun htmlRubyGlossTrailsEvenWhenRomajiIsAbove() {
        val settings = JapanglifySettings(
            outputFormat = OutputFormat.HTML_RUBY,
            romajiPosition = RomajiPosition.ABOVE,
            includeGlosses = true
        )
        val out = renderer.render(nihongoWithGloss, settings)
        // romaji before the ruby element, but gloss still comes last overall.
        assertTrue(out.indexOf("nihongo") < out.indexOf("<ruby>"))
        assertTrue(out.indexOf("<ruby>") < out.indexOf("Japanese"))
    }

    @Test
    fun htmlRubyGlossOnlyNoFuriganaNoRomajiStillWraps() {
        val settings = JapanglifySettings(
            outputFormat = OutputFormat.HTML_RUBY,
            includeFurigana = false,
            includeRomaji = false,
            includeGlosses = true
        )
        val out = renderer.render(nihongoWithGloss, settings)
        assertFalse(out.contains("<ruby>"))
        assertTrue(out.contains("日本語"))
        assertTrue(out.contains("Japanese"))
    }

    // ── Emoji (English→emoji annotation) ────────────────────────────

    // gloss = null here on purpose: JapaneseAnalyzer nulls it out by default
    // once a precise emoji match is found (see JapaneseAnalyzerTest's
    // elision tests) -- the renderer just displays whatever it's given, so
    // this fixture exercises exactly that "elided" shape.
    private val nihongoWithEmoji = listOf(
        AnnotatedSegment(
            surface = "日本語",
            furigana = "にほんご",
            romaji = "nihongo",
            needsFurigana = true,
            gloss = null,
            emoji = "🇯🇵"
        )
    )

    // Both gloss and emoji set -- the "always show both" shape.
    private val nihongoWithGlossAndEmoji = listOf(
        AnnotatedSegment(
            surface = "日本語",
            furigana = "にほんご",
            romaji = "nihongo",
            needsFurigana = true,
            gloss = "Japanese",
            emoji = "🇯🇵"
        )
    )

    private val sentenceWithEmoji = listOf(
        AnnotatedSegment("日本語", "にほんご", "nihongo", true, gloss = null, emoji = "🇯🇵"),
        AnnotatedSegment("を", null, "o", false, isParticle = true, gloss = "object marker"),
        AnnotatedSegment("勉強", "べんきょう", "benkyou", true, gloss = "study"),
        AnnotatedSegment("する", null, "suru", false, gloss = "to do")
    )

    @Test
    fun emojiOffByDefaultAcrossEveryFormat() {
        // includeEmoji defaults to false — no format should ever emit the
        // emoji even when a segment has one attached.
        for (fmt in OutputFormat.entries) {
            val settings = JapanglifySettings(outputFormat = fmt)
            val out = renderer.render(nihongoWithEmoji, settings)
            assertFalse("$fmt leaked emoji with includeEmoji off:\n$out", out.contains("🇯🇵"))
        }
    }

    @Test
    fun parentheticalAppendsEmojiAfterGloss() {
        val settings = JapanglifySettings(
            outputFormat = OutputFormat.PARENTHETICAL,
            romajiPosition = RomajiPosition.BELOW,
            includeGlosses = true,
            includeEmoji = true
        )
        val out = renderer.render(nihongoWithGlossAndEmoji, settings)
        assertEquals("日本語（にほんご / nihongo / Japanese / 🇯🇵）", out)
    }

    @Test
    fun furiganaInlineAddsFourthEmojiLine() {
        val settings = JapanglifySettings(
            outputFormat = OutputFormat.FURIGANA_INLINE,
            romajiPosition = RomajiPosition.BELOW,
            includeGlosses = true,
            includeEmoji = true
        )
        val out = renderer.render(nihongoWithGlossAndEmoji, settings)
        assertEquals("日本語《にほんご》\nnihongo\nJapanese\n🇯🇵", out)
    }

    @Test
    fun furiganaInlineShowsOnlyEmojiWhenGlossElided() {
        val settings = JapanglifySettings(
            outputFormat = OutputFormat.FURIGANA_INLINE,
            romajiPosition = RomajiPosition.BELOW,
            includeGlosses = true,
            includeEmoji = true
        )
        val out = renderer.render(nihongoWithEmoji, settings)
        assertEquals("日本語《にほんご》\nnihongo\n🇯🇵", out)
    }

    @Test
    fun compactBracketsAddsEmojiAfterGlossBraces() {
        val settings = JapanglifySettings(
            outputFormat = OutputFormat.COMPACT,
            includeGlosses = true,
            includeEmoji = true
        )
        val out = renderer.render(nihongoWithGlossAndEmoji, settings)
        assertEquals("日本語〔にほんご〕[nihongo]{Japanese}🇯🇵", out)
    }

    @Test
    fun interlinearAddsFifthEmojiRowAfterGloss() {
        val settings = JapanglifySettings(
            outputFormat = OutputFormat.INTERLINEAR,
            romajiPosition = RomajiPosition.BELOW,
            includeGlosses = true,
            includeEmoji = true,
            maxLineWidthFullwidth = 0
        )
        val out = renderer.render(sentenceWithEmoji, settings).stripWordJoiner()
        val lines = out.lines()
        assertEquals(5, lines.size)
        assertTrue(lines[3].contains("object marker"))
        assertTrue(lines[4].contains("🇯🇵"))
    }

    @Test
    fun interlinearEmojiRowOmittedWhenNoSegmentHasOne() {
        val settings = JapanglifySettings(
            outputFormat = OutputFormat.INTERLINEAR,
            includeGlosses = true,
            includeEmoji = true
        )
        // nihongo (no gloss/emoji attributes set) -- includeEmoji is on, but
        // there's nothing to show, so the row must not appear as a blank line.
        val out = renderer.render(nihongo, settings)
        assertEquals(3, out.lines().size)
    }

    @Test
    fun interlinearEmojiIsWordLevelNotPerCharacter() {
        val settings = JapanglifySettings(
            outputFormat = OutputFormat.INTERLINEAR,
            includeRomaji = false,
            includeGlosses = true,
            includeEmoji = true,
            maxLineWidthFullwidth = 0
        )
        val out = renderer.render(nihongoWithEmoji, settings).stripWordJoiner()
        val emojiLine = out.lines().last()
        assertEquals(1, Regex("🇯🇵").findAll(emojiLine).count())
    }

    @Test
    fun htmlRubyAddsFourthBlockSpanForEmojiAfterGloss() {
        val settings = JapanglifySettings(
            outputFormat = OutputFormat.HTML_RUBY,
            romajiPosition = RomajiPosition.BELOW,
            includeGlosses = true,
            includeEmoji = true
        )
        val out = renderer.render(nihongoWithGlossAndEmoji, settings)
        assertTrue(out.contains("<ruby>日本語<rt>にほんご</rt></ruby>"))
        assertTrue(out.contains("Japanese"))
        assertTrue(out.contains("🇯🇵"))
        // Order: romaji, then ruby, then gloss, then emoji (BELOW here).
        assertTrue(out.indexOf("Japanese") < out.indexOf("🇯🇵"))
    }
}
