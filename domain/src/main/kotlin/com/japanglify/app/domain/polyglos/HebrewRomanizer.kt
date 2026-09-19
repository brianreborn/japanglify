package com.japanglify.app.domain.polyglos

/** Academic Biblical Hebrew romanization (consonants + common niqqud). */
object HebrewRomanizer {

    fun romanize(token: String): String {
        if (token.isEmpty()) return token
        val nfd = java.text.Normalizer.normalize(token, java.text.Normalizer.Form.NFD)
        val out = StringBuilder(nfd.length)
        var i = 0
        while (i < nfd.length) {
            val cp = nfd[i].code
            when {
                cp in 0x0591..0x05AF -> Unit
                VOWELS.containsKey(cp) -> out.append(VOWELS.getValue(cp))
                cp == 0x05BC || cp == 0x05C1 || cp == 0x05C2 -> Unit
                CONSONANTS.containsKey(cp) -> out.append(CONSONANTS.getValue(cp))
                Character.getType(cp) == Character.NON_SPACING_MARK.toInt() -> Unit
                else -> out.append(nfd[i])
            }
            i++
        }
        return out.toString()
    }

    private val CONSONANTS = mapOf(
        0x05D0 to "'", 0x05D1 to "b", 0x05D2 to "g", 0x05D3 to "d",
        0x05D4 to "h", 0x05D5 to "w", 0x05D6 to "z", 0x05D7 to "ḥ",
        0x05D8 to "ṭ", 0x05D9 to "y", 0x05DA to "k", 0x05DB to "k",
        0x05DC to "l", 0x05DD to "m", 0x05DE to "m", 0x05DF to "n",
        0x05E0 to "n", 0x05E1 to "s", 0x05E2 to "'", 0x05E3 to "p",
        0x05E4 to "p", 0x05E5 to "ṣ", 0x05E6 to "ṣ", 0x05E7 to "q",
        0x05E8 to "r", 0x05E9 to "š", 0x05EA to "t"
    )

    private val VOWELS = mapOf(
        0x05B0 to "ə", 0x05B1 to "ă", 0x05B2 to "ă", 0x05B3 to "ŏ",
        0x05B4 to "i", 0x05B5 to "ē", 0x05B6 to "e", 0x05B7 to "a",
        0x05B8 to "ā", 0x05B9 to "ō", 0x05BA to "ō", 0x05BB to "u",
        0x05C7 to "o"
    )
}
