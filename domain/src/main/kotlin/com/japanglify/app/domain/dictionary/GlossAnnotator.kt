package com.japanglify.app.domain.dictionary

import com.japanglify.app.domain.JapaneseAnalyzer

/**
 * Looks up an English gloss for each token via its dictionary/base form.
 *
 * Takes the *whole* token list (not one token at a time) even though v1
 * never looks across token boundaries — this is the deliberate extension
 * point for a future phrasal-grouping pass (e.g. ~ている, ~てください
 * auxiliary chains) to become an internal change to [annotate]'s loop,
 * not a rearchitecture. See the plan's "Phrasal grouping" backlog note.
 */
class GlossAnnotator(private val dictionary: DictionaryProvider) {

    fun interface DictionaryProvider {
        fun lookup(baseForm: String): DictionaryEntry?
    }

    /**
     * One result per input token, same size/order as [tokens] — null means
     * no dictionary entry was found (a genuinely missing word, or a
     * Kuromoji/JMdict lexical mismatch; see the plan's open questions).
     * Formatted ready to render, e.g. "n. paper".
     */
    fun annotate(tokens: List<JapaneseAnalyzer.SurfaceReading>): List<String?> =
        tokens.map { token ->
            val key = token.baseForm ?: token.surface
            val entry = dictionary.lookup(key) ?: return@map null
            format(entry)
        }

    private fun format(entry: DictionaryEntry): String {
        val pos = entry.partOfSpeech?.abbreviation.orEmpty()
        return if (pos.isEmpty()) entry.gloss else "$pos ${entry.gloss}"
    }
}
