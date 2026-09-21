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