package com.japanglify.app.domain.dictionary

import com.japanglify.app.domain.JapaneseAnalyzer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GlossAnnotatorTest {

    private fun fakeDictionary(vararg entries: Pair<String, DictionaryEntry>) =
        GlossAnnotator(GlossAnnotator.DictionaryProvider { key -> entries.toMap()[key] })

    private fun texts(result: List<GlossAnnotator.GlossResult?>): List<String?> = result.map { it?.text }

    @Test
    fun formatsPartOfSpeechAndGloss() {
        val annotator = fakeDictionary(
            "紙" to DictionaryEntry("紙", "かみ", PartOfSpeech.NOUN, "paper")
        )
        val result = annotator.annotate(listOf(JapaneseAnalyzer.SurfaceReading("紙", "かみ", baseForm = "紙")))
        assertEquals(listOf("n. paper"), texts(result))
        assertEquals(PartOfSpeech.NOUN, result[0]?.partOfSpeech)
    }

    @Test
    fun looksUpByBaseFormNotSurface() {
        // 行きました's base form is 行く — the surface itself would never
        // match a dictionary headword for a conjugated token.
        val annotator = fakeDictionary(
            "行く" to DictionaryEntry("行く", "いく", PartOfSpeech.VERB, "to go")
        )
        val result = annotator.annotate(
            listOf(JapaneseAnalyzer.SurfaceReading("行きました", "いきました", baseForm = "行く"))
        )
        assertEquals(listOf("v. to go"), texts(result))
    }

    @Test
    fun fallsBackToSurfaceWhenNoBaseForm() {
        val annotator = fakeDictionary(
            "へ" to DictionaryEntry("へ", null, PartOfSpeech.PARTICLE, "to; toward")
        )
        val result = annotator.annotate(listOf(JapaneseAnalyzer.SurfaceReading("へ", "エ", baseForm = null)))
        assertEquals(listOf("part. to; toward"), texts(result))
    }

    @Test
    fun omitsGlossForAbstractGrammaticalParticles() {
        // は/が/を mark grammatical role only -- no independent lexical
        // meaning to usefully gloss in one line, unlike へ above.
        val annotator = fakeDictionary(
            "は" to DictionaryEntry("は", null, PartOfSpeech.PARTICLE, "topic marker"),
            "が" to DictionaryEntry("が", null, PartOfSpeech.PARTICLE, "subject marker"),
            "を" to DictionaryEntry("を", null, PartOfSpeech.PARTICLE, "object marker")
        )
        val result = annotator.annotate(
            listOf(
                JapaneseAnalyzer.SurfaceReading("は", "ワ", baseForm = "は"),
                JapaneseAnalyzer.SurfaceReading("が", "ガ", baseForm = "が"),
                JapaneseAnalyzer.SurfaceReading("を", "オ", baseForm = "を")
            )
        )
        assertEquals(listOf(null, null, null), texts(result))
    }

    @Test
    fun missingEntryYieldsNullNotCrash() {
        val annotator = fakeDictionary()
        val result = annotator.annotate(listOf(JapaneseAnalyzer.SurfaceReading("謎語", null, baseForm = "謎語")))
        assertEquals(1, result.size)
        assertNull(result[0])
    }

    @Test
    fun preservesOrderAndSizeAcrossMixedHits() {
        val annotator = fakeDictionary(
            "日本語" to DictionaryEntry("日本語", "にほんご", PartOfSpeech.NOUN, "Japanese"),
            "する" to DictionaryEntry("する", "する", PartOfSpeech.VERB, "to do")
        )
        val tokens = listOf(
            JapaneseAnalyzer.SurfaceReading("日本語", "にほんご", baseForm = "日本語"),
            JapaneseAnalyzer.SurfaceReading("を", "オ", isParticle = true, baseForm = "を"),
            JapaneseAnalyzer.SurfaceReading("する", "する", baseForm = "する")
        )
        val result = annotator.annotate(tokens)
        assertEquals(listOf("n. Japanese", null, "v. to do"), texts(result))
    }

    @Test
    fun noPartOfSpeechOmitsAbbreviationPrefix() {
        val annotator = fakeDictionary(
            "謎" to DictionaryEntry("謎", "なぞ", null, "mystery")
        )
        val result = annotator.annotate(listOf(JapaneseAnalyzer.SurfaceReading("謎", "なぞ", baseForm = "謎")))
        assertEquals(listOf("mystery"), texts(result))
        assertNull(result[0]?.partOfSpeech)
    }
}
