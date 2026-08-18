package com.japanglify.app.domain

/**
 * Marks an interlinear row where a line was intentionally elided rather than
 * shown redundant or left silently blank — see [TripleScriptRenderer]'s
 * `buildDisplayLines` doc for the two cases this covers. Deliberately not a
 * checkmark/emoji: those can plausibly appear in real input text (someone
 * pastes a ✓ or 🗸), which would be indistinguishable from our own marker.
 * A ditto-family mark reads as punctuation, not content, and both options
 * below carry the same real-world meaning ("same as above") in their
 * respective traditions, which is exactly what's being signaled here.
 */
enum class ElisionMarker(val id: String, val displayName: String, val symbol: String?) {
    /** Japanese ditto mark (〃), used in vertical lists for "same as above." */
    DITTO(id = "ditto", displayName = "Ditto (〃)", symbol = "〃"),

    /** Western ditto mark, the same convention via inch/prime-style quotes. */
    DITTO_QUOTES(id = "ditto_quotes", displayName = "Ditto (\")", symbol = "\""),

    /**
     * A user-provided single character (see
     * [JapanglifySettings.customLineElisionMarker] /
     * [JapanglifySettings.effectiveLineElisionSymbol]). [symbol] is null here
     * because the actual glyph lives on the settings object, not the enum —
     * mirrors how [com.japanglify.app.domain.dictionary.SenseSelectionPreset.CUSTOM]
     * carries no fixed weights and defers to the settings' custom fields. The
     * class doc's caution applies doubly here: a custom glyph that can also
     * appear in real input text (a ✓, an emoji) becomes indistinguishable
     * from our own marker — that trade-off is the user's to make once they
     * pick this option.
     */
    CUSTOM(id = "custom", displayName = "Custom…", symbol = null),

    /** No marker — the row is just silently shorter (pre-existing behavior). */
    NONE(id = "none", displayName = "None (omit marker)", symbol = null);

    companion object {
        fun fromId(id: String?): ElisionMarker = entries.firstOrNull { it.id == id } ?: DITTO
    }
}
