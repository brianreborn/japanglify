package com.japanglify.app.domain

/**
 * A user-named, saved snapshot of [JapanglifySettings], surfaced as its own
 * entry in the system Share sheet (via a dynamic shortcut — see
 * `ShareTargetShortcuts` in the app module) alongside the default
 * "Japanglify" Share entry. Sharing text to a specific target's shortcut
 * converts it with *that* snapshot instead of the user's live global
 * settings — e.g. a "Romaji only" target for pasting into a chat that can't
 * render furigana well, without having to flip settings back and forth.
 *
 * [id] is a stable identifier independent of [label] (which the user can
 * rename freely) — used as the dynamic shortcut's own id and as the extra
 * [com.japanglify.app.ShareTargetActivity] reads to look the target back up,
 * so renaming or reordering targets never orphans an already-pinned/cached
 * shortcut.
 *
 * [action] (see [ShareTargetAction]) is deliberately a sibling of
 * [settings], not folded into it: it decides *what the target produces on
 * the clipboard*, a share-specific concern with no equivalent in the global
 * Settings screen, whereas [settings] decides *how the text itself gets
 * annotated*, the same concern the global screen already governs.
 */
data class ShareTarget(
    val id: String,
    val label: String,
    val settings: JapanglifySettings,
    val action: ShareTargetAction = ShareTargetAction.COPY_TEXT
)
