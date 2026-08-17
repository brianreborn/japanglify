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

    @Test
    fun moraSeparatorGoesBetweenMoraeOnly() {
        val r = Romanizer()
        // Baseline: clean kana separate at every mora boundary.
        assertEquals("fu·ku", r.romanizeMora("ふく"))
        assertEquals("da·ne", r.romanizeMora("だね"))
        // A passthrough character (space here) must NOT make the following
        // mora sprout a leading separator. Found live: "fu·ku ·da ne" had a
        // stray dot wedged onto だ after the space.
        assertEquals("fu·ku da", r.romanizeMora("ふく だ"))
        // Fullwidth space (U+3000) is also a passthrough, not a mora boundary.
        assertEquals("a　i", r.romanizeMora("あ　い"))
    }

    @Test
    fun moraSeparatorNotLeadingAfterPunctuationOrSpace() {
        val r = Romanizer()
        // No leading separator on the mora right after a space / middle dot /
        // fullwidth space, and none at the very start.
        assertEquals("da", r.romanizeMora(" だ").trimStart())
        assertEquals(" da", r.romanizeMora(" だ"))
        assertEquals("・da", r.romanizeMora("・だ"))
        assertEquals("fu·ku da·ne", r.romanizeMora("ふく だね"))
    }
}
