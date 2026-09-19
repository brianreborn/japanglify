package com.japanglify.app.domain.polyglos

/** Academic romanization for Biblical / modern Hebrew phonetic layer. */
enum class HebrewRomanization(val id: String) {
    ACADEMIC("hebrew_academic");

    companion object {
        fun fromId(id: String?): HebrewRomanization =
            entries.firstOrNull { it.id.equals(id, ignoreCase = true) } ?: ACADEMIC
    }
}
