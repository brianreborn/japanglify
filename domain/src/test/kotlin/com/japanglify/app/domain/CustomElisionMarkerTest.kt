package com.japanglify.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CustomElisionMarkerTest {

    private fun settings(marker: ElisionMarker, custom: String = "") =
        JapanglifySettings(lineElisionMarker = marker, customLineElisionMarker = custom)

    @Test
    fun presetsUseTheirOwnSymbolAndIgnoreTheCustomField() {
        assertEquals("〃", settings(ElisionMarker.DITTO, custom = "★").effectiveLineElisionSymbol)
        assertEquals("\"", settings(ElisionMarker.DITTO_QUOTES).effectiveLineElisionSymbol)
        assertNull(settings(ElisionMarker.NONE).effectiveLineElisionSymbol)
    }

    @Test
    fun customUsesTheWrittenInGlyph() {
        assertEquals("★", settings(ElisionMarker.CUSTOM, custom = "★").effectiveLineElisionSymbol)
    }

    @Test
    fun customBlankMeansNoMarker() {
        assertNull(settings(ElisionMarker.CUSTOM, custom = "").effectiveLineElisionSymbol)
        assertNull(settings(ElisionMarker.CUSTOM, custom = "   ").effectiveLineElisionSymbol)
    }

    @Test
    fun customClampsToTheFirstCodepoint() {
        // Stray multi-character input still yields a single-character marker.
        assertEquals("★", settings(ElisionMarker.CUSTOM, custom = "★xyz").effectiveLineElisionSymbol)
    }

    @Test
    fun customHandlesAstralCodepointAsOneCharacter() {
        // A 😀 is a surrogate pair (2 Java chars, 1 codepoint) -- the first
        // codepoint must come through whole, not sliced in half.
        val emoji = "😀" // 😀 U+1F600
        assertEquals(emoji, settings(ElisionMarker.CUSTOM, custom = "${emoji}more").effectiveLineElisionSymbol)
    }

    @Test
    fun rendererAppendsTheCustomMarkerOnAnElidedRow() {
        // A kana-only word (だ) with furigana-on-kanji-only elides the furigana
        // row; the marker rides on the romaji line. Uses a custom glyph here.
        val renderer = TripleScriptRenderer()
        val segments = listOf(
            AnnotatedSegment(surface = "だ", furigana = "だ", romaji = "da", romajiMora = "da", needsFurigana = false)
        )
        val out = renderer.render(
            segments,
            JapanglifySettings(
                outputFormat = OutputFormat.INTERLINEAR,
                furiganaKanjiOnly = true,
                lineElisionMarker = ElisionMarker.CUSTOM,
                customLineElisionMarker = "★"
            )
        )
        // The star appears exactly once, as the trailing elision marker.
        assertEquals(1, out.count { it == '★' })
    }
}
