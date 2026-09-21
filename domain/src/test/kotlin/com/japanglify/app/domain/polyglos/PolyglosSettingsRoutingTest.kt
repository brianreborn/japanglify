package com.japanglify.app.domain.polyglos

import com.japanglify.app.domain.JapaneseAnalyzer
import com.japanglify.app.domain.JapanglifySettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PolyglosSettingsRoutingTest {
    private val engine = PolyglosEngine.withJapanese(JapaneseAnalyzer())

    @Test fun mixedInputSeparateWordsEachLanguageAnalyzed() {
        val segs = engine.annotate(
            "\u03bb\u03cc\u03b3\u03bf\u03c2 \u65e5\u672c\u8a9e",
            JapanglifySettings(includeRomaji = true)
        )
        val greek = segs.first { it.surface.contains("\u03bb") }
        val japanese = segs.first { it.surface.contains("\u65e5") }
        assertEquals(AnalyzerIds.GRC_ANCIENT, greek.script)
        assertTrue(greek.hasRomaji)
        assertEquals(AnalyzerIds.JA, japanese.script)
        assertTrue(japanese.hasFurigana || japanese.hasRomaji)
    }

    @Test fun mixedScriptWordIsPassthrough() {
        // CJK + Greek + CJK in one token: do not romanize or furigana.
        val word = "\u65e5\u03b1\u8a9e"
        val segs = engine.annotate(word, JapanglifySettings(includeRomaji = true))
        assertEquals(1, segs.size)
        assertEquals(word, segs[0].surface)
        assertFalse(segs[0].hasRomaji)
        assertFalse(segs[0].hasFurigana)
        assertEquals(null, segs[0].script)
    }

    @Test fun unknownLatinWordIsPassthrough() {
        val segs = engine.annotate("hello", JapanglifySettings(includeRomaji = true))
        assertEquals("hello", segs.single().surface)
        assertFalse(segs.single().hasRomaji)
    }

    @Test fun forceAncientGreekOnLatinNoiseStillGreekWhenForced() {
        // Forced analyzer wins over detection.
        val segs = engine.annotate(
            "\u03bb\u03cc\u03b3\u03bf\u03c2",
            JapanglifySettings(includeRomaji = true, forceAnalyzerId = AnalyzerIds.GRC_ANCIENT)
        )
        assertEquals(AnalyzerIds.GRC_ANCIENT, segs.first().script)
        assertTrue(segs.any { !it.romaji.isNullOrBlank() })
    }

    @Test fun erasmianVsSblSettingsChangeOutput() {
        val word = "\u03ba\u03cd\u03c1\u03b9\u03bf\u03c2"
        val er = engine.expand(
            word,
            JapanglifySettings(
                includeRomaji = true,
                forceAnalyzerId = AnalyzerIds.GRC_ANCIENT,
                greekRomanization = GreekRomanization.ERASMIAN
            )
        )
        val sbl = engine.expand(
            word,
            JapanglifySettings(
                includeRomaji = true,
                forceAnalyzerId = AnalyzerIds.GRC_ANCIENT,
                greekRomanization = GreekRomanization.SBL
            )
        )
        assertNotEquals(er, sbl)
        assertTrue(er.isNotBlank() && sbl.isNotBlank())
    }

    @Test fun hebrewPointedGetsPhonetic() {
        val out = engine.expand(
            "\u05e9\u05b8\u05dc\u05d5\u05b9\u05dd",
            JapanglifySettings(includeRomaji = true, forceAnalyzerId = AnalyzerIds.HBO)
        )
        assertTrue(out.isNotBlank())
        assertFalse(out == "\u05e9\u05b8\u05dc\u05d5\u05b9\u05dd") // phonetic layer should alter/expand
    }

    @Test fun japaneseStillWorksUnderAuto() {
        val out = engine.expand(
            "\u65e5\u672c\u8a9e",
            JapanglifySettings(includeRomaji = true, includeFurigana = true)
        )
        assertTrue(out.isNotBlank())
    }
}