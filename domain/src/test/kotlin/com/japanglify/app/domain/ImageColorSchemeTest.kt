package com.japanglify.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ImageColorSchemeTest {

    @Test
    fun everyBuiltInSchemeIsAlreadyLegible() {
        // Presets are hand-tuned to pass on their own -- the guarantee pass
        // should be a no-op for them (nothing to fix).
        for (scheme in ImageColorScheme.PRESETS) {
            val bg = scheme.color(ImageColorRole.BACKGROUND)
            for (role in ImageColorRole.entries) {
                if (role == ImageColorRole.BACKGROUND) continue
                val ratio = ColorContrast.contrastRatio(scheme.color(role), bg)
                assertTrue(
                    "${scheme.id}/$role contrast $ratio should already meet ${ImageColorScheme.MIN_TEXT_CONTRAST}",
                    ratio >= ImageColorScheme.MIN_TEXT_CONTRAST
                )
            }
        }
    }

    @Test
    fun guaranteeRescuesADeliberatelyIllegibleScheme() {
        // Light-gray text on a white background: unreadable as declared.
        val bad = ImageColorScheme(
            id = "bad",
            displayName = "bad",
            colors = mapOf(
                ImageColorRole.BACKGROUND to ImageColorScheme.OPAQUE_WHITE,
                ImageColorRole.BASE to 0xFFEEEEEE.toInt(),
                ImageColorRole.FURIGANA to 0xFFDDDDDD.toInt(),
                ImageColorRole.ROMAJI to 0xFFF0F0F0.toInt(),
                ImageColorRole.GLOSS to 0xFFCCCCCC.toInt()
            )
        )
        // Before: at least one role fails badly.
        assertTrue(ColorContrast.contrastRatio(bad.color(ImageColorRole.BASE), ImageColorScheme.OPAQUE_WHITE) < ImageColorScheme.MIN_TEXT_CONTRAST)

        val fixed = bad.withGuaranteedContrast()
        // Background is never touched.
        assertEquals(ImageColorScheme.OPAQUE_WHITE, fixed.color(ImageColorRole.BACKGROUND))
        // Every text role now meets the floor.
        for (role in listOf(ImageColorRole.BASE, ImageColorRole.FURIGANA, ImageColorRole.ROMAJI, ImageColorRole.GLOSS)) {
            val ratio = ColorContrast.contrastRatio(fixed.color(role), ImageColorScheme.OPAQUE_WHITE)
            assertTrue("$role rescued to $ratio", ratio >= ImageColorScheme.MIN_TEXT_CONTRAST)
        }
        // And it actually changed the colors (didn't just pass them through).
        assertNotEquals(bad.color(ImageColorRole.BASE), fixed.color(ImageColorRole.BASE))
    }

    @Test
    fun guaranteeIsIdempotentAndLeavesPassingColorsUntouched() {
        val onceFixed = ImageColorScheme.STANDARD.withGuaranteedContrast()
        val twiceFixed = onceFixed.withGuaranteedContrast()
        assertEquals(onceFixed.colors, twiceFixed.colors)
        // STANDARD already passes, so nothing should have changed at all.
        assertEquals(ImageColorScheme.STANDARD.colors, onceFixed.colors)
    }

    @Test
    fun contrastRatioMatchesKnownWcagAnchors() {
        // Black on white is the canonical 21:1.
        assertEquals(21.0, ColorContrast.contrastRatio(0xFF000000.toInt(), 0xFFFFFFFF.toInt()), 0.05)
        // A color against itself is 1:1.
        assertEquals(1.0, ColorContrast.contrastRatio(0xFF3366CC.toInt(), 0xFF3366CC.toInt()), 0.0001)
    }

    @Test
    fun fromIdRoundTripsAndFallsBackToDefault() {
        for (scheme in ImageColorScheme.PRESETS) {
            assertEquals(scheme, ImageColorScheme.fromId(scheme.id))
        }
        assertEquals(ImageColorScheme.DEFAULT, ImageColorScheme.fromId("nonexistent"))
        assertEquals(ImageColorScheme.DEFAULT, ImageColorScheme.fromId(null))
    }

    @Test
    fun pickerOffersEveryPresetPlusCustom() {
        val ids = ImageColorScheme.PICKER_OPTIONS.map { it.first }
        assertEquals(ImageColorScheme.PRESETS.map { it.id } + ImageColorScheme.CUSTOM_ID, ids)
    }

    @Test
    fun settingsResolvesCustomColorsWhenSelected() {
        val teal = 0xFF008080.toInt()
        val cream = 0xFFFFFDF0.toInt()
        val s = JapanglifySettings(
            imageColorSchemeId = ImageColorScheme.CUSTOM_ID,
            customImageBackgroundColor = cream,
            customImageBaseColor = teal,
            customImageFuriganaColor = teal,
            customImageRomajiColor = teal,
            customImageGlossColor = teal
        )
        val resolved = s.effectiveImageColorScheme
        assertEquals(ImageColorScheme.CUSTOM_ID, resolved.id)
        // Background is never touched by the guarantee; teal-on-cream already
        // passes, so the text roles come through as chosen.
        assertEquals(cream, resolved.color(ImageColorRole.BACKGROUND))
        assertEquals(teal, resolved.color(ImageColorRole.BASE))
    }

    @Test
    fun contrastGuaranteeAlsoRescuesAnIllegibleCustomScheme() {
        // White text on a white background, chosen by hand -- unreadable.
        val s = JapanglifySettings(
            imageColorSchemeId = ImageColorScheme.CUSTOM_ID,
            customImageBackgroundColor = ImageColorScheme.OPAQUE_WHITE,
            customImageBaseColor = ImageColorScheme.OPAQUE_WHITE,
            customImageFuriganaColor = ImageColorScheme.OPAQUE_WHITE,
            customImageRomajiColor = ImageColorScheme.OPAQUE_WHITE,
            customImageGlossColor = ImageColorScheme.OPAQUE_WHITE
        )
        val resolved = s.effectiveImageColorScheme
        for (role in listOf(ImageColorRole.BASE, ImageColorRole.FURIGANA, ImageColorRole.ROMAJI, ImageColorRole.GLOSS)) {
            val ratio = ColorContrast.contrastRatio(resolved.color(role), ImageColorScheme.OPAQUE_WHITE)
            assertTrue("$role rescued to $ratio against white", ratio >= ImageColorScheme.MIN_TEXT_CONTRAST)
        }
    }

    @Test
    fun nonCustomSchemeIgnoresCustomColorFields() {
        val s = JapanglifySettings(
            imageColorSchemeId = ImageColorScheme.REVERSE.id,
            customImageBaseColor = 0xFFFF00FF.toInt() // garish, must be ignored
        )
        assertEquals(ImageColorScheme.REVERSE.id, s.effectiveImageColorScheme.id)
    }
}
