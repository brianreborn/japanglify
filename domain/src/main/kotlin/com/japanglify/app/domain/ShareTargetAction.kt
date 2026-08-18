package com.japanglify.app.domain

/**
 * What a [ShareTarget] actually produces on the clipboard when shared text
 * arrives — mirrors the two result forms the Copy-hook notification already
 * offers ("Copy" / "Copy image"), just chosen once up front per target
 * instead of picked per-notification.
 */
enum class ShareTargetAction(val id: String, val displayName: String) {
    /** Puts the Japanglified plain text on the clipboard — today's default behavior. */
    COPY_TEXT(id = "copy_text", displayName = "Copy text"),

    /**
     * Renders the result to a PNG (same renderer the Copy-hook notification's
     * "Copy image" action uses) and puts *that* on the clipboard as a
     * content:// URI — for pasting straight into a chat that renders images
     * better than plain-text alignment, skipping the extra "Copy image" tap.
     */
    COPY_IMAGE(id = "copy_image", displayName = "Copy image");

    companion object {
        fun fromId(id: String?): ShareTargetAction = entries.firstOrNull { it.id == id } ?: COPY_TEXT
    }
}
