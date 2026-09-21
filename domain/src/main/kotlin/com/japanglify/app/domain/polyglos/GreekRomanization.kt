package com.japanglify.app.domain.polyglos

/**
 * Phonetic Latin scheme for Greek analyzers.
 * Ancient default: [ERASMIAN]; modern default: [MODERN_PHONEMIC].
 */
enum class GreekRomanization(val id: String, val displayName: String) {
    ERASMIAN("erasmian", "Erasmian (ancient default)"),
    SBL("sbl", "SBL academic"),
    MODERN_PHONEMIC("modern_phonemic", "Modern phonemic");

    companion object {
        fun fromId(id: String?): GreekRomanization =
            entries.firstOrNull { it.id.equals(id, ignoreCase = true) } ?: ERASMIAN
    }
}
