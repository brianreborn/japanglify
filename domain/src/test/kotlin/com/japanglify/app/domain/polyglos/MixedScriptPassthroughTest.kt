package com.japanglify.app.domain.polyglos

import com.japanglify.app.domain.JapaneseAnalyzer
import com.japanglify.app.domain.JapanglifySettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Extra §7 coverage beyond [PolyglosSettingsRoutingTest]: Hebrew+Greek mash,
 * force-analyzer still passthrough, three-word mixed input, Latin is not a
 * mix partner, unpointed modern Hebrew phonetic suppress.
 */
class MixedScriptPassthroughTest {
    private val engine = PolyglosEngine.withJapanese(JapaneseAnalyzer())
    private val withPhonetic = JapanglifySettings(includeRomaji = true)

    @Test fun hebrewGreekMashIsPassthrough() {
        val word = "\u03bb\u05d0\u03b3" // λ א γ
        val segs = engine.annotate(word, withPhonetic)
        assertEquals(word, segs.single().surface)
        assertFalse(segs.single().hasRomaji)
        assertNull(segs.single().script)
    }

    @Test fun japaneseHebrewMashIsPassthrough() {
        val word = "\u65e5\u05d0\u8a9e"
        val segs = engine.annotate(word, withPhonetic)
        assertEquals(word, segs.single().surface)
        assertFalse(segs.single().hasRomaji)
        assertFalse(segs.single().hasFurigana)
        assertNull(segs.single().script)
    }

    @Test fun forceAnalyzerDoesNotInterpretMixedWord() {
        val word = "\u65e5\u03b1\u8a9e"
        val segs = engine.annotate(
            word,
            JapanglifySettings(
                includeRomaji = true,
                includeFurigana = true,
                forceAnalyzerId = AnalyzerIds.GRC_ANCIENT
            )
        )
        assertEquals(word, segs.single().surface)
        assertFalse(segs.single().hasRomaji)
        assertNull(segs.single().script)
    }

    @Test fun mixedInputThreeLanguagesEachWordRouted() {
        val segs = engine.annotate(
            "\u03bb\u03cc\u03b3\u03bf\u03c2 \u65e5\u672c\u8a9e \u05e9\u05b8\u05dc\u05d5\u05b9\u05dd",
            JapanglifySettings(includeRomaji = true, includeFurigana = true)
        )
        val greek = segs.first { it.surface.contains("\u03bb") }
        val japanese = segs.first { it.surface.contains("\u65e5") }
        val hebrew = segs.first { it.surface.contains("\u05e9") }
        assertEquals(AnalyzerIds.GRC_ANCIENT, greek.script)
        assertTrue(greek.hasRomaji)
        assertEquals(AnalyzerIds.JA, japanese.script)
        assertTrue(japanese.hasFurigana || japanese.hasRomaji)
        assertEquals(AnalyzerIds.HBO, hebrew.script)
        assertTrue(hebrew.hasRomaji)
    }

    @Test fun latinPlusGreekInOneTokenIsNotMixedWord() {
        // §7 mix set is {ja, el, he}; Latin does not trigger passthrough.
        val word = "hello\u03bb\u03cc\u03b3\u03bf\u03c2"
        val profile = ScriptDetector.detect(word)
        assertFalse(profile.mixed)
        assertEquals(ScriptKind.GREEK, profile.dominant)
        val segs = engine.annotate(word, withPhonetic)
        assertEquals(AnalyzerIds.GRC_ANCIENT, segs.first().script)
        assertTrue(segs.any { it.hasRomaji })
    }

    @Test fun unpointedModernHebrewSuppressesPhoneticByDefault() {
        val unpointed = "\u05e9\u05dc\u05d5\u05dd" // שלום
        val segs = engine.annotate(
            unpointed,
            JapanglifySettings(
                includeRomaji = true,
                forceAnalyzerId = AnalyzerIds.HE_MODERN
            )
        )
        assertEquals(unpointed, segs.single().surface)
        assertFalse(segs.single().hasRomaji)
        assertEquals(AnalyzerIds.HE_MODERN, segs.single().script)
    }

    @Test fun unpointedModernHebrewPhoneticWhenOptedIn() {
        val unpointed = "\u05e9\u05dc\u05d5\u05dd"
        val segs = engine.annotate(
            unpointed,
            JapanglifySettings(
                includeRomaji = true,
                forceAnalyzerId = AnalyzerIds.HE_MODERN,
                hebrewPhoneticUnpointed = true
            )
        )
        assertTrue(segs.single().hasRomaji)
        assertEquals(AnalyzerIds.HE_MODERN, segs.single().script)
    }
}
