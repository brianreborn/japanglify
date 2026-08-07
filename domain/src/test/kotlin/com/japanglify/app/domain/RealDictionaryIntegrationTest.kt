package com.japanglify.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Regression coverage using the real Kuromoji/IPADIC dictionary — every
 * other test in this suite uses hand-crafted `AnnotatedSegment` fixtures
 * that bypass tokenization entirely. This exercises the full pipeline
 * (tokenize → resolve readings → render) against a genuine sentence,
 * captured verbatim (not written for testing) from a live Discord message
 * draft during this project's 2026-08-06 live-verification session — the
 * stored proof that the real dictionary-backed pipeline renders organic,
 * unscripted Japanese correctly, not just curated examples.
 */
class RealDictionaryIntegrationTest {

    private val engine = JapanglifyEngine(JapaneseAnalyzer(DemoMain.buildKuromojiProvider()))

    /** "[I] probably think that was the last chapter of Mark, preach the gospel to all creation." */
    private val sentence = "すべての被造物に福音を語れは、多分マルコの一番最後の章だったと思う。"

    private fun String.stripWordJoiner() = replace("⁠", "")

    @Test
    fun parentheticalExactOutput() {
        val out = engine.expand(sentence, JapanglifySettings(outputFormat = OutputFormat.PARENTHETICAL))
        assertEquals(
            "すべて（subete）の（no）被（ひ / hi）造物（ぞうぶつ / zoubutsu）に（ni）" +
                "福音（ふくいん / fukuin）を（o）語れ（かたれ / katare）は（wa）、（、 / ,）" +
                "多分（たぶん / tabun）マルコ（maruko）の（no）一番（いちばん / ichiban）" +
                "最後（さいご / saigo）の（no）章（しょう / shou）だっ（dat）た（ta）と（to）" +
                "思う（おもう / omou）。（。 / .）",
            out
        )
    }

    @Test
    fun everyOutputFormatRendersWithoutErrorAndKeepsTheOriginalWords() {
        // Kuromoji tokenizes 被造物 as two morphemes (被 / 造物, confirmed via
        // the parenthetical pin above), so check at token granularity, not
        // the human "word" 被造物 as one contiguous run.
        val tokens = listOf("被", "造物", "福音", "マルコ", "一番", "最後", "章", "思う")

        for (fmt in OutputFormat.entries) {
            val out = engine.expand(sentence, JapanglifySettings(outputFormat = fmt))
            assertTrue("$fmt produced blank output", out.isNotBlank())

            // INTERLINEAR legitimately interleaves PAD (U+2800) and Word
            // Joiner (U+2060) characters within and around cells for column
            // alignment / host-safe wrapping (see TripleScriptRenderer) —
            // that's correct behavior there, not token loss, so it's exempt
            // from the verbatim-substring check the other four formats get.
            if (fmt == OutputFormat.INTERLINEAR) continue
            for (token in tokens) {
                assertTrue("$fmt lost \"$token\" from the source text:\n$out", out.contains(token))
            }
        }
    }

    @Test
    fun interlinearRendersWithoutErrorAndKeepsTokensAfterStrippingLayoutArtifacts() {
        val out = engine.expand(sentence, JapanglifySettings(outputFormat = OutputFormat.INTERLINEAR))
        // Word Joiner (U+2060, wrap protection) and PAD (U+2800, both cell
        // padding and — since the space-around alignment refinement — the
        // gaps *between* characters within a multi-character cell) can both
        // legitimately land between two characters of the same token. Strip
        // both to recover the readable text before checking token survival;
        // this is layout artifact removal, not a claim those chars are
        // meaningless everywhere else.
        val stripped = out.stripWordJoiner().replace("⠀", "")
        for (token in listOf("被", "造物", "福音", "マルコ", "一番", "最後", "章", "思う")) {
            assertTrue("interlinear lost \"$token\" after stripping layout artifacts:\n$out", stripped.contains(token))
        }
    }

    @Test
    fun everyRomanizationSystemRendersWithoutError() {
        for (sys in RomanizationSystem.entries) {
            val out = engine.expand(
                sentence,
                JapanglifySettings(romanizationSystem = sys, outputFormat = OutputFormat.INTERLINEAR)
            )
            assertTrue("$sys produced blank output", out.isNotBlank())
        }
    }
}
