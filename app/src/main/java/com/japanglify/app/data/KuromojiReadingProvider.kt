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
        return tokenizer.tokenize(text).map { token ->
            JapaneseAnalyzer.SurfaceReading(
                surface = token.surface,
                reading = spokenReading(token),
                // 助動詞 (auxiliary verb / conjugation ending, e.g. ました, ない)
                // completes the previous word rather than starting a new one.
                isBoundToPrevious = token.partOfSpeechLevel1 == "助動詞",
                isParticle = token.partOfSpeechLevel1 == "助詞"
            )
        }
    }

    private fun spokenReading(token: Token): String? {
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
        return token.reading?.takeIf { it.isNotBlank() && it != "*" }
    }
}
