package com.japanglify.app.domain.polyglos

import com.japanglify.app.domain.AnnotatedSegment
import com.japanglify.app.domain.JapaneseAnalyzer
import com.japanglify.app.domain.JapanglifySettings
import com.japanglify.app.domain.TripleScriptRenderer

/**
 * Detect → analyze → render facade (Πολγζλοξ / polyglos).
 * Primary engine for 1.0; [com.japanglify.app.domain.JapanglifyEngine] remains a thin alias.
 */
class PolyglosEngine(
    private val registry: AnalyzerRegistry,
    private val renderer: TripleScriptRenderer = TripleScriptRenderer()
) {
    fun detect(text: CharSequence): ScriptProfile = ScriptDetector.detect(text)

    fun annotate(text: String, settings: JapanglifySettings): List<AnnotatedSegment> {
        if (text.isEmpty()) return emptyList()
        val out = ArrayList<AnnotatedSegment>()
        for (tok in WhitespaceTokenizer.tokenize(text)) {
            if (!tok.isWord) {
                out += AnnotatedSegment(surface = tok.surface)
                continue
            }
            val profile = ScriptDetector.detect(tok.surface)
            // Mixed-script *word* (ja/el/he letters in one token): pass through.
            // Mixed *input* (separate words) is handled by routing each token.
            if (profile.mixed) {
                out += AnnotatedSegment(surface = tok.surface)
                continue
            }
            val analyzer = registry.select(profile, settings.forceAnalyzerId)
            if (analyzer == null) {
                out += AnnotatedSegment(surface = tok.surface)
            } else {
                out += analyzer.annotate(tok.surface, settings)
            }
        }
        return out
    }

    fun expand(text: String, settings: JapanglifySettings): String {
        val segments = annotate(text, settings)
        return renderer.render(segments, settings)
    }

    fun buildInterlinearRows(
        text: String,
        settings: JapanglifySettings
    ): List<TripleScriptRenderer.InterlinearRowData> {
        val segments = annotate(text, settings)
        return renderer.buildInterlinearRows(segments, settings)
    }

    fun buildInterlinearDisplayRows(
        text: String,
        settings: JapanglifySettings
    ): List<TripleScriptRenderer.InterlinearDisplayRow> {
        val segments = annotate(text, settings)
        return renderer.buildInterlinearDisplayRows(segments, settings)
    }

    companion object {
        fun withJapanese(japanese: JapaneseAnalyzer): PolyglosEngine {
            val registry = AnalyzerRegistry(
                listOf(
                    JapaneseAnalyzerAdapter(japanese),
                    GreekAnalyzer.ancient(),
                    GreekAnalyzer.modern(),
                    HebrewAnalyzer.ancient(),
                    HebrewAnalyzer.modern()
                )
            )
            return PolyglosEngine(registry)
        }
    }
}
