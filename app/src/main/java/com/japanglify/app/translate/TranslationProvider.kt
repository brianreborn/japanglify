package com.japanglify.app.translate

/**
 * Blocking Japanese→English translation seam — mirrors
 * [com.japanglify.app.domain.JapaneseAnalyzer.ReadingProvider]'s shape.
 * Callers are responsible for running this off the main thread (see
 * [Translator]) and for treating a null result as "no 4th line," never
 * as an error to surface.
 */
fun interface TranslationProvider {
    fun translate(text: String): String?
}
