package com.japanglify.app.domain.polyglos

/**
 * Selects an [Analyzer] from a [ScriptProfile] and optional force override.
 *
 * Mixed-script *words* are not selected here: [PolyglosEngine] passthroughs a
 * token when [ScriptProfile.mixed] is true, then calls [select] only for
 * monolingual tokens. Auto-detect uses registry order + [Analyzer.supports]
 * on that token's dominant script (Greek → [AnalyzerIds.GRC_ANCIENT], Hebrew →
 * [AnalyzerIds.HBO] unless [select] is given a force id).
 */
class AnalyzerRegistry(
    private val analyzers: List<Analyzer>
) {
    private val byId = analyzers.associateBy { it.id }

    fun get(id: String): Analyzer? = byId[id]

    fun select(profile: ScriptProfile, forceId: String?): Analyzer? {
        val forced = forceId?.takeIf { it.isNotBlank() && it != AnalyzerIds.AUTO }
        if (forced != null) return byId[forced]

        return analyzers.firstOrNull { it.supports(profile) }
            ?: when (profile.dominant) {
                ScriptKind.JAPANESE -> byId[AnalyzerIds.JA]
                ScriptKind.GREEK -> byId[AnalyzerIds.GRC_ANCIENT]
                ScriptKind.HEBREW -> byId[AnalyzerIds.HBO]
                else -> null
            }
    }
}
