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
        /**
         * [reading] is the token's kana reading (katakana or hiragana; null
         * when unknown or when looking up a multi-token phrase). When present
         * it disambiguates same-spelling headwords read differently — e.g.
         * 僕 is read ぼく ("I, me") here, not しもべ ("servant") — so a
         * reading-aware provider restricts its candidate senses to the
         * matching reading before scoring. [weights] drives [SenseSelector]
         * when several candidate senses remain.
         */
        fun lookup(baseForm: String, reading: String?, weights: SenseWeights): DictionaryEntry?
    }

    /**
     * [text] is the gloss text, direct with no abbreviation/metadata prefix
     * (e.g. just "paper", never "n. paper") -- found this reads as noise,
     * not help, live. [partOfSpeech] is the entry's real category and is
     * never shown in [text]; it exists purely for a consumer like
     * [com.japanglify.app.domain.emoji.EmojiAnnotator] that needs the actual
     * category to apply a part-of-speech scope filter.
     */
    data class GlossResult(val text: String, val partOfSpeech: PartOfSpeech?)

    /**
     * One result per input token, same size/order as [tokens] — null means
     * either no dictionary entry was found (a genuinely missing word, or a
     * Kuromoji/JMdict lexical mismatch; see the plan's open questions) or a
     * deliberately omitted gloss (every particle -- see [format] -- plus
     * every [JapaneseAnalyzer.SurfaceReading.isBoundToPrevious] token, see
     * below).
     */
    fun annotate(
        tokens: List<JapaneseAnalyzer.SurfaceReading>,
        senseWeights: SenseWeights = SenseSelectionPreset.MODERN.weights!!
    ): List<GlossResult?> {
        val results = arrayOfNulls<GlossResult>(tokens.size)
        var i = 0
        while (i < tokens.size) {
            // Longest-match phrase first: several consecutive tokens whose
            // concatenated surface is itself a dictionary headword (a real set
            // expression, e.g. ご+機嫌+よう -> ご機嫌よう "nice to see you /
            // good day"). Kuromoji splits such phrases into their pieces, and
            // glossing the pieces separately is both wrong and noisy, so a
            // whole-phrase entry wins when one exists. The gloss rides on the
            // span's first token (like split-kanji/okurigana romaji does);
            // the rest stay null.
            val phrase = longestPhraseMatch(tokens, i, senseWeights)
            if (phrase != null) {
                val (span, entry) = phrase
                results[i] = format(entry)?.let { GlossResult(it, entry.partOfSpeech) }
                i += span
                continue
            }
            results[i] = glossToken(tokens[i], senseWeights)
            i++
        }
        return results.toList()
    }

    /**
     * The longest span of [MIN_PHRASE_TOKENS]..[MAX_PHRASE_TOKENS] tokens
     * starting at [start] whose concatenated surface is a dictionary
     * headword, or null if none is. A phrase has no single reading of its
     * own, so the lookup passes `reading = null` (no reading filter).
     */
    private fun longestPhraseMatch(
        tokens: List<JapaneseAnalyzer.SurfaceReading>,
        start: Int,
        weights: SenseWeights
    ): Pair<Int, DictionaryEntry>? {
        val maxSpan = minOf(MAX_PHRASE_TOKENS, tokens.size - start)
        for (span in maxSpan downTo MIN_PHRASE_TOKENS) {
            val surface = buildString {
                for (j in start until start + span) append(tokens[j].surface)
            }
            dictionary.lookup(surface, null, weights)?.let { return span to it }
        }
        return null
    }

    private fun glossToken(
        token: JapaneseAnalyzer.SurfaceReading,
        weights: SenseWeights
    ): GlossResult? {
        // A conjugation ending / auxiliary / copula (ました, ない, だ, ...)
        // isn't an independent word -- it completes the previous token's
        // inflected form (see isBoundToPrevious's own doc). Glossing it
        // separately reads as a stray, disconnected word, and JMdict's
        // POS code for these doesn't reliably land on PARTICLE either
        // (copula is its own JMdict tag, "cop", which format()'s
        // PARTICLE-only check never catches) -- found live via real
        // device UAT: だ's own gloss rendered as an unrelated word
        // jammed with zero gap right against the previous word's gloss
        // (isBoundToPrevious cells get no word-gap by design), reading
        // as one garbled word, e.g. "wonderfuldui" for 不思議な. Skipping
        // the lookup entirely for these tokens fixes both problems at
        // once: no stray gloss, and nothing left to jam into the gap.
        if (token.isBoundToPrevious) return null
        // Prefer Kuromoji's own contextual particle tag over re-deriving
        // it from the dictionary lookup below: a dictionary match is
        // keyed on baseForm/surface alone, out of context, and can land
        // on the wrong same-spelling headword (a real risk for common
        // single-kana particles) -- Kuromoji already knows definitively
        // whether *this* token, in *this* sentence, is a particle.
        // format()'s own entry.partOfSpeech check stays as a second,
        // independent safety net for a ReadingProvider that doesn't set
        // this flag.
        if (token.isParticle) return null
        val key = token.baseForm ?: token.surface
        // Pass the token's reading so the provider can disambiguate a
        // same-spelling headword read two ways (see DictionaryProvider.lookup).
        val entry = dictionary.lookup(key, token.reading, weights) ?: return null
        return format(entry)?.let { GlossResult(it, entry.partOfSpeech) }
    }

    private fun format(entry: DictionaryEntry): String? {
        // Every particle is omitted, not just the abstract-marker subset
        // (は/が/を) an earlier version of this curated by hand. Found live
        // via real device UAT: の's actual JMdict gloss ("possessive /
        // nominalizing particle" plus, for some entries, a much longer
        // explanatory definition) reads as an entire sentence crammed into
        // one word's slot -- unusable regardless of whether の counts as
        // "abstract" or "concrete." A JMdict gloss is written for a
        // dictionary entry, not for a one-line annotation under a single
        // character, and that mismatch isn't particular to a handful of
        // grammatical-role markers -- it affects particles generally, so
        // the whole category is omitted rather than trying to hand-curate
        // which particles' dictionary glosses happen to be short enough.
        if (entry.partOfSpeech == PartOfSpeech.PARTICLE) return null
        // No "n."/"v."/etc. prefix -- a direct translation, not a dictionary
        // entry with lexicographic metadata attached. Tried showing it only
        // when a headword's senses genuinely disagreed on part of speech;
        // still read as unwanted commentary rather than help, per direct
        // feedback, so it's gone unconditionally rather than narrowed further.
        // Same reasoning for JMdict's own "to " infinitive-marker convention
        // on verb glosses ("to see", "to become") -- it's a citation-form
        // artifact of how JMdict writes verb entries, not information: 見る
        // means "see," full stop, and marking that as an infinitive adds
        // nothing a reader uses. Gated on VERB specifically, not applied to
        // every gloss: an expression/adverb gloss can legitimately start
        // with "to " as real content ("to a certain extent," "to no end"),
        // where stripping it would corrupt the meaning rather than
        // declutter it -- only a verb's leading "to " is purely a citation-
        // form marker with nothing lost by removing it.
        return if (entry.partOfSpeech == PartOfSpeech.VERB) entry.gloss.removePrefix("to ") else entry.gloss
    }

    private companion object {
        /** Phrase lookup only kicks in for multi-token spans. */
        const val MIN_PHRASE_TOKENS = 2
        /**
         * Longest span the greedy phrase pass will try to match as one
         * dictionary entry. 4 comfortably covers set expressions like
         * ご機嫌よう (3 tokens) without turning every lookup into a long chain
         * of speculative DB queries on a big paste.
         */
        const val MAX_PHRASE_TOKENS = 4
    }
}
