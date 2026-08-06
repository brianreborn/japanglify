package com.japanglify.app.domain

/**
 * Facade used by the process-text activity: annotate + render in one call.
 * Pure domain — no Android types — so it is unit-testable on the JVM.
 */
class JapanglifyEngine(
    private val analyzer: JapaneseAnalyzer,
    private val renderer: TripleScriptRenderer = TripleScriptRenderer()
) {
    fun expand(text: String, settings: JapanglifySettings): String {
        val segments = analyzer.annotate(text, settings)
        return renderer.render(segments, settings)
    }

    /** Structured interlinear cells for callers laying out pixels themselves (e.g. an image). */
    fun buildInterlinearRows(
        text: String,
        settings: JapanglifySettings
    ): List<TripleScriptRenderer.InterlinearRowData> {
        val segments = analyzer.annotate(text, settings)
        return renderer.buildInterlinearRows(segments, settings)
    }
}
