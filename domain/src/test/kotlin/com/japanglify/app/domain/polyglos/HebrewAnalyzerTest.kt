package com.japanglify.app.domain.polyglos

import com.japanglify.app.domain.JapanglifySettings
import org.junit.Assert.assertTrue
import org.junit.Test

class HebrewAnalyzerTest {
    private val ancient = HebrewAnalyzer.ancient()
    private val settings = JapanglifySettings(includeRomaji = true, includeGlosses = true)

    @Test fun pointedHasPhonetic() {
        val segs = ancient.annotate("\u05e9\u05b8\u05dc\u05d5\u05b9\u05dd", settings)
        val romaji = segs.mapNotNull { it.romaji }.joinToString("")
        assertTrue("got=$romaji", romaji.isNotBlank())
    }

    @Test fun expandDoesNotThrow() {
        val segs = ancient.annotate("\u05d1\u05e8\u05d0\u05e9\u05d9\u05ea \u05d1\u05e8\u05d0", settings)
        assertTrue(segs.isNotEmpty())
    }
}