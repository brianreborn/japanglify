package com.japanglify.app.domain

/**
 * Converts hiragana/katakana to Latin script under a chosen
 * [RomanizationSystem].
 *
 * Input may be mixed; katakana is normalized to hiragana first. Long vowels,
 * sokuon (っ), and yōon (きゃ etc.) are handled per-system conventions.
 */
class Romanizer(
    private val system: RomanizationSystem = RomanizationSystem.HEPBURN_MODIFIED
) {

    fun romanize(kana: String): String {
        if (kana.isEmpty()) return ""
        val hira = KanaConverter.toHiragana(kana)
        return romanizeHiragana(hira, separated = false)
    }

    /**
     * Same romanization, with a hyphen inserted at each mora boundary (e.g.
     * "nihongo" -> "ni-hon-go"). Used only for the interlinear romaji row,
     * where one solid run under a multi-kanji word hides which part of the
     * reading corresponds to which kana/kanji; [romanize] (used everywhere
     * else — clipboard text, notifications, PROCESS_TEXT replacement) stays
     * a natural unbroken word.
     */
    fun romanizeSyllables(kana: String): String {
        if (kana.isEmpty()) return ""
        val hira = KanaConverter.toHiragana(kana)
        return romanizeHiragana(hira, separated = true)
    }

    private fun romanizeHiragana(text: String, separated: Boolean): String {
        val out = StringBuilder(text.length * 2)
        var i = 0
        // True right after a sokuon: the doubled consonant it produced binds
        // to the next mora as one unit (っち -> "tchi", not "t-chi"), so skip
        // the separator just this once.
        var glueNext = false
        while (i < text.length) {
            val c = text[i]

            // Sokuon (促音): double the following consonant
            if (c == 'っ' || c == 'ッ') {
                if (separated && out.isNotEmpty()) out.append(MORA_SEP)
                val next = peekMora(text, i + 1)
                val cons = next?.let { initialConsonant(romanizeMora(it)) }
                if (!cons.isNullOrEmpty()) {
                    out.append(cons[0])
                } else {
                    out.append(if (system == RomanizationSystem.WAPURO) "xtu" else "t")
                }
                glueNext = true
                i++
                continue
            }

            // Prolonged sound mark — modifies the previous letter in place
            // (macron/doubled vowel) or appends its own dash; never gets an
            // extra mora separator of its own.
            if (c == 'ー') {
                applyChoonpu(out)
                i++
                continue
            }

            // Syllabic ん — system-specific nasal rules need the following mora
            if (c == 'ん') {
                if (separated && out.isNotEmpty() && !glueNext) out.append(MORA_SEP)
                out.append(romanizeN(text, i + 1))
                glueNext = false
                i++
                continue
            }

            val mora = peekMora(text, i)
            if (mora != null) {
                if (separated && out.isNotEmpty() && !glueNext) out.append(MORA_SEP)
                out.append(romanizeMora(mora))
                glueNext = false
                i += mora.length
                continue
            }

            // Pass through non-kana (numbers, Latin, punctuation)
            out.append(c)
            glueNext = false
            i++
        }

        return out.toString()
    }

    /**
     * Render syllabic ん with lookahead.
     * Modified Hepburn: n' before vowels and y; Traditional: m before b/m/p.
     */
    private fun romanizeN(text: String, nextIndex: Int): String {
        val next = peekMora(text, nextIndex)
        val nextRomaji = next?.let { romanizeMora(it) }.orEmpty()

        return when (system) {
            RomanizationSystem.HEPBURN_TRADITIONAL -> {
                if (nextRomaji.firstOrNull() in setOf('b', 'm', 'p')) "m" else "n"
            }
            RomanizationSystem.HEPBURN_MODIFIED -> {
                val lead = nextRomaji.firstOrNull()
                when {
                    lead == null -> "n"
                    lead in "aiueoy" -> "n'"
                    else -> "n"
                }
            }
            RomanizationSystem.WAPURO -> "nn"
            RomanizationSystem.KUNREI,
            RomanizationSystem.NIHON -> "n"
        }
    }

    /**
     * Longest-match mora starting at [index]: digraph yōon first, then single.
     */
    private fun peekMora(text: String, index: Int): String? {
        if (index >= text.length) return null
        if (index + 1 < text.length) {
            val two = text.substring(index, index + 2)
            if (two in digraphs) return two
        }
        val one = text[index].toString()
        return if (one in monographs || isSingleKana(text[index])) one else null
    }

    private fun isSingleKana(c: Char): Boolean =
        KanaConverter.isHiragana(c) || c == 'ん' || c == 'ゐ' || c == 'ゑ' || c == 'を'

    private fun romanizeMora(mora: String): String {
        digraphs[mora]?.let { return pick(it) }
        monographs[mora]?.let { return pick(it) }
        // Fallback: leave as-is (should be rare)
        return mora
    }

    private fun pick(variants: MoraRomaji): String = when (system) {
        RomanizationSystem.HEPBURN_MODIFIED -> variants.hepburnModified
        RomanizationSystem.HEPBURN_TRADITIONAL -> variants.hepburnTraditional
        RomanizationSystem.KUNREI -> variants.kunrei
        RomanizationSystem.NIHON -> variants.nihon
        RomanizationSystem.WAPURO -> variants.wapuro
    }

    private fun initialConsonant(romaji: String): String {
        if (romaji.isEmpty()) return ""
        // ch/sh/ts clusters
        if (romaji.startsWith("ch")) return "c"
        if (romaji.startsWith("sh")) return "s"
        if (romaji.startsWith("ts")) return "t"
        val first = romaji[0]
        return if (first in "bcdfghjklmnpqrstvwxyz") first.toString() else ""
    }

    private fun applyChoonpu(out: StringBuilder) {
        if (out.isEmpty()) {
            out.append('-')
            return
        }
        when (system) {
            RomanizationSystem.WAPURO -> out.append('-')
            RomanizationSystem.HEPBURN_MODIFIED,
            RomanizationSystem.HEPBURN_TRADITIONAL -> {
                val last = out.last()
                val macron = macronFor(last)
                if (macron != null) {
                    out.setCharAt(out.length - 1, macron)
                } else {
                    out.append(last) // lengthen vowel letter
                }
            }
            RomanizationSystem.KUNREI,
            RomanizationSystem.NIHON -> {
                // Official systems often double the vowel or use circumflex;
                // we double the vowel letter for plain-text portability.
                val last = out.last()
                if (last in "aiueoâîûêô") {
                    out.append(vowelBase(last))
                } else {
                    out.append('-')
                }
            }
        }
    }

    private fun macronFor(vowel: Char): Char? = when (vowel) {
        'a' -> 'ā'
        'i' -> 'ī'
        'u' -> 'ū'
        'e' -> 'ē'
        'o' -> 'ō'
        else -> null
    }

    private fun vowelBase(c: Char): Char = when (c) {
        'â', 'ā' -> 'a'
        'î', 'ī' -> 'i'
        'û', 'ū' -> 'u'
        'ê', 'ē' -> 'e'
        'ô', 'ō' -> 'o'
        else -> c
    }

    /**
     * Per-system spellings for one mora.
     * Order: modified Hepburn, traditional Hepburn, Kunrei, Nihon, Wāpuro.
     */
    private data class MoraRomaji(
        val hepburnModified: String,
        val hepburnTraditional: String = hepburnModified,
        val kunrei: String = hepburnModified,
        val nihon: String = kunrei,
        val wapuro: String = hepburnModified
    )

    companion object {
        private const val MORA_SEP = "-"

        private val monographs: Map<String, MoraRomaji> = mapOf(
            // Vowels
            "あ" to MoraRomaji("a"),
            "い" to MoraRomaji("i"),
            "う" to MoraRomaji("u"),
            "え" to MoraRomaji("e"),
            "お" to MoraRomaji("o"),
            // K
            "か" to MoraRomaji("ka"),
            "き" to MoraRomaji("ki"),
            "く" to MoraRomaji("ku"),
            "け" to MoraRomaji("ke"),
            "こ" to MoraRomaji("ko"),
            "が" to MoraRomaji("ga"),
            "ぎ" to MoraRomaji("gi"),
            "ぐ" to MoraRomaji("gu"),
            "げ" to MoraRomaji("ge"),
            "ご" to MoraRomaji("go"),
            // S
            "さ" to MoraRomaji("sa"),
            "し" to MoraRomaji("shi", "shi", "si", "si", "shi"),
            "す" to MoraRomaji("su"),
            "せ" to MoraRomaji("se"),
            "そ" to MoraRomaji("so"),
            "ざ" to MoraRomaji("za"),
            "じ" to MoraRomaji("ji", "ji", "zi", "zi", "ji"),
            "ず" to MoraRomaji("zu"),
            "ぜ" to MoraRomaji("ze"),
            "ぞ" to MoraRomaji("zo"),
            // T
            "た" to MoraRomaji("ta"),
            "ち" to MoraRomaji("chi", "chi", "ti", "ti", "chi"),
            "つ" to MoraRomaji("tsu", "tsu", "tu", "tu", "tsu"),
            "て" to MoraRomaji("te"),
            "と" to MoraRomaji("to"),
            "だ" to MoraRomaji("da"),
            "ぢ" to MoraRomaji("ji", "ji", "zi", "di", "di"),
            "づ" to MoraRomaji("zu", "zu", "zu", "du", "du"),
            "で" to MoraRomaji("de"),
            "ど" to MoraRomaji("do"),
            // N
            "な" to MoraRomaji("na"),
            "に" to MoraRomaji("ni"),
            "ぬ" to MoraRomaji("nu"),
            "ね" to MoraRomaji("ne"),
            "の" to MoraRomaji("no"),
            // H
            "は" to MoraRomaji("ha"),
            "ひ" to MoraRomaji("hi"),
            "ふ" to MoraRomaji("fu", "fu", "hu", "hu", "fu"),
            "へ" to MoraRomaji("he"),
            "ほ" to MoraRomaji("ho"),
            "ば" to MoraRomaji("ba"),
            "び" to MoraRomaji("bi"),
            "ぶ" to MoraRomaji("bu"),
            "べ" to MoraRomaji("be"),
            "ぼ" to MoraRomaji("bo"),
            "ぱ" to MoraRomaji("pa"),
            "ぴ" to MoraRomaji("pi"),
            "ぷ" to MoraRomaji("pu"),
            "ぺ" to MoraRomaji("pe"),
            "ぽ" to MoraRomaji("po"),
            // M
            "ま" to MoraRomaji("ma"),
            "み" to MoraRomaji("mi"),
            "む" to MoraRomaji("mu"),
            "め" to MoraRomaji("me"),
            "も" to MoraRomaji("mo"),
            // Y
            "や" to MoraRomaji("ya"),
            "ゆ" to MoraRomaji("yu"),
            "よ" to MoraRomaji("yo"),
            // R
            "ら" to MoraRomaji("ra"),
            "り" to MoraRomaji("ri"),
            "る" to MoraRomaji("ru"),
            "れ" to MoraRomaji("re"),
            "ろ" to MoraRomaji("ro"),
            // W
            "わ" to MoraRomaji("wa"),
            "ゐ" to MoraRomaji("i", "i", "i", "wi", "wyi"),
            "ゑ" to MoraRomaji("e", "e", "e", "we", "wye"),
            "を" to MoraRomaji("o", "wo", "o", "wo", "wo"),
            "ん" to MoraRomaji("n", "n", "n", "n", "nn"),
            // Small vowels / archaic
            "ぁ" to MoraRomaji("a", wapuro = "xa"),
            "ぃ" to MoraRomaji("i", wapuro = "xi"),
            "ぅ" to MoraRomaji("u", wapuro = "xu"),
            "ぇ" to MoraRomaji("e", wapuro = "xe"),
            "ぉ" to MoraRomaji("o", wapuro = "xo"),
            "ゃ" to MoraRomaji("ya", wapuro = "xya"),
            "ゅ" to MoraRomaji("yu", wapuro = "xyu"),
            "ょ" to MoraRomaji("yo", wapuro = "xyo"),
            "ゎ" to MoraRomaji("wa", wapuro = "xwa"),
            "ゔ" to MoraRomaji("vu"),
        )

        private val digraphs: Map<String, MoraRomaji> = mapOf(
            // Kyō
            "きゃ" to MoraRomaji("kya"),
            "きゅ" to MoraRomaji("kyu"),
            "きょ" to MoraRomaji("kyo"),
            "ぎゃ" to MoraRomaji("gya"),
            "ぎゅ" to MoraRomaji("gyu"),
            "ぎょ" to MoraRomaji("gyo"),
            // Sh/Sy
            "しゃ" to MoraRomaji("sha", "sha", "sya", "sya", "sha"),
            "しゅ" to MoraRomaji("shu", "shu", "syu", "syu", "shu"),
            "しょ" to MoraRomaji("sho", "sho", "syo", "syo", "sho"),
            "じゃ" to MoraRomaji("ja", "ja", "zya", "zya", "ja"),
            "じゅ" to MoraRomaji("ju", "ju", "zyu", "zyu", "ju"),
            "じょ" to MoraRomaji("jo", "jo", "zyo", "zyo", "jo"),
            // Ch/Ty
            "ちゃ" to MoraRomaji("cha", "cha", "tya", "tya", "cha"),
            "ちゅ" to MoraRomaji("chu", "chu", "tyu", "tyu", "chu"),
            "ちょ" to MoraRomaji("cho", "cho", "tyo", "tyo", "cho"),
            "ぢゃ" to MoraRomaji("ja", "ja", "zya", "dya", "dya"),
            "ぢゅ" to MoraRomaji("ju", "ju", "zyu", "dyu", "dyu"),
            "ぢょ" to MoraRomaji("jo", "jo", "zyo", "dyo", "dyo"),
            // Ny
            "にゃ" to MoraRomaji("nya"),
            "にゅ" to MoraRomaji("nyu"),
            "にょ" to MoraRomaji("nyo"),
            // Hy
            "ひゃ" to MoraRomaji("hya"),
            "ひゅ" to MoraRomaji("hyu"),
            "ひょ" to MoraRomaji("hyo"),
            "びゃ" to MoraRomaji("bya"),
            "びゅ" to MoraRomaji("byu"),
            "びょ" to MoraRomaji("byo"),
            "ぴゃ" to MoraRomaji("pya"),
            "ぴゅ" to MoraRomaji("pyu"),
            "ぴょ" to MoraRomaji("pyo"),
            // My
            "みゃ" to MoraRomaji("mya"),
            "みゅ" to MoraRomaji("myu"),
            "みょ" to MoraRomaji("myo"),
            // Ry
            "りゃ" to MoraRomaji("rya"),
            "りゅ" to MoraRomaji("ryu"),
            "りょ" to MoraRomaji("ryo"),
            // Foreign extensions
            "ふぁ" to MoraRomaji("fa", wapuro = "fa"),
            "ふぃ" to MoraRomaji("fi", wapuro = "fi"),
            "ふぇ" to MoraRomaji("fe", wapuro = "fe"),
            "ふぉ" to MoraRomaji("fo", wapuro = "fo"),
            "てぃ" to MoraRomaji("ti", wapuro = "thi"),
            "でぃ" to MoraRomaji("di", wapuro = "dhi"),
            "とぅ" to MoraRomaji("tu", wapuro = "twu"),
            "どぅ" to MoraRomaji("du", wapuro = "dwu"),
            "うぃ" to MoraRomaji("wi", wapuro = "wi"),
            "うぇ" to MoraRomaji("we", wapuro = "we"),
            "うぉ" to MoraRomaji("wo", wapuro = "wo"),
            "ゔぁ" to MoraRomaji("va"),
            "ゔぃ" to MoraRomaji("vi"),
            "ゔぇ" to MoraRomaji("ve"),
            "ゔぉ" to MoraRomaji("vo"),
            "しぇ" to MoraRomaji("she", "she", "sye", "sye", "she"),
            "じぇ" to MoraRomaji("je", "je", "zye", "zye", "je"),
            "ちぇ" to MoraRomaji("che", "che", "tye", "tye", "che"),
            "つぁ" to MoraRomaji("tsa"),
            "つぃ" to MoraRomaji("tsi"),
            "つぇ" to MoraRomaji("tse"),
            "つぉ" to MoraRomaji("tso"),
        )
    }
}
