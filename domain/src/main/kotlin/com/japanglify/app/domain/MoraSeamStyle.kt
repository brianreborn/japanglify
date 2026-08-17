package com.japanglify.app.domain

/**
 * How the interlinear romaji row separates a word from a directly-abutting
 * bound segment (a copula/auxiliary like です/だ/ました that renders flush to
 * the previous word, with no word gap) — see [TripleScriptRenderer]'s
 * `buildRawTripleLines` and the `MORA_SEAM` doc for why the seam exists at all
 * (to stop two independently mora-separated romaji runs from fusing into a
 * bogus mora, e.g. すごい + です → "su·go·ide·su").
 *
 * Both markers are exactly one display cell wide, so switching between them
 * never disturbs column alignment (the width reserved for the seam is the same
 * either way — see `MORA_SEAM_WIDTH`).
 */
enum class MoraSeamStyle(val id: String, val displayName: String, val marker: String) {
    /** Middle dot (·) — reads as a mora boundary: すごい + です → "su·go·i·de·su". */
    DOT(id = "dot", displayName = "Middle dot (·)", marker = "·"),

    /** Space — reads as a word break instead: 服 + だ → "fu·ku da". */
    SPACE(id = "space", displayName = "Space", marker = " ");

    companion object {
        fun fromId(id: String?): MoraSeamStyle = entries.firstOrNull { it.id == id } ?: DOT
    }
}
