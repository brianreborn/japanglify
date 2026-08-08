package com.japanglify.app.domain.emoji

import com.japanglify.app.domain.EmojiPrecisionTier
import com.japanglify.app.domain.dictionary.GlossAnnotator
import com.japanglify.app.domain.dictionary.PartOfSpeech
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class EmojiAnnotatorTest {

    private val allPos = PartOfSpeech.entries.toSet()

    private fun fakeProvider(vararg entries: Pair<String, String>) =
        EmojiAnnotator(EmojiAnnotator.EmojiProvider { word, _ -> entries.toMap()[word] })

    private fun tieredProvider(strict: Map<String, String>, medium: Map<String, String>) =
        EmojiAnnotator(EmojiAnnotator.EmojiProvider { word, tier ->
            strict[word] ?: medium[word]?.takeIf { tier == EmojiPrecisionTier.MEDIUM }
        })

    @Test
    fun matchesPlainGloss() {
        val annotator = fakeProvider("paper" to "📄")
        val result = annotator.annotate(
            listOf(GlossAnnotator.GlossResult("paper", PartOfSpeech.NOUN)),
            allPos,
            EmojiPrecisionTier.STRICT
        )
        assertEquals("📄", result[0])
    }

    @Test
    fun noMatchYieldsNull() {
        val annotator = fakeProvider("paper" to "📄")
        val result = annotator.annotate(
            listOf(GlossAnnotator.GlossResult("study", PartOfSpeech.NOUN)),
            allPos,
            EmojiPrecisionTier.STRICT
        )
        assertNull(result[0])
    }

    @Test
    fun nullGlossResultYieldsNullEmoji() {
        val annotator = fakeProvider("paper" to "📄")
        val result = annotator.annotate(listOf(null), allPos, EmojiPrecisionTier.STRICT)
        assertNull(result[0])
    }

    @Test
    fun partOfSpeechOutsideScopeIsExcluded() {
        val annotator = fakeProvider("go" to "🏃")
        val result = annotator.annotate(
            listOf(GlossAnnotator.GlossResult("go", PartOfSpeech.VERB)),
            setOf(PartOfSpeech.NOUN),
            EmojiPrecisionTier.STRICT
        )
        assertNull(result[0])
    }

    @Test
    fun unknownPartOfSpeechIsNotBlockedByScope() {
        // A gloss with no resolvable part of speech shouldn't be silently
        // excluded by a scope filter it can't meaningfully be checked against.
        val annotator = fakeProvider("mystery" to "❓")
        val result = annotator.annotate(
            listOf(GlossAnnotator.GlossResult("mystery", null)),
            setOf(PartOfSpeech.NOUN),
            EmojiPrecisionTier.STRICT
        )
        assertEquals("❓", result[0])
    }

    @Test
    fun preservesOrderAndSizeAcrossMixedResults() {
        val annotator = fakeProvider("dog" to "🐕")
        val result = annotator.annotate(
            listOf(
                GlossAnnotator.GlossResult("dog", PartOfSpeech.NOUN),
                null,
                GlossAnnotator.GlossResult("to think", PartOfSpeech.VERB)
            ),
            allPos,
            EmojiPrecisionTier.STRICT
        )
        assertEquals(listOf("🐕", null, null), result)
    }

    @Test
    fun strictTierIgnoresMediumOnlyMatch() {
        val annotator = tieredProvider(strict = emptyMap(), medium = mapOf("animal" to "🐕"))
        val result = annotator.annotate(
            listOf(GlossAnnotator.GlossResult("animal", PartOfSpeech.NOUN)),
            allPos,
            EmojiPrecisionTier.STRICT
        )
        assertNull(result[0])
    }

    @Test
    fun mediumTierFallsBackToKeywordMatchWhenStrictMisses() {
        val annotator = tieredProvider(strict = emptyMap(), medium = mapOf("animal" to "🐕"))
        val result = annotator.annotate(
            listOf(GlossAnnotator.GlossResult("animal", PartOfSpeech.NOUN)),
            allPos,
            EmojiPrecisionTier.MEDIUM
        )
        assertEquals("🐕", result[0])
    }

    @Test
    fun mediumTierPrefersStrictMatchOverKeywordMatch() {
        val annotator = tieredProvider(strict = mapOf("dog" to "🐕"), medium = mapOf("dog" to "🐶"))
        val result = annotator.annotate(
            listOf(GlossAnnotator.GlossResult("dog", PartOfSpeech.NOUN)),
            allPos,
            EmojiPrecisionTier.MEDIUM
        )
        assertEquals("🐕", result[0])
    }
}
