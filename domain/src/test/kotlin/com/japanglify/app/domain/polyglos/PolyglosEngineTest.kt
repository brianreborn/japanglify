package com.japanglify.app.domain.polyglos

import com.japanglify.app.domain.JapaneseAnalyzer
import com.japanglify.app.domain.JapanglifySettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PolyglosEngineTest {
    private val engine = PolyglosEngine.withJapanese(JapaneseAnalyzer())

    @Test fun routesGreek() {
        val segs = engine.annotate("\u03bb\u03cc\u03b3\u03bf\u03c2", JapanglifySettings(includeRomaji = true))
        assertTrue(segs.any { it.romaji != null })
        assertEquals(AnalyzerIds.GRC_ANCIENT, segs.first().script)
    }

    @Test fun routesHebrew() {
        val segs = engine.annotate("\u05e9\u05b8\u05dc\u05d5\u05b9\u05dd", JapanglifySettings(includeRomaji = true))
        assertTrue(segs.any { it.romaji != null })
        assertEquals(AnalyzerIds.HBO, segs.first().script)
    }

    @Test fun forceOverride() {
        val segs = engine.annotate(
            "\u03bb\u03cc\u03b3\u03bf\u03c2",
            JapanglifySettings(includeRomaji = true, forceAnalyzerId = AnalyzerIds.EL_MODERN)
        )
        assertEquals(AnalyzerIds.EL_MODERN, segs.first().script)
    }

    @Test fun japaneseRegressionSmoke() {
        val out = engine.expand("\u3042", JapanglifySettings())
        assertTrue(out.isNotBlank())
    }
}