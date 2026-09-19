package com.japanglify.app.domain.polyglos

import com.japanglify.app.domain.JapanglifySettings
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GreekAnalyzerTest {
    private val ancient = GreekAnalyzer.ancient()
    private val settings = JapanglifySettings(includeRomaji = true, includeGlosses = true)

    @Test fun erasmianHasPhonetic() {
        val segs = ancient.annotate("\u03bb\u03cc\u03b3\u03bf\u03c2", settings.copy(greekRomanization = GreekRomanization.ERASMIAN))
        val romaji = segs.mapNotNull { it.romaji }.joinToString("")
        assertTrue("got=$romaji", romaji.contains("logos") || romaji.contains("logo"))
    }

    @Test fun sblDiffersFromErasmianOnUpsilon() {
        val word = "\u03ba\u03cd\u03c1\u03b9\u03bf\u03c2"
        val er = ancient.annotate(word, settings.copy(greekRomanization = GreekRomanization.ERASMIAN))
            .mapNotNull { it.romaji }.joinToString("")
        val sbl = ancient.annotate(word, settings.copy(greekRomanization = GreekRomanization.SBL))
            .mapNotNull { it.romaji }.joinToString("")
        assertNotEquals(er, sbl)
    }

    @Test fun glossSoftFail() {
        val segs = ancient.annotate("xyzzy", settings)
        assertTrue(segs.isNotEmpty())
    }
}