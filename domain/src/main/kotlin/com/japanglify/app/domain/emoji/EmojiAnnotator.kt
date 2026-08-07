package com.japanglify.app.domain.emoji

import com.japanglify.app.domain.EmojiPrecisionTier
import com.japanglify.app.domain.dictionary.GlossAnnotator
import com.japanglify.app.domain.dictionary.PartOfSpeech

/**
 * Looks up a single, precise emoji for an already-resolved English gloss —
 * a second annotation pass layered on top of
 * [com.japanglify.app.domain.dictionary.GlossAnnotator], not a replacement
 * for it (see the plan's "English→emoji annotation" design section). The
 * match key is the gloss text itself (after stripping its part-of-speech
 * abbreviation prefix, e.g. "n. paper" -> "paper"), matched against CLDR's
 * emoji short names — the Japanese word never enters this step at all.
 */
class EmojiAnnotator(private val provider: EmojiProvider) {

    fun interface EmojiProvider {
        /**
         * [englishWord] is already lowercased/trimmed. [tier] tells the
         * provider how far to relax matching -- STRICT/LOOSE should only
         * ever consult exact (tts) matches; MEDIUM may also fall back to a
         * looser match if no exact one exists. Returns null if no match at
         * that tier.
         */
        fun lookup(englishWord: String, tier: EmojiPrecisionTier): String?
    }

    /**
     * One result per input gloss result, same size/order — null means no
     * gloss to match against, the token's part of speech falls outside
     * [posScope], or no match exists at [tier].
     */
    fun annotate(
        glossResults: List<GlossAnnotator.GlossResult?>,
        posScope: Set<PartOfSpeech>,
        tier: EmojiPrecisionTier
    ): List<String?> =
        glossResults.map { result ->
            if (result == null) return@map null
            // Only a *known* part of speech can be scoped out -- an unknown
            // one (null) shouldn't be silently blocked by a scope filter it
            // can't meaningfully be checked against.
            if (result.partOfSpeech != null && result.partOfSpeech !in posScope) return@map null
            provider.lookup(stripPosPrefix(result.text).lowercase(), tier)
        }

    private fun stripPosPrefix(gloss: String): String {
        for (abbr in POS_ABBREVIATIONS) {
            if (gloss.startsWith("$abbr ")) return gloss.substring(abbr.length + 1)
        }
        return gloss
    }

    companion object {
        private val POS_ABBREVIATIONS =
            PartOfSpeech.entries.map { it.abbreviation }.filter { it.isNotEmpty() }
    }
}
