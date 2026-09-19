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
        val profile = ScriptDetector.detect(text)
        val analyzer = registry.select(profile, settings.forceAnalyzerId)
            ?: return listOf(AnnotatedSegment(surface = text))
        return analyzer.annotate(text, settings)
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
