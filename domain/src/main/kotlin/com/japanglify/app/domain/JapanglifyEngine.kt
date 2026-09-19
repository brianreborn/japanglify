package com.japanglify.app.domain

import com.japanglify.app.domain.polyglos.AnalyzerRegistry
import com.japanglify.app.domain.polyglos.GreekAnalyzer
import com.japanglify.app.domain.polyglos.HebrewAnalyzer
import com.japanglify.app.domain.polyglos.JapaneseAnalyzerAdapter
import com.japanglify.app.domain.polyglos.PolyglosEngine

/**
 * Facade used by the process-text activity: annotate + render in one call.
 * Pure domain — no Android types — so it is unit-testable on the JVM.
 *
 * 1.0: thin wrapper around [PolyglosEngine] (Πολγζλοξ / polyglos) so existing
 * call sites keep compiling while script detection + Greek/Hebrew analyzers land.
 */
class JapanglifyEngine(
    analyzer: JapaneseAnalyzer,
    renderer: TripleScriptRenderer = TripleScriptRenderer()
) {
    private val polyglos = PolyglosEngine(
        registry = AnalyzerRegistry(
            listOf(
                JapaneseAnalyzerAdapter(analyzer),
                GreekAnalyzer.ancient(),
                GreekAnalyzer.modern(),
                HebrewAnalyzer.ancient(),
                HebrewAnalyzer.modern()
            )
        ),
        renderer = renderer
    )

    fun expand(text: String, settings: JapanglifySettings): String =
        polyglos.expand(text, settings)

    fun buildInterlinearRows(
        text: String,
        settings: JapanglifySettings
    ): List<TripleScriptRenderer.InterlinearRowData> =
        polyglos.buildInterlinearRows(text, settings)

    fun buildInterlinearDisplayRows(
        text: String,
        settings: JapanglifySettings
    ): List<TripleScriptRenderer.InterlinearDisplayRow> =
        polyglos.buildInterlinearDisplayRows(text, settings)
}
