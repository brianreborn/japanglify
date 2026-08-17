package com.japanglify.app.domain.dictionary

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SenseSelectorTest {

    @Test
    fun modernPresetPrefersRicherSenseOverFirstSense() {
        // Real すごい data: sense 0 "terrible, dreadful" (2 glosses), sense 1
        // "amazing, great, wonderful, terrific" (4 glosses), neither tagged
        // dated. The Modern preset should surface sense 1.
        val terrible = SenseCandidate("terrible", PartOfSpeech.ADJECTIVE, glossCount = 2, isDated = false, rank = 0)
        val amazing = SenseCandidate("amazing", PartOfSpeech.ADJECTIVE, glossCount = 4, isDated = false, rank = 1)
        val toAGreatExtent = SenseCandidate("to a great extent", PartOfSpeech.ADVERB, glossCount = 1, isDated = false, rank = 2)

        val best = SenseSelector.pickBest(listOf(terrible, amazing, toAGreatExtent), SenseSelectionPreset.MODERN.weights!!)

        assertEquals(amazing, best)
    }

    @Test
    fun modernPresetPenalizesDatedSenseWhenRichnessIsComparable() {
        val modern = SenseCandidate("current meaning", PartOfSpeech.NOUN, glossCount = 2, isDated = false, rank = 1)
        val datedButRicher = SenseCandidate("archaic meaning", PartOfSpeech.NOUN, glossCount = 3, isDated = true, rank = 0)

        val best = SenseSelector.pickBest(listOf(datedButRicher, modern), SenseSelectionPreset.MODERN.weights!!)

        assertEquals(modern, best)
    }

    @Test
    fun classicalPresetFavorsDatedSense() {
        val modern = SenseCandidate("current meaning", PartOfSpeech.NOUN, glossCount = 2, isDated = false, rank = 1)
        val dated = SenseCandidate("archaic meaning", PartOfSpeech.NOUN, glossCount = 2, isDated = true, rank = 0)

        val best = SenseSelector.pickBest(listOf(modern, dated), SenseSelectionPreset.CLASSICAL.weights!!)

        assertEquals(dated, best)
    }

    @Test
    fun tiedScoreFallsBackToEarlierRank() {
        val weights = SenseWeights(richness = 0.0, position = 0.0, dated = 0.0)
        val first = SenseCandidate("a", PartOfSpeech.NOUN, glossCount = 1, isDated = false, rank = 0)
        val second = SenseCandidate("b", PartOfSpeech.NOUN, glossCount = 1, isDated = false, rank = 1)

        val best = SenseSelector.pickBest(listOf(second, first), weights)

        assertEquals(first, best)
    }

    @Test
    fun emptyCandidateListReturnsNull() {
        assertNull(SenseSelector.pickBest(emptyList(), SenseSelectionPreset.MODERN.weights!!))
    }

    @Test
    fun customPresetHasNoOwnWeights() {
        assertNull(SenseSelectionPreset.CUSTOM.weights)
    }

    @Test
    fun unknownPresetIdFallsBackToModern() {
        assertEquals(SenseSelectionPreset.MODERN, SenseSelectionPreset.fromId("nonsense"))
        assertEquals(SenseSelectionPreset.MODERN, SenseSelectionPreset.fromId(null))
    }
}
