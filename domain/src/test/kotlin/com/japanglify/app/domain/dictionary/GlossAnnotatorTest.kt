package com.japanglify.app.domain.dictionary

import com.japanglify.app.domain.JapaneseAnalyzer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GlossAnnotatorTest {

    private fun fakeDictionary(vararg entries: Pair<String, DictionaryEntry>) =
        GlossAnnotator(GlossAnnotator.DictionaryProvider { key, _, _, _ -> entries.toMap()[key] })

    private fun texts(result: List<GlossAnnotator.TokenGloss>): List<String?> = result.map { it.result?.text }

    @Test
    fun formatsGlossDirectlyWithNoPartOfSpeechPrefix() {
        // No "n."/"v."/etc. abbreviation in the shown text -- direct
        // feedback that it read as unwanted metadata commentary, not help.
        // partOfSpeech is still carried on the result (see [GlossResult])
        // for logic that needs it, just never shown.
        val annotator = fakeDictionary(
            "紙" to DictionaryEntry("紙", "かみ", PartOfSpeech.NOUN, "paper")
        )
        val result = annotator.annotate(listOf(JapaneseAnalyzer.SurfaceReading("紙", "かみ", baseForm = "紙")))
        assertEquals(listOf("paper"), texts(result))
        assertEquals(PartOfSpeech.NOUN, result[0].result?.partOfSpeech)
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
        // "to " stripped -- see stripsLeadingToOnlyForVerbs below.
        assertEquals(listOf("go"), texts(result))
    }

    @Test
    fun stripsLeadingToOnlyForVerbs() {
        // JMdict's "to " infinitive-marker convention on verb glosses is a
        // citation-form artifact, not information -- but only for verbs.
        // An expression/adverb gloss that legitimately starts with "to " as
        // real content ("to a certain extent") must NOT be touched, or
        // stripping would corrupt the meaning instead of decluttering it.
        val annotator = fakeDictionary(
            "行く" to DictionaryEntry("行く", "いく", PartOfSpeech.VERB, "to go"),
            "程度" to DictionaryEntry("程度", "ていど", PartOfSpeech.EXPRESSION, "to a certain extent")
        )
        val result = annotator.annotate(
            listOf(
                JapaneseAnalyzer.SurfaceReading("行く", "いく", baseForm = "行く"),
                JapaneseAnalyzer.SurfaceReading("程度", "ていど", baseForm = "程度")
            )
        )
        assertEquals(listOf("go", "to a certain extent"), texts(result))
    }

    @Test
    fun trimsLongSynonymListsByOverallLengthNotAFixedCount() {
        // ご機嫌よう's first synonym alone ("nice to see you", 16 chars)
        // already exceeds the default 12-char budget, so only it survives --
        // matching the old interjection-only "just the first" rule for this
        // case. それでも's synonyms are individually short enough that "but/and
        // yet" (11 chars) still fits before "nevertheless" would push it over
        // -- a length budget keeps adding whole synonyms as long as they fit,
        // it doesn't hard-stop at exactly one the way the old rule did.
        val annotator = fakeDictionary(
            "ご機嫌よう" to DictionaryEntry(
                "ご機嫌よう", "ごきげんよう", PartOfSpeech.INTERJECTION,
                "nice to see you/good morning/good evening"
            ),
            "それでも" to DictionaryEntry("それでも", "それでも", PartOfSpeech.EXPRESSION, "but/and yet/nevertheless")
        )
        val result = annotator.annotate(
            listOf(
                JapaneseAnalyzer.SurfaceReading("ご機嫌よう", "ごきげんよう", baseForm = "ご機嫌よう"),
                JapaneseAnalyzer.SurfaceReading("それでも", "それでも", baseForm = "それでも")
            )
        )
        assertEquals(listOf("nice to see you", "but/and yet"), texts(result))
    }

    @Test
    fun keepsShortSynonymListsInFullRegardlessOfPartOfSpeech() {
        // さん is JMdict's real-world example of this: tagged SUFFIX (an
        // honorific title), not some special-cased category. "Mr/Mrs/Miss"
        // (11 chars) fits whole under the default 12-char budget, so nothing
        // truncates it to just "Mr" -- collapsing to one would silently drop
        // real, gender-distinguishing information. An earlier, synonym-COUNT-
        // based version of this had to hand-exempt whichever parts of speech
        // "seemed like" they'd have distinguishing synonyms; a length budget
        // protects any short-enough list automatically, regardless of category.
        val annotator = fakeDictionary(
            "さん" to DictionaryEntry("さん", "さん", PartOfSpeech.SUFFIX, "Mr/Mrs/Miss")
        )
        val result = annotator.annotate(listOf(JapaneseAnalyzer.SurfaceReading("さん", "さん", baseForm = "さん")))
        assertEquals(listOf("Mr/Mrs/Miss"), texts(result))
    }

    @Test
    fun trimsLongSynonymListsForAnyPartOfSpeechNotJustInterjections() {
        // The old rule only trimmed interjection/expression glosses; a verb
        // with an equally long near-duplicate synonym chain kept its full
        // set. Found live: exactly this kind of untrimmed VERB/NOUN/ADJECTIVE
        // gloss was what stretched a word's own interlinear column wide
        // enough to visibly balloon the gap to the next word.
        val annotator = fakeDictionary(
            "する" to DictionaryEntry("する", "する", PartOfSpeech.VERB, "to do/to carry out/to perform")
        )
        val result = annotator.annotate(listOf(JapaneseAnalyzer.SurfaceReading("する", "する", baseForm = "する")))
        assertEquals(listOf("do"), texts(result))
    }

    @Test
    fun maxGlossLengthIsConfigurable() {
        val annotator = fakeDictionary(
            "する" to DictionaryEntry("する", "する", PartOfSpeech.VERB, "to do/to carry out/to perform")
        )
        val result = annotator.annotate(
            listOf(JapaneseAnalyzer.SurfaceReading("する", "する", baseForm = "する")),
            maxGlossLength = 100
        )
        assertEquals(listOf("do/to carry out/to perform"), texts(result))
    }

    @Test
    fun fallsBackToSurfaceWhenNoBaseForm() {
        val annotator = fakeDictionary(
            "大きい" to DictionaryEntry("大きい", "おおきい", PartOfSpeech.ADJECTIVE, "big; large")
        )
        val result = annotator.annotate(listOf(JapaneseAnalyzer.SurfaceReading("大きい", "おおきい", baseForm = null)))
        assertEquals(listOf("big; large"), texts(result))
    }

    @Test
    fun omitsGlossForEveryParticleRegardlessOfMeaning() {
        // Found live via real device UAT: の's actual JMdict gloss reads as
        // an entire sentence crammed into one word's slot. That mismatch
        // (a dictionary-entry-length gloss squeezed under a single
        // character) isn't unique to abstract grammatical-role markers like
        // は/が/を -- it affects particles with real semantic content too
        // (へ, と, ...), so the whole category is omitted rather than
        // hand-curating which particles' glosses happen to be short enough.
        val annotator = fakeDictionary(
            "は" to DictionaryEntry("は", null, PartOfSpeech.PARTICLE, "topic marker"),
            "が" to DictionaryEntry("が", null, PartOfSpeech.PARTICLE, "subject marker"),
            "を" to DictionaryEntry("を", null, PartOfSpeech.PARTICLE, "object marker"),
            "の" to DictionaryEntry("の", null, PartOfSpeech.PARTICLE, "possessive / nominalizing particle"),
            "へ" to DictionaryEntry("へ", null, PartOfSpeech.PARTICLE, "to; toward")
        )
        val result = annotator.annotate(
            listOf(
                JapaneseAnalyzer.SurfaceReading("は", "ワ", baseForm = "は"),
                JapaneseAnalyzer.SurfaceReading("が", "ガ", baseForm = "が"),
                JapaneseAnalyzer.SurfaceReading("を", "オ", baseForm = "を"),
                JapaneseAnalyzer.SurfaceReading("の", "ノ", baseForm = "の"),
                JapaneseAnalyzer.SurfaceReading("へ", "エ", baseForm = "へ")
            )
        )
        assertEquals(listOf(null, null, null, null, null), texts(result))
    }

    @Test
    fun omittedParticleStillCarriesNoGlossResultAtAllNotJustHiddenText() {
        // format() returning null makes annotate() skip GlossResult entirely
        // for that token (see annotate()'s use of ?.let) -- confirms a
        // particle doesn't just hide its *text* while still producing a
        // GlossResult an EmojiAnnotator downstream could match against.
        val annotator = fakeDictionary(
            "の" to DictionaryEntry("の", null, PartOfSpeech.PARTICLE, "possessive / nominalizing particle")
        )
        val result = annotator.annotate(listOf(JapaneseAnalyzer.SurfaceReading("の", "ノ", baseForm = "の")))
        assertNull(result[0].result)
    }

    @Test
    fun omitsGlossForBoundToPreviousTokensEvenWithARealDictionaryEntry() {
        // Found live via real device UAT: だ (copula, bound to the previous
        // word) isn't tagged PARTICLE by JMdict (copula is its own "cop"
        // code, which format()'s PARTICLE-only check never catches), so it
        // kept its real dictionary gloss and rendered as a stray word jammed
        // with zero gap against the previous word's gloss (bound-to-previous
        // cells get no word-gap) -- e.g. "wonderfuldui" for 不思議な. The
        // dictionary entry here deliberately has a real, non-particle
        // gloss/POS to prove omission comes from isBoundToPrevious itself,
        // not from re-deriving it out of the dictionary lookup.
        val annotator = fakeDictionary(
            "だ" to DictionaryEntry("だ", null, PartOfSpeech.OTHER, "dui")
        )
        val result = annotator.annotate(
            listOf(JapaneseAnalyzer.SurfaceReading("だ", "ダ", isBoundToPrevious = true, baseForm = "だ"))
        )
        assertNull(result[0].result)
    }

    @Test
    fun omitsGlossForContextualParticleEvenWhenDictionaryEntryDisagrees() {
        // Kuromoji's own isParticle tag reflects this exact token in this
        // exact sentence; a dictionary lookup is keyed on baseForm/surface
        // alone and can land on the wrong same-spelling headword. This
        // entry deliberately claims a non-particle POS to prove the
        // contextual tag wins rather than being silently overridden by a
        // mismatched dictionary classification.
        val annotator = fakeDictionary(
            "な" to DictionaryEntry("な", null, PartOfSpeech.OTHER, "not a real particle gloss")
        )
        val result = annotator.annotate(
            listOf(JapaneseAnalyzer.SurfaceReading("な", "ナ", isParticle = true, baseForm = "な"))
        )
        assertNull(result[0].result)
    }

    @Test
    fun missingEntryYieldsNullNotCrash() {
        val annotator = fakeDictionary()
        val result = annotator.annotate(listOf(JapaneseAnalyzer.SurfaceReading("謎語", null, baseForm = "謎語")))
        assertEquals(1, result.size)
        assertNull(result[0].result)
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
        assertEquals(listOf("Japanese", null, "do"), texts(result))
    }

    @Test
    fun longestMatchPhraseWinsOverPerTokenGlosses() {
        // ご機嫌よう is one JMdict entry ("nice to see you / good morning /
        // good evening"), but Kuromoji splits it into ご + 機嫌 + よう. The
        // whole-phrase entry must win, its gloss riding on the span's first
        // token; the consumed pieces stay null and 機嫌's own "mood" gloss is
        // never used. Only the first synonym survives format() here since it
        // alone already exceeds the default length budget -- see
        // trimsLongSynonymListsByOverallLengthNotAFixedCount.
        val annotator = fakeDictionary(
            "ご機嫌よう" to DictionaryEntry(
                "ご機嫌よう", "ごきげんよう", PartOfSpeech.INTERJECTION,
                "nice to see you/good morning/good evening"
            ),
            "機嫌" to DictionaryEntry("機嫌", "きげん", PartOfSpeech.NOUN, "mood")
        )
        val tokens = listOf(
            JapaneseAnalyzer.SurfaceReading("ご", "ゴ", baseForm = "ご"),
            JapaneseAnalyzer.SurfaceReading("機嫌", "キゲン", baseForm = "機嫌"),
            JapaneseAnalyzer.SurfaceReading("よう", "ヨウ", baseForm = "よう")
        )
        val result = annotator.annotate(tokens)
        assertEquals(listOf("nice to see you", null, null), texts(result))
        // The phrase's 2nd/3rd tokens are marked as continuations of the
        // first (see TokenGloss's doc) so the renderer never wraps a line
        // between them — found live: ご機嫌よう wrapped between ご and 機嫌.
        assertFalse(result[0].isPhraseContinuation)
        assertTrue(result[1].isPhraseContinuation)
        assertTrue(result[2].isPhraseContinuation)
    }

    @Test
    fun phraseMatchIgnoresCoincidentalNonExpressionConcatenations() {
        // いい ("good") + ん concatenates to いいん, which is *also* the plain
        // noun 医院 ("doctor's office"). A greedy longest-match must NOT let
        // that ordinary noun hijack the span — only exp/int entries count as
        // set phrases — so いい keeps its own per-token "good".
        val annotator = fakeDictionary(
            "いいん" to DictionaryEntry("いいん", "いいん", PartOfSpeech.NOUN, "doctor's office"),
            "いい" to DictionaryEntry("いい", "いい", PartOfSpeech.ADJECTIVE, "good")
        )
        val tokens = listOf(
            JapaneseAnalyzer.SurfaceReading("いい", "イイ", baseForm = "いい"),
            JapaneseAnalyzer.SurfaceReading("ん", "ン", baseForm = "ん")
        )
        assertEquals(listOf("good", null), texts(annotator.annotate(tokens)))
    }

    @Test
    fun forwardsTokenReadingToLookupForSameSpellingDisambiguation() {
        // 僕 read ぼく is "I, me"; read しもべ is "servant". The annotator must
        // pass the token's reading through so a reading-aware provider picks
        // the right sense instead of pooling both by headword.
        val provider = GlossAnnotator.DictionaryProvider { key, reading, _, _ ->
            if (key != "僕") null
            else if (reading == "ボク" || reading == "ぼく")
                DictionaryEntry("僕", "ぼく", PartOfSpeech.NOUN, "I/me")
            else DictionaryEntry("僕", "しもべ", PartOfSpeech.NOUN, "servant")
        }
        val result = GlossAnnotator(provider)
            .annotate(listOf(JapaneseAnalyzer.SurfaceReading("僕", "ボク", baseForm = "僕")))
        assertEquals(listOf("I/me"), texts(result))
    }

    @Test
    fun forwardsVerbPosHintToLookupForSameReadingDifferentWordDisambiguation() {
        // する ("to do") and 擦る ("to rub") are both spelled/read する in
        // kana -- a reading match alone can't tell them apart, unlike 僕
        // above. Kuromoji's own conjugation class for this specific token
        // (サ変・スル vs. 五段・ラ行, mapped by jmdictVerbConjugationPrefix to
        // "vs" vs. "v5r") is the signal that does. Found live: する rendered
        // as "to rub" because 擦る's entry listed more English synonyms.
        val provider = GlossAnnotator.DictionaryProvider { key, _, verbPosHint, _ ->
            if (key != "する") null
            else if (verbPosHint == "vs") DictionaryEntry("する", "する", PartOfSpeech.VERB, "to do")
            else DictionaryEntry("する", "する", PartOfSpeech.VERB, "to rub")
        }
        val result = GlossAnnotator(provider).annotate(
            listOf(JapaneseAnalyzer.SurfaceReading("する", "する", baseForm = "する", verbPosHint = "vs"))
        )
        // "to " stripped -- see stripsLeadingToOnlyForVerbs above.
        assertEquals(listOf("do"), texts(result))
    }

    @Test
    fun noPartOfSpeechStillFormatsGloss() {
        val annotator = fakeDictionary(
            "謎" to DictionaryEntry("謎", "なぞ", null, "mystery")
        )
        val result = annotator.annotate(listOf(JapaneseAnalyzer.SurfaceReading("謎", "なぞ", baseForm = "謎")))
        assertEquals(listOf("mystery"), texts(result))
        assertNull(result[0].result?.partOfSpeech)
    }
}
