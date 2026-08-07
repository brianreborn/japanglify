package com.japanglify.app.domain.dictionary

import org.junit.Assert.assertEquals
import org.junit.Test

class PartOfSpeechTest {

    @Test
    fun mapsCommonJmdictVerbCodes() {
        // v1 = ichidan, v5r/v5k/v5s/... = godan by ending, vs = suru, vk = kuru —
        // v1 only needs the broad category, not the conjugation subtype.
        for (code in listOf("v1", "v5r", "v5k", "v5s", "vs", "vk")) {
            assertEquals(code, PartOfSpeech.VERB, PartOfSpeech.fromJmdictCode(code))
        }
    }

    @Test
    fun mapsAdjectiveCodesBeforeVerbOrNoun() {
        for (code in listOf("adj-i", "adj-na", "adj-no")) {
            assertEquals(code, PartOfSpeech.ADJECTIVE, PartOfSpeech.fromJmdictCode(code))
        }
    }

    @Test
    fun mapsNounCodes() {
        for (code in listOf("n", "n-adv", "n-pref", "n-suf")) {
            assertEquals(code, PartOfSpeech.NOUN, PartOfSpeech.fromJmdictCode(code))
        }
    }

    @Test
    fun mapsRemainingCategories() {
        assertEquals(PartOfSpeech.ADVERB, PartOfSpeech.fromJmdictCode("adv"))
        assertEquals(PartOfSpeech.PARTICLE, PartOfSpeech.fromJmdictCode("prt"))
        assertEquals(PartOfSpeech.PRONOUN, PartOfSpeech.fromJmdictCode("pn"))
        assertEquals(PartOfSpeech.CONJUNCTION, PartOfSpeech.fromJmdictCode("conj"))
        assertEquals(PartOfSpeech.INTERJECTION, PartOfSpeech.fromJmdictCode("int"))
        assertEquals(PartOfSpeech.AUXILIARY, PartOfSpeech.fromJmdictCode("aux-v"))
        assertEquals(PartOfSpeech.AUXILIARY, PartOfSpeech.fromJmdictCode("aux-adj"))
        assertEquals(PartOfSpeech.PREFIX, PartOfSpeech.fromJmdictCode("pref"))
        assertEquals(PartOfSpeech.SUFFIX, PartOfSpeech.fromJmdictCode("suf"))
        assertEquals(PartOfSpeech.COUNTER, PartOfSpeech.fromJmdictCode("ctr"))
        assertEquals(PartOfSpeech.EXPRESSION, PartOfSpeech.fromJmdictCode("exp"))
    }

    @Test
    fun unknownCodeMapsToOther() {
        assertEquals(PartOfSpeech.OTHER, PartOfSpeech.fromJmdictCode("totally-unrecognized-tag"))
    }

    @Test
    fun abbreviationsMatchStandardLexicographicConvention() {
        assertEquals("n.", PartOfSpeech.NOUN.abbreviation)
        assertEquals("v.", PartOfSpeech.VERB.abbreviation)
        assertEquals("adj.", PartOfSpeech.ADJECTIVE.abbreviation)
        assertEquals("adv.", PartOfSpeech.ADVERB.abbreviation)
        assertEquals("part.", PartOfSpeech.PARTICLE.abbreviation)
    }
}
