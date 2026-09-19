package com.japanglify.app.domain.polyglos

/** Whitespace/punctuation splitter; combining marks stay with their base letter. */
internal object WhitespaceTokenizer {
    data class Token(val surface: String, val isWord: Boolean)

    fun tokenize(text: CharSequence): List<Token> {
        if (text.isEmpty()) return emptyList()
        val nfc = java.text.Normalizer.normalize(text.toString(), java.text.Normalizer.Form.NFC)
        val result = ArrayList<Token>()
        val buf = StringBuilder()
        var wordMode: Boolean? = null

        fun flush() {
            if (buf.isEmpty()) return
            result.add(Token(buf.toString(), wordMode == true))
            buf.clear()
            wordMode = null
        }

        var i = 0
        while (i < nfc.length) {
            val cp = Character.codePointAt(nfc, i)
            val len = Character.charCount(cp)
            val type = Character.getType(cp)
            val isMark = type == Character.NON_SPACING_MARK.toInt() ||
                type == Character.COMBINING_SPACING_MARK.toInt()
            val isSpace = Character.isWhitespace(cp)
            val isWordChar = Character.isLetter(cp) || Character.isDigit(cp) || isMark

            when {
                isSpace -> flush()
                !isWordChar && !isMark -> {
                    flush()
                    result.add(Token(String(Character.toChars(cp)), false))
                }
                else -> {
                    if (wordMode == false) flush()
                    wordMode = true
                    buf.append(nfc, i, i + len)
                }
            }
            i += len
        }
        flush()
        return result
    }
}
