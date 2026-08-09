package com.japanglify.app.domain.emoji

import com.japanglify.app.domain.EmojiPrecisionTier
import com.japanglify.app.domain.dictionary.GlossAnnotator
import com.japanglify.app.domain.dictionary.PartOfSpeech

/**
 * Looks up a single, precise emoji for an already-resolved English gloss —
 * a second annotation pass layered on top of
 * [com.japanglify.app.domain.dictionary.GlossAnnotator], not a replacement
 * for it (see the plan's "English→emoji annotation" design section). The
 * match key is the gloss text itself, matched against CLDR's emoji short
 * names — the Japanese word never enters this step at all.
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
            val word = result.text.lowercase()
            provider.lookup(word, tier) ?: categoryFallback(word, tier)
        }

    /**
     * [CategoryEmoji]'s hand-reviewed category clusters ("animal" -> a
     * worm, a bird, a fish) -- pure static data, no download needed, so it
     * lives here rather than behind [provider]. Absolute last resort: only
     * consulted at LOOSE, after [provider] itself already tried STRICT,
     * MEDIUM, and its own further LOOSE fallbacks and found nothing.
     */
    private fun categoryFallback(word: String, tier: EmojiPrecisionTier): String? =
        if (tier == EmojiPrecisionTier.LOOSE) CategoryEmoji.TABLE[word] else null
}
