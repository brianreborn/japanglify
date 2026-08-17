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
        /** [weights] drives [SenseSelector] when a provider stores multiple candidate senses per headword. */
        fun lookup(baseForm: String, weights: SenseWeights): DictionaryEntry?
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
    ): List<GlossResult?> =
        tokens.map { token ->
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
            if (token.isBoundToPrevious) return@map null
            // Prefer Kuromoji's own contextual particle tag over re-deriving
            // it from the dictionary lookup below: a dictionary match is
            // keyed on baseForm/surface alone, out of context, and can land
            // on the wrong same-spelling headword (a real risk for common
            // single-kana particles) -- Kuromoji already knows definitively
            // whether *this* token, in *this* sentence, is a particle.
            // format()'s own entry.partOfSpeech check stays as a second,
            // independent safety net for a ReadingProvider that doesn't set
            // this flag.
            if (token.isParticle) return@map null
            val key = token.baseForm ?: token.surface
            val entry = dictionary.lookup(key, senseWeights) ?: return@map null
            format(entry)?.let { GlossResult(it, entry.partOfSpeech) }
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
}
