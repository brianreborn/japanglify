package com.japanglify.app.domain

/**
 * Text flow for rendered output.
 *
 * [VERTICAL] is reserved for traditional tategaki (縦書き). The pipeline
 * already threads orientation through the renderer so a vertical layout
 * can be enabled without redesigning tokenization or romanization.
 * Current vertical output uses a plain-text approximation (right-side
 * annotation markers); a full vertical UI would layer CSS
 * `writing-mode: vertical-rl` or a custom canvas view on top.
 */
enum class WritingOrientation(val id: String, val displayName: String) {
    HORIZONTAL(
        id = "horizontal",
        displayName = "Horizontal (横書き)"
    ),
    VERTICAL(
        id = "vertical",
        displayName = "Vertical (縦書き) — experimental"
    );

    companion object {
        fun fromId(id: String?): WritingOrientation =
            entries.firstOrNull { it.id == id } ?: HORIZONTAL
    }
}
