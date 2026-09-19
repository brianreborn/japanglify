package com.japanglify.app.domain.polyglos

import com.japanglify.app.domain.AnnotatedSegment
import com.japanglify.app.domain.JapanglifySettings

/** Ancient (Biblical) or modern Hebrew analyzer. */
class HebrewAnalyzer(
    override val id: String,
    private val lexicon: BundledLexicon = BundledLexicon.loadHebrew()
) : Analyzer {

    override fun supports(profile: ScriptProfile): Boolean =
        profile.dominant == ScriptKind.HEBREW

    override fun annotate(text: CharSequence, settings: JapanglifySettings): List<AnnotatedSegment> {
        if (text.isEmpty()) return emptyList()
        val system = settings.hebrewRomanization.id
        return WhitespaceTokenizer.tokenize(text).map { tok ->
            if (!tok.isWord) {
                AnnotatedSegment(surface = tok.surface, script = id, phoneticSystem = system)
            } else {
                val hasNiqqud = tok.surface.any { ch ->
                    val cp = ch.code
                    cp in 0x05B0..0x05BD || cp in 0x05BF..0x05C7
                }
                val phonetic = when {
                    !settings.includeRomaji -> null
                    id == AnalyzerIds.HE_MODERN && !hasNiqqud && !settings.hebrewPhoneticUnpointed -> null
                    else -> HebrewRomanizer.romanize(tok.surface)
                }
                val gloss = if (settings.includeGlosses) lexicon.gloss(tok.surface) else null
                AnnotatedSegment(
                    surface = tok.surface,
                    romaji = phonetic,
                    gloss = gloss,
                    script = id,
                    phoneticSystem = system
                )
            }
        }
    }

    companion object {
        fun ancient(lexicon: BundledLexicon = BundledLexicon.loadHebrew()) =
            HebrewAnalyzer(AnalyzerIds.HBO, lexicon)

        fun modern(lexicon: BundledLexicon = BundledLexicon.loadHebrew()) =
            HebrewAnalyzer(AnalyzerIds.HE_MODERN, lexicon)
    }
}
