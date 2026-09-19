package com.japanglify.app.domain.polyglos

import com.japanglify.app.domain.AnnotatedSegment
import com.japanglify.app.domain.JapanglifySettings

/** Ancient (polytonic) or modern (monotonic) Greek analyzer. */
class GreekAnalyzer(
    override val id: String,
    private val defaultScheme: GreekRomanization,
    private val lexicon: BundledLexicon = BundledLexicon.loadGreek()
) : Analyzer {

    override fun supports(profile: ScriptProfile): Boolean =
        profile.dominant == ScriptKind.GREEK

    override fun annotate(text: CharSequence, settings: JapanglifySettings): List<AnnotatedSegment> {
        if (text.isEmpty()) return emptyList()
        val scheme = resolveScheme(settings)
        return WhitespaceTokenizer.tokenize(text).map { tok ->
            if (!tok.isWord) {
                AnnotatedSegment(surface = tok.surface, script = id, phoneticSystem = scheme.id)
            } else {
                val phonetic = if (settings.includeRomaji) {
                    GreekRomanizer.romanize(tok.surface, scheme)
                } else null
                val gloss = if (settings.includeGlosses) lexicon.gloss(tok.surface) else null
                AnnotatedSegment(
                    surface = tok.surface,
                    romaji = phonetic,
                    gloss = gloss,
                    script = id,
                    phoneticSystem = scheme.id
                )
            }
        }
    }

    private fun resolveScheme(settings: JapanglifySettings): GreekRomanization {
        val requested = settings.greekRomanization
        return when (id) {
            AnalyzerIds.GRC_ANCIENT -> when (requested) {
                GreekRomanization.SBL -> GreekRomanization.SBL
                else -> GreekRomanization.ERASMIAN
            }
            else -> requested
        }
    }

    companion object {
        fun ancient(lexicon: BundledLexicon = BundledLexicon.loadGreek()) =
            GreekAnalyzer(AnalyzerIds.GRC_ANCIENT, GreekRomanization.ERASMIAN, lexicon)

        fun modern(lexicon: BundledLexicon = BundledLexicon.loadGreek()) =
            GreekAnalyzer(AnalyzerIds.EL_MODERN, GreekRomanization.MODERN_PHONEMIC, lexicon)
    }
}
