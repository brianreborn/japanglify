package com.japanglify.app.data

import com.atilika.kuromoji.ipadic.Token
import com.atilika.kuromoji.ipadic.Tokenizer
import com.japanglify.app.domain.JapaneseAnalyzer

/**
 * Kuromoji/IPADIC-backed [JapaneseAnalyzer.ReadingProvider].
 * Tokenizer construction loads the dictionary once; keep a single instance
 * per process (see [JapanglifyApp]).
 *
 * Particle spellings that differ from surface kana in speech
 * (は→わ, へ→え, を→お) are normalized here so romaji matches speech.
 */
class KuromojiReadingProvider(
    private val tokenizer: Tokenizer = Tokenizer.Builder().build()
) : JapaneseAnalyzer.ReadingProvider {

    override fun tokenize(text: String): List<JapaneseAnalyzer.SurfaceReading> {
        if (text.isEmpty()) return emptyList()
        return mergeConsecutiveNumbers(tokenizer.tokenize(text)).map { token ->
            JapaneseAnalyzer.SurfaceReading(
                surface = token.surface,
                reading = spokenReading(token),
                // 助動詞 (auxiliary verb / conjugation ending, e.g. ました, ない)
                // completes the previous word rather than starting a new one.
                isBoundToPrevious = token.partOfSpeechLevel1 == "助動詞",
                isParticle = token.partOfSpeechLevel1 == "助詞",
                baseForm = token.baseForm
            )
        }
    }

    /**
     * IPADIC tokenizes multi-digit numbers one digit per token (名詞,数) —
     * "２５" comes back as "２" and "５" separately, with no reading on
     * either. Left alone this fragments a single number into multiple
     * interlinear cells (each getting its own word-gap/no-gap treatment
     * inconsistently) instead of the one column a number should occupy.
     * Kuromoji's [Token] has no public constructor, so we merge via a small
     * shim rather than building a synthetic Token.
     */
    private data class MergedToken(
        val surface: String,
        val rawReading: String?,
        val partOfSpeechLevel1: String,
        val baseForm: String?
    )

    private fun mergeConsecutiveNumbers(tokens: List<Token>): List<MergedToken> {
        val out = ArrayList<MergedToken>()
        var i = 0
        while (i < tokens.size) {
            val t = tokens[i]
            if (t.partOfSpeechLevel2 == "数") {
                val surface = StringBuilder(t.surface)
                var j = i + 1
                while (j < tokens.size && tokens[j].partOfSpeechLevel2 == "数") {
                    surface.append(tokens[j].surface)
                    j++
                }
                // A merged number has no single dictionary reading or base
                // form of its own.
                out += MergedToken(
                    surface = surface.toString(),
                    rawReading = null,
                    partOfSpeechLevel1 = t.partOfSpeechLevel1,
                    baseForm = null
                )
                i = j
            } else {
                out += MergedToken(t.surface, t.reading, t.partOfSpeechLevel1, t.baseForm)
                i++
            }
        }
        return out
    }

    private fun spokenReading(token: MergedToken): String? {
        val surface = token.surface
        val pos1 = token.partOfSpeechLevel1
        // 助詞 with historical kana spellings spoken differently
        if (pos1 == "助詞") {
            when (surface) {
                "は" -> return "ワ"
                "へ" -> return "エ"
                "を" -> return "オ"
            }
        }
        return token.rawReading?.takeIf { it.isNotBlank() && it != "*" }
    }
}
