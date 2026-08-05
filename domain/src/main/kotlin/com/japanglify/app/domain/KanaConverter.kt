package com.japanglify.app.domain

/**
 * Hiragana ↔ katakana helpers and character-class tests used throughout
 * the annotation pipeline.
 */
object KanaConverter {

    private val HIRAGANA = '\u3040'..'\u309F'
    private val KATAKANA = '\u30A0'..'\u30FF'
    private val KANJI_BMP = '\u4E00'..'\u9FFF'
    private val KANJI_EXT_A = '\u3400'..'\u4DBF'

    fun isHiragana(c: Char): Boolean = c in HIRAGANA
    fun isKatakana(c: Char): Boolean = c in KATAKANA
    fun isKana(c: Char): Boolean = isHiragana(c) || isKatakana(c)

    fun isKanji(c: Char): Boolean =
        c in KANJI_BMP || c in KANJI_EXT_A || c == '々' || c == '〆' || c == 'ヶ' || c == 'ヵ'

    fun containsKanji(text: String): Boolean = text.any { isKanji(it) }

    fun isMostlyKana(text: String): Boolean {
        val significant = text.filter { !it.isWhitespace() && !isPunctuation(it) }
        if (significant.isEmpty()) return false
        return significant.all { isKana(it) || it == 'ー' || it == 'ゝ' || it == 'ゞ' || it == 'ヽ' || it == 'ヾ' }
    }

    fun isPunctuation(c: Char): Boolean = when (c) {
        in '\u3000'..'\u303F' -> true // CJK punctuation
        in '\uFF01'..'\uFF60' -> true // fullwidth ASCII range
        in ".,!?;:'\"()[]{}…—–-_/\\@#$%^&*+=<>|~`".toSet() -> true
        else -> c.isWhitespace()
    }

    fun isMostlyPunctuation(text: String): Boolean {
        val t = text.trim()
        if (t.isEmpty()) return false
        return t.all { isPunctuation(it) || it.isWhitespace() }
    }

    /**
     * Latin / halfwidth stand-ins for common Japanese / fullwidth punctuation
     * so the romaji line stays “translated” rather than blank.
     */
    fun punctuationToRomaji(text: String): String = buildString(text.length) {
        for (c in text) {
            append(
                when (c) {
                    '、', '，' -> ','
                    '。', '．' -> '.'
                    '！' -> '!'
                    '？' -> '?'
                    '：' -> ':'
                    '；' -> ';'
                    '「', '『', '【', '（', '［', '〈', '《' -> '"'
                    '」', '』', '】', '）', '］', '〉', '》' -> '"'
                    '・' -> '·'
                    '…', '‥' -> '…'
                    'ー', '―', '—', '–' -> '-'
                    '　' -> ' '
                    '〜', '～' -> '~'
                    else -> if (c in '\uFF01'..'\uFF5E') {
                        // fullwidth ASCII → halfwidth
                        (c.code - 0xFEE0).toChar()
                    } else c
                }
            )
        }
    }

    /**
     * Convert katakana (and prolonged sound mark) runs to hiragana.
     * Non-katakana characters are left unchanged.
     */
    fun toHiragana(text: String): String = buildString(text.length) {
        for (c in text) {
            append(
                when {
                    c in 'ァ'..'ン' -> (c.code - 0x60).toChar()
                    c == 'ヴ' -> 'ゔ'
                    c == 'ヵ' -> 'か'
                    c == 'ヶ' -> 'け'
                    c == 'ヽ' -> 'ゝ'
                    c == 'ヾ' -> 'ゞ'
                    else -> c
                }
            )
        }
    }

    fun toKatakana(text: String): String = buildString(text.length) {
        for (c in text) {
            append(
                when {
                    c in 'ぁ'..'ん' -> (c.code + 0x60).toChar()
                    c == 'ゔ' -> 'ヴ'
                    else -> c
                }
            )
        }
    }

    /**
     * Halfwidth katakana for a more compact “ruby-like” top line in plain text.
     * Unicode has no complete set of small hiragana for true furigana sizing;
     * halfwidth katakana is the usual plain-text stand-in (narrower in most fonts).
     */
    fun toHalfwidthKatakana(text: String): String {
        val kata = toKatakana(toHiragana(text))
        return buildString(kata.length * 2) {
            var i = 0
            while (i < kata.length) {
                val c = kata[i]
                val next = kata.getOrNull(i + 1)
                // Digraphs: キ + ャ → ｷｬ etc.
                if (next != null) {
                    val digraph = halfwidthDigraph(c, next)
                    if (digraph != null) {
                        append(digraph)
                        i += 2
                        continue
                    }
                }
                append(halfwidthSingle(c))
                i++
            }
        }
    }

    private fun halfwidthDigraph(base: Char, small: Char): String? {
        val b = when (base) {
            'キ' -> 'ｷ'; 'シ' -> 'ｼ'; 'チ' -> 'ﾁ'; 'ニ' -> 'ﾆ'
            'ヒ' -> 'ﾋ'; 'ミ' -> 'ﾐ'; 'リ' -> 'ﾘ'; 'ギ' -> 'ｷ'
            'ジ' -> 'ｼ'; 'ヂ' -> 'ﾁ'; 'ビ' -> 'ﾋ'; 'ピ' -> 'ﾋ'
            'フ' -> 'ﾌ'
            else -> return null
        }
        val s = when (small) {
            'ャ' -> 'ｬ'; 'ュ' -> 'ｭ'; 'ョ' -> 'ｮ'
            'ァ' -> 'ｧ'; 'ィ' -> 'ｨ'; 'ゥ' -> 'ｩ'; 'ェ' -> 'ｪ'; 'ォ' -> 'ｫ'
            else -> return null
        }
        // Dakuten/handakuten for voiced bases: approximate with base + mark
        val voice = when (base) {
            'ギ', 'ジ', 'ヂ', 'ビ' -> "ﾞ"
            'ピ' -> "ﾟ"
            else -> ""
        }
        return if (voice.isEmpty()) "$b$s" else "$b$voice$s"
    }

    private fun halfwidthSingle(c: Char): String = when (c) {
        'ア' -> "ｱ"; 'イ' -> "ｲ"; 'ウ' -> "ｳ"; 'エ' -> "ｴ"; 'オ' -> "ｵ"
        'カ' -> "ｶ"; 'キ' -> "ｷ"; 'ク' -> "ｸ"; 'ケ' -> "ｹ"; 'コ' -> "ｺ"
        'サ' -> "ｻ"; 'シ' -> "ｼ"; 'ス' -> "ｽ"; 'セ' -> "ｾ"; 'ソ' -> "ｿ"
        'タ' -> "ﾀ"; 'チ' -> "ﾁ"; 'ツ' -> "ﾂ"; 'テ' -> "ﾃ"; 'ト' -> "ﾄ"
        'ナ' -> "ﾅ"; 'ニ' -> "ﾆ"; 'ヌ' -> "ﾇ"; 'ネ' -> "ﾈ"; 'ノ' -> "ﾉ"
        'ハ' -> "ﾊ"; 'ヒ' -> "ﾋ"; 'フ' -> "ﾌ"; 'ヘ' -> "ﾍ"; 'ホ' -> "ﾎ"
        'マ' -> "ﾏ"; 'ミ' -> "ﾐ"; 'ム' -> "ﾑ"; 'メ' -> "ﾒ"; 'モ' -> "ﾓ"
        'ヤ' -> "ﾔ"; 'ユ' -> "ﾕ"; 'ヨ' -> "ﾖ"
        'ラ' -> "ﾗ"; 'リ' -> "ﾘ"; 'ル' -> "ﾙ"; 'レ' -> "ﾚ"; 'ロ' -> "ﾛ"
        'ワ' -> "ﾜ"; 'ヲ' -> "ｦ"; 'ン' -> "ﾝ"
        'ガ' -> "ｶﾞ"; 'ギ' -> "ｷﾞ"; 'グ' -> "ｸﾞ"; 'ゲ' -> "ｹﾞ"; 'ゴ' -> "ｺﾞ"
        'ザ' -> "ｻﾞ"; 'ジ' -> "ｼﾞ"; 'ズ' -> "ｽﾞ"; 'ゼ' -> "ｾﾞ"; 'ゾ' -> "ｿﾞ"
        'ダ' -> "ﾀﾞ"; 'ヂ' -> "ﾁﾞ"; 'ヅ' -> "ﾂﾞ"; 'デ' -> "ﾃﾞ"; 'ド' -> "ﾄﾞ"
        'バ' -> "ﾊﾞ"; 'ビ' -> "ﾋﾞ"; 'ブ' -> "ﾌﾞ"; 'ベ' -> "ﾍﾞ"; 'ボ' -> "ﾎﾞ"
        'パ' -> "ﾊﾟ"; 'ピ' -> "ﾋﾟ"; 'プ' -> "ﾌﾟ"; 'ペ' -> "ﾍﾟ"; 'ポ' -> "ﾎﾟ"
        'ッ' -> "ｯ"; 'ャ' -> "ｬ"; 'ュ' -> "ｭ"; 'ョ' -> "ｮ"
        'ー' -> "ｰ"; '・' -> "･"; '゛' -> "ﾞ"; '゜' -> "ﾟ"
        'ヴ' -> "ｳﾞ"
        else -> c.toString()
    }

    /** True when [reading] is a real Kuromoji/IPADIC reading (not unknown). */
    fun isValidReading(reading: String?): Boolean =
        !reading.isNullOrBlank() && reading != "*"

    /** Split hiragana/katakana into morae (handles yōon, sokuon). */
    fun morae(kana: String): List<String> {
        if (kana.isEmpty()) return emptyList()
        val h = toHiragana(kana)
        val out = ArrayList<String>()
        var i = 0
        val small = "ゃゅょぁぃぅぇぉゎ".toSet()
        while (i < h.length) {
            val c = h[i]
            if (c == 'っ' || c == 'ん' || c == 'ー') {
                out += c.toString()
                i++
            } else if (i + 1 < h.length && h[i + 1] in small) {
                out += h.substring(i, i + 2)
                i += 2
            } else {
                out += c.toString()
                i++
            }
        }
        return out
    }
}
