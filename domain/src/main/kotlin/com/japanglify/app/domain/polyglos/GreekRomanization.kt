package com.japanglify.app.domain.polyglos

/**
 * Phonetic Latin scheme for Greek analyzers.
 * Ancient default: [ERASMIAN]; modern default: [MODERN_PHONEMIC] (SBL-like).
 */
enum class GreekRomanization(val id: String) {
    ERASMIAN("erasmian"),
    SBL("sbl"),
    MODERN_PHONEMIC("modern_phonemic");

    companion object {
        fun fromId(id: String?): GreekRomanization =
            entries.firstOrNull { it.id.equals(id, ignoreCase = true) } ?: ERASMIAN
    }
}
