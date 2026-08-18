package com.japanglify.app.domain.dictionary

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DictionaryEntryTest {

    @Test
    fun mapsKuromojiConjugationTypesToJmdictVerbClassPrefixes() {
        // Exact Kuromoji/IPADIC conjugationType strings, confirmed live via
        // Tokenizer output for 勉強する/手を擦る/新聞を刷る/ひげを剃る/友達が来る.
        assertEquals("vs", jmdictVerbConjugationPrefix("サ変・スル"))
        assertEquals("v5r", jmdictVerbConjugationPrefix("五段・ラ行"))
        assertEquals("v1", jmdictVerbConjugationPrefix("一段"))
        assertEquals("vk", jmdictVerbConjugationPrefix("カ変・来ル"))
    }

    @Test
    fun mapsEveryGodanRow() {
        assertEquals("v5k", jmdictVerbConjugationPrefix("五段・カ行"))
        assertEquals("v5g", jmdictVerbConjugationPrefix("五段・ガ行"))
        assertEquals("v5s", jmdictVerbConjugationPrefix("五段・サ行"))
        assertEquals("v5t", jmdictVerbConjugationPrefix("五段・タ行"))
        assertEquals("v5n", jmdictVerbConjugationPrefix("五段・ナ行"))
        assertEquals("v5b", jmdictVerbConjugationPrefix("五段・バ行"))
        assertEquals("v5m", jmdictVerbConjugationPrefix("五段・マ行"))
        assertEquals("v5u", jmdictVerbConjugationPrefix("五段・ワ行促音便"))
    }

    @Test
    fun nullForNonVerbTokensAndUnknownTypes() {
        // "*" is Kuromoji's own placeholder for "not applicable" (particles,
        // nouns, punctuation) -- must mean "no hint," not "no match."
        assertNull(jmdictVerbConjugationPrefix("*"))
        assertNull(jmdictVerbConjugationPrefix(null))
        assertNull(jmdictVerbConjugationPrefix("文語・ル"))
    }
}
