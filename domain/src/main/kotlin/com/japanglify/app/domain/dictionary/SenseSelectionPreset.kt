package com.japanglify.app.domain.dictionary

/**
 * Named bundles of [SenseWeights] for [SenseSelector] — see its doc for the
 * scoring model. [CUSTOM] carries no weights of its own; a [CUSTOM]
 * selection reads [com.japanglify.app.domain.JapanglifySettings]'s own
 * customSense*Weight fields instead (see
 * [com.japanglify.app.domain.JapanglifySettings.effectiveSenseWeights]).
 */
enum class SenseSelectionPreset(val id: String, val displayName: String, val weights: SenseWeights?) {
    MODERN(
        id = "modern",
        displayName = "Modern (default)",
        weights = SenseWeights(richness = 1.0, position = 0.15, dated = -3.0)
    ),
    CLASSICAL(
        id = "classical",
        displayName = "Classical / dated source text",
        weights = SenseWeights(richness = 1.0, position = 0.15, dated = 1.5)
    ),
    CUSTOM(id = "custom", displayName = "Custom", weights = null);

    companion object {
        fun fromId(id: String?): SenseSelectionPreset = entries.firstOrNull { it.id == id } ?: MODERN
    }
}
