package com.japanglify.app.clipboard

import android.accessibilityservice.AccessibilityService
import android.graphics.Bitmap
import android.graphics.Paint
import android.graphics.Rect
import android.os.Build
import android.view.Display
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.japanese.JapaneseTextRecognizerOptions
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.roundToInt

/**
 * Measures how wide a host app *actually* renders a CJK glyph relative to a
 * Latin one, via on-device OCR against a real screenshot of the field the
 * user just copied from -- instead of assuming the traditional "CJK is
 * exactly double-width" ratio [TripleScriptRenderer.INTERLINEAR]'s plain-text
 * column padding defaults to. That default breaks down for a host whose font
 * stack falls back to a different (non-CJK-aware) font specifically for
 * kanji/kana, which real hosts do -- see the session notes this landed from
 * for the actual investigation.
 *
 * Why OCR at all, rather than reading the host's `Paint`/`Typeface`
 * directly: [android.view.accessibility.AccessibilityNodeInfo] deliberately
 * doesn't expose that -- it's a minimal semantic snapshot for screen
 * readers, not a rendering-introspection API, and Android has no public API
 * for "what font is this foreign view using." `getExtraRenderingInfo()`
 * (API 30+) is the one crack in that wall (real text size in px), but stops
 * short of glyph identity or width tables. OCR against
 * [AccessibilityService.takeScreenshot] (also API 30+, and crucially
 * requires no extra permission dialog *because* it's called from an
 * already-bound accessibility service) is the sanctioned way to get ground
 * truth Android won't hand over directly.
 *
 * Only measures the *source* field's font, not the paste destination's --
 * a real assumption, not a guarantee, but the dominant workflow (copy
 * Japanese text in an app, convert, paste back into the same field/app)
 * makes them the same box in practice.
 *
 * Deliberately never blocks the copy pipeline: OCR + a screenshot easily
 * takes longer than users expect a Copy notification to appear, so results
 * are cached per host package and only apply starting with that host's
 * *next* copy in this process's lifetime -- this copy still renders with
 * whatever ratio (profiled earlier, or the domain default) was already
 * known.
 */
object HostFontProfiler {
    private val cache = ConcurrentHashMap<String, Int>()

    /** Previously profiled CJK display-width units for [packageName], if any. */
    fun cachedUnitsFor(packageName: String?): Int? = packageName?.let { cache[it] }

    /**
     * Best-effort, always-async, never-throws. No-ops below API 30 (no
     * [AccessibilityService.takeScreenshot]) or if OCR finds nothing usable
     * -- the domain default (2) simply keeps applying for that host.
     */
    fun profileAsync(service: AccessibilityService, packageName: String?, fieldBoundsInScreen: Rect) {
        if (packageName.isNullOrEmpty()) return
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return
        if (fieldBoundsInScreen.width() <= 0 || fieldBoundsInScreen.height() <= 0) return

        try {
            service.takeScreenshot(
                Display.DEFAULT_DISPLAY,
                service.mainExecutor,
                object : AccessibilityService.TakeScreenshotCallback {
                    override fun onSuccess(result: AccessibilityService.ScreenshotResult) {
                        runCatching { handleScreenshot(result, packageName, fieldBoundsInScreen) }
                        result.hardwareBuffer.close()
                    }

                    override fun onFailure(errorCode: Int) = Unit
                }
            )
        } catch (_: Exception) {
            // Screenshot API refused (rate-limited, no active window, …) — fine, stays unprofiled.
        }
    }

    private fun handleScreenshot(
        result: AccessibilityService.ScreenshotResult,
        packageName: String,
        fieldBoundsInScreen: Rect
    ) {
        val hardwareBitmap = Bitmap.wrapHardwareBuffer(result.hardwareBuffer, result.colorSpace) ?: return
        val screenBitmap = try {
            hardwareBitmap.copy(Bitmap.Config.ARGB_8888, false) ?: return
        } finally {
            hardwareBitmap.recycle()
        }
        try {
            val clamped = Rect(0, 0, screenBitmap.width, screenBitmap.height)
            if (!clamped.intersect(fieldBoundsInScreen)) return
            val crop = Bitmap.createBitmap(
                screenBitmap, clamped.left, clamped.top, clamped.width(), clamped.height()
            )
            val recognizer = TextRecognition.getClient(JapaneseTextRecognizerOptions.Builder().build())
            recognizer.process(InputImage.fromBitmap(crop, 0))
                .addOnSuccessListener { text -> onOcrSuccess(text, packageName) }
                .addOnCompleteListener { recognizer.close() }
        } finally {
            screenBitmap.recycle()
        }
    }

    private fun onOcrSuccess(text: com.google.mlkit.vision.text.Text, packageName: String) {
        val cjkWidths = mutableListOf<Float>()
        val heights = mutableListOf<Int>()
        for (block in text.textBlocks) {
            for (line in block.lines) {
                for (element in line.elements) {
                    val box = element.boundingBox ?: continue
                    val cjkCount = element.text.count { isCjk(it) }
                    if (cjkCount == 0 || element.text.length != cjkCount) continue
                    // Approximation: divide the element's total box width evenly
                    // across its glyphs. Real inter-glyph kerning varies, but
                    // CJK fonts are traditionally near-monospaced within a run,
                    // so this holds up far better here than it would for Latin text.
                    cjkWidths += box.width().toFloat() / cjkCount
                    heights += box.height()
                }
            }
        }
        if (cjkWidths.isEmpty()) return
        val avgCjkWidthPx = cjkWidths.average().toFloat()
        val avgHeightPx = heights.average().toFloat()
        if (avgCjkWidthPx <= 0f || avgHeightPx <= 0f) return

        // Reference Latin glyph width at a matching apparent font size, from
        // our own bundled typeface -- not the host's, which we have no way
        // to query directly (see class doc). The box height is a reasonable
        // font-size proxy (ascent+descent), not an exact em-size, but the
        // ratio this produces is still a meaningfully better estimate than
        // an untuned universal constant.
        val paint = Paint().apply { textSize = avgHeightPx }
        val latinRefWidthPx = paint.measureText("n")
        if (latinRefWidthPx <= 0f) return

        val ratio = avgCjkWidthPx / latinRefWidthPx
        val units = ratio.roundToInt().coerceIn(1, 4)
        cache[packageName] = units
    }

    private fun isCjk(c: Char): Boolean {
        val cp = c.code
        return cp in 0x2E80..0xA4CF || cp in 0x3040..0x30FF || cp in 0xF900..0xFAFF
    }
}
