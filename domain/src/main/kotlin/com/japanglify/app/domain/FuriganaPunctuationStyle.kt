package com.japanglify.app.domain

/**
 * How punctuation is mirrored onto the furigana row of an interlinear block.
 *
 * [NONE] is the default: a bare 、／。／？ sitting alone in the ruby row reads
 * as visual noise rather than a reading, since punctuation has no reading to
 * begin with — it only got a furigana-row entry in the first place so the
 * column count (and therefore alignment) stayed in sync with the base row.
 */
enum class FuriganaPunctuationStyle(val id: String, val displayName: String) {
    /** Leave the furigana-row cell blank for punctuation (default). */
    NONE(id = "none", displayName = "None (blank)"),

    /** Repeat the original Japanese punctuation mark verbatim. */
    ORIGINAL(id = "original", displayName = "Original (、／。／？)"),

    /** Use the Latin form (,／.／?) instead. */
    ROMAN(id = "roman", displayName = "Roman (,／.？)");

    companion object {
        fun fromId(id: String?): FuriganaPunctuationStyle =
            entries.firstOrNull { it.id == id } ?: NONE
    }
}
