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
     * [text] is the formatted, ready-to-render string (e.g. "n. paper").
     * [partOfSpeech] is the entry's *actual* category regardless of whether
     * [text] happens to show it -- [DictionaryEntry.partOfSpeech] is already
     * null'd out at the DB layer for non-ambiguous headwords (see
     * `SqliteDictionaryProvider`/`MARK_POS_AMBIGUOUS_SQL`) purely so the
     * abbreviation isn't *displayed*; that's a separate concern from a
     * consumer like [com.japanglify.app.domain.emoji.EmojiAnnotator] needing
     * the real category to apply a part-of-speech scope filter, so this
     * carries [DictionaryEntry.partOfSpeech] unchanged, independent of [text].
     */
    data class GlossResult(val text: String, val partOfSpeech: PartOfSpeech?)

    /**
     * One result per input token, same size/order as [tokens] — null means
     * either no dictionary entry was found (a genuinely missing word, or a
     * Kuromoji/JMdict lexical mismatch; see the plan's open questions) or a
     * deliberately omitted gloss (see [OMITTED_ABSTRACT_PARTICLES]).
     */
    fun annotate(tokens: List<JapaneseAnalyzer.SurfaceReading>): List<GlossResult?> =
        tokens.map { token ->
            val key = token.baseForm ?: token.surface
            val entry = dictionary.lookup(key) ?: return@map null
            format(entry)?.let { GlossResult(it, entry.partOfSpeech) }
        }

    private fun format(entry: DictionaryEntry): String? {
        if (entry.headword in OMITTED_ABSTRACT_PARTICLES) return null
        val pos = entry.partOfSpeech?.abbreviation.orEmpty()
        return if (pos.isEmpty()) entry.gloss else "$pos ${entry.gloss}"
    }

    companion object {
        /**
         * は/が/を mark a sentence's grammatical role (topic/subject/object)
         * but don't carry independent lexical meaning the way a preposition
         * does -- forcing them into a one- or two-word gloss ("topic
         * marker") tends to read as filler rather than genuine help.
         * Particles with an actual semantic correlate keep their gloss
         * (へ → "to; toward", と → "and; with", から → "from", etc.) since
         * those translations are concrete and unambiguous, not abstract.
         * A curated set, not a POS-code rule: JMdict's "prt" tag covers
         * both kinds equally, and there's no automatic way to tell "pure
         * grammatical marker" from "particle with real meaning" from the
         * data alone.
         */
        private val OMITTED_ABSTRACT_PARTICLES = setOf("は", "が", "を")
    }
}
