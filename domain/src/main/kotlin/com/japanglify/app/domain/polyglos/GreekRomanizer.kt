package com.japanglify.app.domain.polyglos

/** Letter-level Greek to Latin for Erasmian, SBL, and modern phonemic schemes. */
object GreekRomanizer {

    fun romanize(token: String, scheme: GreekRomanization): String {
        if (token.isEmpty()) return token
        val nfd = java.text.Normalizer.normalize(token, java.text.Normalizer.Form.NFD)
        val out = StringBuilder(nfd.length)
        var i = 0
        while (i < nfd.length) {
            val ch = nfd[i]
            val cp = ch.code
            val type = Character.getType(cp)
            if (type == Character.NON_SPACING_MARK.toInt() ||
                type == Character.COMBINING_SPACING_MARK.toInt()
            ) { i++; continue }
            val upper = Character.isUpperCase(cp)
            val lower = Character.toLowerCase(cp)
            val raw = table(scheme)[lower]
            if (raw != null) {
                out.append(if (upper) raw.replaceFirstChar { it.uppercaseChar() } else raw)
            } else {
                out.append(ch)
            }
            i++
        }
        return out.toString()
    }

    private fun table(scheme: GreekRomanization): Map<Int, String> = when (scheme) {
        GreekRomanization.ERASMIAN -> ERASMIAN
        GreekRomanization.SBL -> SBL
        GreekRomanization.MODERN_PHONEMIC -> MODERN
    }

    private val ERASMIAN = mapOf(
        0x03B1 to "a", 0x03B2 to "b", 0x03B3 to "g", 0x03B4 to "d",
        0x03B5 to "e", 0x03B6 to "z", 0x03B7 to "ē", 0x03B8 to "th",
        0x03B9 to "i", 0x03BA to "k", 0x03BB to "l", 0x03BC to "m",
        0x03BD to "n", 0x03BE to "x", 0x03BF to "o", 0x03C0 to "p",
        0x03C1 to "r", 0x03C2 to "s", 0x03C3 to "s", 0x03C4 to "t",
        0x03C5 to "u", 0x03C6 to "ph", 0x03C7 to "ch", 0x03C8 to "ps",
        0x03C9 to "ō"
    )

    private val SBL = mapOf(
        0x03B1 to "a", 0x03B2 to "b", 0x03B3 to "g", 0x03B4 to "d",
        0x03B5 to "e", 0x03B6 to "z", 0x03B7 to "ē", 0x03B8 to "th",
        0x03B9 to "i", 0x03BA to "k", 0x03BB to "l", 0x03BC to "m",
        0x03BD to "n", 0x03BE to "x", 0x03BF to "o", 0x03C0 to "p",
        0x03C1 to "r", 0x03C2 to "s", 0x03C3 to "s", 0x03C4 to "t",
        0x03C5 to "y", 0x03C6 to "ph", 0x03C7 to "ch", 0x03C8 to "ps",
        0x03C9 to "ō"
    )

    private val MODERN = mapOf(
        0x03B1 to "a", 0x03B2 to "v", 0x03B3 to "g", 0x03B4 to "d",
        0x03B5 to "e", 0x03B6 to "z", 0x03B7 to "i", 0x03B8 to "th",
        0x03B9 to "i", 0x03BA to "k", 0x03BB to "l", 0x03BC to "m",
        0x03BD to "n", 0x03BE to "x", 0x03BF to "o", 0x03C0 to "p",
        0x03C1 to "r", 0x03C2 to "s", 0x03C3 to "s", 0x03C4 to "t",
        0x03C5 to "y", 0x03C6 to "f", 0x03C7 to "ch", 0x03C8 to "ps",
        0x03C9 to "o"
    )
}
