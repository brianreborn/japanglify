package com.japanglify.app.domain.polyglos

/**
 * Coarse script families used for analyzer routing.
 * [LATIN] / [COMMON] never force an analyzer by themselves.
 */
enum class ScriptKind {
    JAPANESE,
    GREEK,
    HEBREW,
    LATIN,
    COMMON,
    OTHER
}
