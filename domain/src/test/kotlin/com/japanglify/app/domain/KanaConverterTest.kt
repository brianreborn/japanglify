package com.japanglify.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KanaConverterTest {

    @Test
    fun katakanaToHiragana() {
        assertEquals("ひらがな", KanaConverter.toHiragana("ヒラガナ"))
        assertEquals("ゔ", KanaConverter.toHiragana("ヴ"))
        assertEquals("あいう", KanaConverter.toHiragana("あいう"))
    }

    @Test
    fun characterClasses() {
        assertTrue(KanaConverter.isKanji('漢'))
        assertTrue(KanaConverter.isHiragana('あ'))
        assertTrue(KanaConverter.isKatakana('ア'))
        assertTrue(KanaConverter.containsKanji("日本語"))
        assertFalse(KanaConverter.containsKanji("ひらがな"))
        assertTrue(KanaConverter.isMostlyKana("カタカナー"))
    }
}
