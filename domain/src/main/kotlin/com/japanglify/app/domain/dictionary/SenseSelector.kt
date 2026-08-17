package com.japanglify.app.domain.dictionary

/**
 * One JMdict sense, reduced to just the signals [SenseSelector] scores on,
 * plus the reading that goes with it (not scored, just carried through).
 * [rank] is the sense's position in JMdict's own ordering (0 = first/
 * "primary" per JMdict's editorial convention — see [SenseSelector]'s doc
 * for why that convention alone is an unreliable proxy for "most useful
 * gloss to show a learner today").
 */
data class SenseCandidate(
    val gloss: String,
    val partOfSpeech: PartOfSpeech?,
    val glossCount: Int,
    val isDated: Boolean,
    val rank: Int,
    val reading: String? = null
)

/**
 * Weights for [SenseSelector.pickBest]'s scoring formula. [richness] and
 * [position] are expected non-negative (higher = more influence); [dated]
 * is signed — negative penalizes a dated/archaic sense (the default,
 * "Modern" preset), positive instead favors one (a "Classical / dated
 * source text" preset, for when the input itself is old and an archaic
 * sense is actually the *right* one), zero ignores the signal entirely.
 */
data class SenseWeights(
    val richness: Double,
    val position: Double,
    val dated: Double
)

/**
 * Picks which of a headword's candidate JMdict senses to actually show,
 * replacing the old "always sense 0" rule (see [DictionaryEntry]'s doc).
 * JMdict's own sense order reflects lexicographic/editorial convention —
 * often the word's older or more literal meaning — not modern usage
 * frequency; for a word like すごい ("terrible, dreadful" listed first,
 * "amazing, great, wonderful" second) that makes "just take sense 0"
 * actively misleading for a live learner-facing gloss.
 *
 * No per-sense usage-frequency data exists in JMdict to resolve this
 * precisely, so the score is a small weighted blend of what *is* available:
 * how many English synonyms a sense lists ([SenseCandidate.glossCount] —
 * richer senses tend to be the ones in live use), a small tiebreak toward
 * earlier senses (still JMdict's own editorial signal, just not decisive on
 * its own), and whether JMdict tags the sense archaic/dated/obscure.
 *
 * Future directions, not yet implemented:
 * - Use the surrounding sentence's own context to disambiguate rather than
 *   a single blended score globally per headword (e.g. すごい next to a
 *   clearly negative clause should still resolve to "terrible"). Worth
 *   doing once there's a genuinely trivial way to do it that meaningfully
 *   improves emoji-match quality — not worth building speculatively before
 *   that.
 * - [isDated] currently lumps every JMdict "dated" `misc` tag (arch/obs/
 *   obsc/dated) into one boolean with one shared weight. JMdict's tags
 *   actually distinguish different flavors/degrees of "old" (archaic vs.
 *   obsolete vs. merely dated), which a single weight can't target — a
 *   per-tag weight (or an explicit "which period is this text from" input)
 *   would let the Classical preset aim at a specific era instead of just
 *   "prefer old over new."
 */
object SenseSelector {
    fun pickBest(candidates: List<SenseCandidate>, weights: SenseWeights): SenseCandidate? {
        var best: SenseCandidate? = null
        var bestScore = Double.NEGATIVE_INFINITY
        for (candidate in candidates) {
            val s = score(candidate, weights)
            // Strictly-greater score always wins; an exact tie falls back
            // to the earlier (smaller-rank) sense, matching JMdict's own
            // editorial ordering as the tiebreak of last resort.
            if (best == null || s > bestScore || (s == bestScore && candidate.rank < best.rank)) {
                best = candidate
                bestScore = s
            }
        }
        return best
    }

    private fun score(candidate: SenseCandidate, weights: SenseWeights): Double =
        weights.richness * candidate.glossCount -
            weights.position * candidate.rank +
            weights.dated * (if (candidate.isDated) 1.0 else 0.0)
}
