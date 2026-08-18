package com.japanglify.app.domain

/**
 * A drawn line's semantic color ROLE in the interlinear image output — the
 * unit a color scheme assigns a color to, rather than the raw "ink vs
 * background" pair. Lets a scheme tint the reading, romanization, and gloss
 * distinctly from the base text (and each other) while a single
 * contrast-guarantee pass keeps every one of them legible against the
 * chosen [BACKGROUND] (see [ImageColorScheme.withGuaranteedContrast]).
 */
enum class ImageColorRole { BACKGROUND, BASE, FURIGANA, ROMAJI, GLOSS }

/**
 * A named mapping of [ImageColorRole] → ARGB color (0xAARRGGBB) for the
 * rasterized "Copy image" output. Ships a few presets; the app-side renderer
 * (`ClipboardImageRenderer`) reads the resolved scheme and paints each line's
 * role. Colors are plain `Int`s (no `android.graphics.Color`) so the whole
 * model + the contrast math stay in the pure-JVM domain module and are
 * unit-testable without a device.
 *
 * The "smart" part is [withGuaranteedContrast]: whatever colors a scheme (or
 * a future custom one) declares, each text role is nudged toward black/white
 * until it meets a minimum WCAG contrast ratio against the background, so a
 * scheme can never render illegible text — you pick a look, legibility is
 * enforced for you.
 */
data class ImageColorScheme(
    val id: String,
    val displayName: String,
    val colors: Map<ImageColorRole, Int>
) {
    /** ARGB for [role], falling back to BASE then opaque black if a scheme omits it. */
    fun color(role: ImageColorRole): Int =
        colors[role] ?: colors[ImageColorRole.BASE] ?: OPAQUE_BLACK

    /**
     * A copy of this scheme where every TEXT role (all but [BACKGROUND]) is
     * adjusted, if needed, to meet [minContrastRatio] against the background
     * — see [ColorContrast.ensureContrast]. Idempotent for a scheme that
     * already passes.
     */
    fun withGuaranteedContrast(minContrastRatio: Double = MIN_TEXT_CONTRAST): ImageColorScheme {
        val bg = color(ImageColorRole.BACKGROUND)
        val adjusted = colors.mapValues { (role, c) ->
            if (role == ImageColorRole.BACKGROUND) c
            else ColorContrast.ensureContrast(c, bg, minContrastRatio)
        }
        return copy(colors = adjusted)
    }

    companion object {
        const val OPAQUE_BLACK = 0xFF000000.toInt()
        const val OPAQUE_WHITE = 0xFFFFFFFF.toInt()

        /** WCAG AA for normal-size text. Furigana is small, so this is the floor for all. */
        const val MIN_TEXT_CONTRAST = 4.5

        val STANDARD = ImageColorScheme(
            id = "standard",
            displayName = "Standard (dark on light)",
            colors = mapOf(
                ImageColorRole.BACKGROUND to OPAQUE_WHITE,
                ImageColorRole.BASE to OPAQUE_BLACK,
                ImageColorRole.FURIGANA to 0xFF555555.toInt(),
                ImageColorRole.ROMAJI to 0xFF555555.toInt(),
                ImageColorRole.GLOSS to 0xFF1E6B34.toInt()
            )
        )

        val REVERSE = ImageColorScheme(
            id = "reverse",
            displayName = "Reverse (light on dark)",
            colors = mapOf(
                ImageColorRole.BACKGROUND to 0xFF111111.toInt(),
                ImageColorRole.BASE to OPAQUE_WHITE,
                ImageColorRole.FURIGANA to 0xFFBBBBBB.toInt(),
                ImageColorRole.ROMAJI to 0xFFBBBBBB.toInt(),
                ImageColorRole.GLOSS to 0xFF7FC99A.toInt()
            )
        )

        val SEPIA = ImageColorScheme(
            id = "sepia",
            displayName = "Sepia",
            colors = mapOf(
                ImageColorRole.BACKGROUND to 0xFFF4ECD8.toInt(),
                ImageColorRole.BASE to 0xFF3B2F2F.toInt(),
                ImageColorRole.FURIGANA to 0xFF6B5A45.toInt(),
                ImageColorRole.ROMAJI to 0xFF6B5A45.toInt(),
                ImageColorRole.GLOSS to 0xFF7A5230.toInt()
            )
        )

        val HIGH_CONTRAST = ImageColorScheme(
            id = "high_contrast",
            displayName = "High contrast",
            colors = mapOf(
                ImageColorRole.BACKGROUND to OPAQUE_WHITE,
                ImageColorRole.BASE to OPAQUE_BLACK,
                ImageColorRole.FURIGANA to OPAQUE_BLACK,
                ImageColorRole.ROMAJI to OPAQUE_BLACK,
                ImageColorRole.GLOSS to OPAQUE_BLACK
            )
        )

        val PRESETS = listOf(STANDARD, REVERSE, SEPIA, HIGH_CONTRAST)

        val DEFAULT = STANDARD

        /**
         * The scheme id meaning "use the user's own per-role custom colors"
         * (stored on [JapanglifySettings], resolved by its
         * [JapanglifySettings.effectiveImageColorScheme]) rather than a
         * preset — mirrors [ElisionMarker.CUSTOM] / [dictionary.SenseSelectionPreset.CUSTOM].
         * Not in [PRESETS]; [fromId] still falls back to [DEFAULT] for it,
         * since building a custom scheme needs the settings' color fields
         * that this pure companion doesn't have.
         */
        const val CUSTOM_ID = "custom"

        /** Options for the scheme picker: the concrete presets plus a "Custom…" entry. */
        val PICKER_OPTIONS: List<Pair<String, String>> =
            PRESETS.map { it.id to it.displayName } + (CUSTOM_ID to "Custom…")

        fun fromId(id: String?): ImageColorScheme =
            PRESETS.firstOrNull { it.id == id } ?: DEFAULT

        /** Builds a [CUSTOM_ID] scheme from explicit per-role ARGB colors. */
        fun custom(background: Int, base: Int, furigana: Int, romaji: Int, gloss: Int): ImageColorScheme =
            ImageColorScheme(
                id = CUSTOM_ID,
                displayName = "Custom",
                colors = mapOf(
                    ImageColorRole.BACKGROUND to background,
                    ImageColorRole.BASE to base,
                    ImageColorRole.FURIGANA to furigana,
                    ImageColorRole.ROMAJI to romaji,
                    ImageColorRole.GLOSS to gloss
                )
            )
    }
}

/**
 * WCAG relative-luminance + contrast-ratio math, plus a [ensureContrast]
 * helper that nudges a foreground color until it is legible against a
 * background. Pure integer/float math on 0xAARRGGBB colors — no Android.
 */
object ColorContrast {

    private fun channelLinear(c8: Int): Double {
        val s = c8 / 255.0
        return if (s <= 0.03928) s / 12.92 else Math.pow((s + 0.055) / 1.055, 2.4)
    }

    /** WCAG relative luminance of an ARGB color (alpha ignored). */
    fun relativeLuminance(argb: Int): Double {
        val r = (argb ushr 16) and 0xFF
        val g = (argb ushr 8) and 0xFF
        val b = argb and 0xFF
        return 0.2126 * channelLinear(r) + 0.7152 * channelLinear(g) + 0.0722 * channelLinear(b)
    }

    /** WCAG contrast ratio in [1.0, 21.0] between two ARGB colors. */
    fun contrastRatio(a: Int, b: Int): Double {
        val la = relativeLuminance(a)
        val lb = relativeLuminance(b)
        val hi = maxOf(la, lb)
        val lo = minOf(la, lb)
        return (hi + 0.05) / (lo + 0.05)
    }

    private fun lerpChannel(from: Int, to: Int, t: Double): Int =
        (from + (to - from) * t).toInt().coerceIn(0, 255)

    private fun lerp(from: Int, to: Int, t: Double): Int {
        val a = (from ushr 24) and 0xFF // preserve foreground alpha
        val r = lerpChannel((from ushr 16) and 0xFF, (to ushr 16) and 0xFF, t)
        val g = lerpChannel((from ushr 8) and 0xFF, (to ushr 8) and 0xFF, t)
        val b = lerpChannel(from and 0xFF, to and 0xFF, t)
        return (a shl 24) or (r shl 16) or (g shl 8) or b
    }

    /**
     * Returns [fg] unchanged if it already meets [minRatio] contrast against
     * [bg]; otherwise blends it toward whichever of black/white yields more
     * contrast with [bg] (a monotonic increase, so binary-searched) until it
     * just meets the ratio. If even the pure extreme can't reach [minRatio]
     * (a near-mid-gray background), returns that best-effort extreme rather
     * than failing.
     */
    fun ensureContrast(fg: Int, bg: Int, minRatio: Double): Int {
        if (contrastRatio(fg, bg) >= minRatio) return fg
        // Blend toward the extreme that maximizes contrast with the background.
        val towardBlack = ImageColorScheme.OPAQUE_BLACK
        val towardWhite = ImageColorScheme.OPAQUE_WHITE
        val extreme = if (contrastRatio(towardBlack, bg) >= contrastRatio(towardWhite, bg)) towardBlack else towardWhite
        if (contrastRatio(extreme, bg) < minRatio) return lerp(fg, extreme, 1.0)
        var lo = 0.0
        var hi = 1.0
        repeat(24) {
            val mid = (lo + hi) / 2
            if (contrastRatio(lerp(fg, extreme, mid), bg) >= minRatio) hi = mid else lo = mid
        }
        return lerp(fg, extreme, hi)
    }
}
