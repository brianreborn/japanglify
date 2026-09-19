package com.japanglify.app.domain.polyglos

/**
 * Result of [ScriptDetector.detect]: counts of script runs plus the dominant
 * linguistic family (Japanese / Greek / Hebrew), or null when only Latin/common.
 */
data class ScriptProfile(
    val japaneseUnits: Int = 0,
    val greekUnits: Int = 0,
    val hebrewUnits: Int = 0,
    val latinUnits: Int = 0,
    val otherUnits: Int = 0,
    /** True when more than one of ja/el/he scored > 0. */
    val mixed: Boolean = false,
    /** Dominant linguistic script, or null for Latin-only / empty. */
    val dominant: ScriptKind? = null
) {
    val isLatinOnly: Boolean
        get() = dominant == null && greekUnits == 0 && hebrewUnits == 0 && japaneseUnits == 0
}
