package com.japanglify.app.domain.polyglos

/**
 * Unicode-block based script classifier for analyzer routing.
 * Counts code points (not grapheme clusters) in linguistic buckets;
 * Common/Inherited (spaces, punctuation, marks) are ignored for dominance.
 */
object ScriptDetector {

    fun detect(text: CharSequence): ScriptProfile {
        if (text.isEmpty()) return ScriptProfile()

        var ja = 0
        var el = 0
        var he = 0
        var la = 0
        var other = 0

        var i = 0
        while (i < text.length) {
            val cp = Character.codePointAt(text, i)
            i += Character.charCount(cp)
            when (classify(cp)) {
                ScriptKind.JAPANESE -> ja++
                ScriptKind.GREEK -> el++
                ScriptKind.HEBREW -> he++
                ScriptKind.LATIN -> la++
                ScriptKind.COMMON -> { /* ignore for dominance */ }
                ScriptKind.OTHER -> other++
            }
        }

        val linguistic = listOf(
            ScriptKind.JAPANESE to ja,
            ScriptKind.GREEK to el,
            ScriptKind.HEBREW to he
        ).filter { it.second > 0 }

        val mixed = linguistic.size > 1
        val dominant = linguistic.maxByOrNull { it.second }?.first

        return ScriptProfile(
            japaneseUnits = ja,
            greekUnits = el,
            hebrewUnits = he,
            latinUnits = la,
            otherUnits = other,
            mixed = mixed,
            dominant = dominant
        )
    }

    fun classify(codePoint: Int): ScriptKind {
        // Combining marks / format — treat as common so they ride with host script.
        val type = Character.getType(codePoint)
        if (type == Character.NON_SPACING_MARK.toInt() ||
            type == Character.COMBINING_SPACING_MARK.toInt() ||
            type == Character.ENCLOSING_MARK.toInt() ||
            type == Character.FORMAT.toInt() ||
            type == Character.SPACE_SEPARATOR.toInt() ||
            type == Character.LINE_SEPARATOR.toInt() ||
            type == Character.PARAGRAPH_SEPARATOR.toInt() ||
            Character.isWhitespace(codePoint)
        ) {
            return ScriptKind.COMMON
        }

        return when (codePoint) {
            in 0x3040..0x309F -> ScriptKind.JAPANESE // Hiragana
            in 0x30A0..0x30FF -> ScriptKind.JAPANESE // Katakana
            in 0x31F0..0x31FF -> ScriptKind.JAPANESE // Katakana Phonetic Ext
            in 0xFF65..0xFF9F -> ScriptKind.JAPANESE // Halfwidth katakana
            in 0x3400..0x4DBF -> ScriptKind.JAPANESE // CJK Ext A
            in 0x4E00..0x9FFF -> ScriptKind.JAPANESE // CJK Unified
            in 0xF900..0xFAFF -> ScriptKind.JAPANESE // CJK Compatibility
            in 0x0370..0x03FF -> ScriptKind.GREEK   // Greek and Coptic
            in 0x1F00..0x1FFF -> ScriptKind.GREEK   // Greek Extended (polytonic)
            in 0x0590..0x05FF -> ScriptKind.HEBREW  // Hebrew
            in 0xFB1D..0xFB4F -> ScriptKind.HEBREW  // Hebrew presentation forms
            in 0x0041..0x005A, in 0x0061..0x007A -> ScriptKind.LATIN
            in 0x00C0..0x024F -> ScriptKind.LATIN   // Latin-1 / Ext
            else -> {
                if (Character.isLetter(codePoint) || Character.isDigit(codePoint)) {
                    ScriptKind.OTHER
                } else {
                    ScriptKind.COMMON
                }
            }
        }
    }
}
