package com.japanglify.app.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class RomanizerTest {

    @Test
    fun hepburnBasicKana() {
        val r = Romanizer(RomanizationSystem.HEPBURN_MODIFIED)
        assertEquals("hiragana", r.romanize("ひらがな"))
        assertEquals("katakana", r.romanize("カタカナ"))
        assertEquals("nippon", r.romanize("にっぽん"))
    }

    @Test
    fun hepburnYoonAndSpecial() {
        val r = Romanizer(RomanizationSystem.HEPBURN_MODIFIED)
        assertEquals("shashin", r.romanize("しゃしん"))
        assertEquals("chizu", r.romanize("ちず"))
        assertEquals("kyou", r.romanize("きょう"))
    }

    @Test
    fun hepburnSyllabicN() {
        val modified = Romanizer(RomanizationSystem.HEPBURN_MODIFIED)
        // ん + vowel/y → n' (modified Hepburn)
        assertEquals("kan'i", modified.romanize("かんい"))
        assertEquals("shin'you", modified.romanize("しんよう"))
        // ん before consonant n: no apostrophe; は stays "ha" without POS
        assertEquals("konnichiha", modified.romanize("こんにちは"))

        val traditional = Romanizer(RomanizationSystem.HEPBURN_TRADITIONAL)
        assertEquals("shimbun", traditional.romanize("しんぶん"))
    }

    @Test
    fun kunreiVsHepburn() {
        val hepburn = Romanizer(RomanizationSystem.HEPBURN_MODIFIED)
        val kunrei = Romanizer(RomanizationSystem.KUNREI)
        assertEquals("shi", hepburn.romanize("し"))
        assertEquals("si", kunrei.romanize("し"))
        assertEquals("chi", hepburn.romanize("ち"))
        assertEquals("ti", kunrei.romanize("ち"))
        assertEquals("tsu", hepburn.romanize("つ"))
        assertEquals("tu", kunrei.romanize("つ"))
        assertEquals("fu", hepburn.romanize("ふ"))
        assertEquals("hu", kunrei.romanize("ふ"))
        assertEquals("sha", hepburn.romanize("しゃ"))
        assertEquals("sya", kunrei.romanize("しゃ"))
    }

    @Test
    fun nihonShikiDiDu() {
        val nihon = Romanizer(RomanizationSystem.NIHON)
        assertEquals("di", nihon.romanize("ぢ"))
        assertEquals("du", nihon.romanize("づ"))
    }

    @Test
    fun wapuroAndSokuon() {
        val w = Romanizer(RomanizationSystem.WAPURO)
        assertEquals("gakkou", w.romanize("がっこう"))
        assertEquals("konnnichiha", w.romanize("こんにちは"))
    }
}
