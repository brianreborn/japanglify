package com.japanglify.app.clipboard

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.net.Uri
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.core.content.FileProvider
import com.japanglify.app.domain.JapanglifySettings
import com.japanglify.app.domain.RomajiPosition
import com.japanglify.app.domain.TripleScriptRenderer
import java.io.File
import java.io.FileOutputStream

/**
 * Rasterizes a Japanglify result into a PNG so no host's font stack (Discord's
 * CJK-fallback widening every glyph on a line once *any* CJK codepoint is
 * present, or anything else) can re-break alignment we already got right.
 *
 * For [com.japanglify.app.domain.OutputFormat.INTERLINEAR] we don't reuse the
 * flattened, PAD-padded plain-text string at all — that scheme assumes every
 * fullwidth codepoint renders at exactly 2x a halfwidth one in whatever font
 * displays it, and real fonts don't always honor that (narrow punctuation
 * glyphs are a common offender). Instead we take the structured cell data
 * ([TripleScriptRenderer.buildInterlinearRows]) and lay out pixels ourselves
 * using this Paint's *actual measured* glyph widths, so alignment holds
 * regardless of any font's real advance widths.
 */
object ClipboardImageRenderer {
    private const val TEXT_SIZE_SP = 20f
    private const val PADDING_DP = 24f
    private const val WORD_GAP_DP = 10f
    private const val ROW_GROUP_GAP_DP = 14f
    private const val BG_COLOR = Color.WHITE
    private const val TEXT_COLOR = Color.BLACK

    /** One full-width kana glyph, used to size the wrap budget to a target pixel width. */
    private const val SAMPLE_FULLWIDTH_CHAR = "あ"

    private fun buildPaint(context: Context): TextPaint {
        val density = context.resources.displayMetrics.density
        return TextPaint().apply {
            isAntiAlias = true
            color = TEXT_COLOR
            textSize = TEXT_SIZE_SP * density
        }
    }

    /** Full-width kana glyph width in px at our render text size — for sizing the wrap budget. */
    fun fullwidthUnitPx(context: Context): Float =
        buildPaint(context).measureText(SAMPLE_FULLWIDTH_CHAR)

    fun paddingPx(context: Context): Int =
        (PADDING_DP * context.resources.displayMetrics.density).toInt()

    private data class MeasuredCell(
        val furigana: String,
        val base: String,
        val romaji: String,
        val isWordStart: Boolean,
        val width: Float
    )

    /** Which of the (up to) three lines to draw, top to bottom, per [RomajiPosition]. */
    private enum class Line { FURIGANA, BASE, ROMAJI }

    private fun lineOrder(settings: JapanglifySettings): List<Line> =
        when (settings.romajiPosition) {
            RomajiPosition.ABOVE, RomajiPosition.BEFORE -> listOf(Line.ROMAJI, Line.FURIGANA, Line.BASE)
            RomajiPosition.BELOW, RomajiPosition.AFTER -> listOf(Line.FURIGANA, Line.BASE, Line.ROMAJI)
        }

    private fun MeasuredCell.text(line: Line): String = when (line) {
        Line.FURIGANA -> furigana
        Line.BASE -> base
        Line.ROMAJI -> romaji
    }

    /**
     * Pixel-precise interlinear render — measures every glyph itself instead of
     * trusting a font's fullwidth/halfwidth ratio or a text layout engine's own
     * line-breaking (which can silently insert an unwanted wrap when its
     * internal measurement of a run differs by even a pixel from a standalone
     * [android.graphics.Paint.measureText] call, e.g. across BiDi-adjacent
     * punctuation).
     */
    fun renderInterlinearToBitmap(
        context: Context,
        rows: List<TripleScriptRenderer.InterlinearRowData>,
        settings: JapanglifySettings
    ): Bitmap {
        val paint = buildPaint(context)
        val density = context.resources.displayMetrics.density
        val padding = paddingPx(context)
        val wordGapPx = WORD_GAP_DP * density
        val rowGroupGapPx = ROW_GROUP_GAP_DP * density
        val lineHeight = paint.fontSpacing
        val order = lineOrder(settings)

        val measuredRows = rows.map { row ->
            row.cells.map { c ->
                val furi = if (settings.includeFurigana) c.furigana else ""
                val roma = if (settings.includeRomaji) c.romaji else ""
                val width = maxOf(
                    paint.measureText(c.base),
                    paint.measureText(furi),
                    paint.measureText(roma)
                ).coerceAtLeast(1f)
                MeasuredCell(furi, c.base, roma, c.isWordStart, width)
            }
        }

        fun rowWidth(row: List<MeasuredCell>): Float {
            var w = 0f
            row.forEachIndexed { i, cell -> if (i > 0 && cell.isWordStart) w += wordGapPx; w += cell.width }
            return w
        }

        fun rowVisibleLines(row: List<MeasuredCell>): List<Line> = order.filter { line ->
            line == Line.BASE || row.any { it.text(line).isNotBlank() }
        }

        val contentWidth = measuredRows.maxOfOrNull(::rowWidth) ?: 0f
        val rowLineCounts = measuredRows.map { rowVisibleLines(it).size }
        val totalHeight = rowLineCounts.sum() * lineHeight +
            (measuredRows.size - 1).coerceAtLeast(0) * rowGroupGapPx

        val bitmap = Bitmap.createBitmap(
            (contentWidth + padding * 2).toInt().coerceAtLeast(1),
            (totalHeight + padding * 2).toInt().coerceAtLeast(1),
            Bitmap.Config.ARGB_8888
        )
        val canvas = Canvas(bitmap)
        canvas.drawColor(BG_COLOR)

        val baselineOffset = -paint.fontMetrics.top
        var y = padding.toFloat()
        for (row in measuredRows) {
            for (line in rowVisibleLines(row)) {
                var x = padding.toFloat()
                row.forEachIndexed { i, cell ->
                    if (i > 0 && cell.isWordStart) x += wordGapPx
                    val text = cell.text(line)
                    if (text.isNotEmpty()) {
                        val tw = paint.measureText(text)
                        canvas.drawText(text, x + (cell.width - tw) / 2f, y + baselineOffset, paint)
                    }
                    x += cell.width
                }
                y += lineHeight
            }
            y += rowGroupGapPx
        }
        return bitmap
    }

    /** Plain rasterization for non-columnar output formats (no cell alignment to preserve). */
    fun renderToBitmap(context: Context, text: String): Bitmap {
        val paint = buildPaint(context)
        val padding = paddingPx(context)
        val lines = text.split("\n")
        val measuredWidth = lines.maxOf { paint.measureText(it) }
        // Safety margin: StaticLayout does its own line-breaking against the width
        // we give it, and its internal measurement of a run can differ from a
        // standalone measureText call by a pixel or two (BiDi-adjacent punctuation
        // is a known case) — without slack that can trigger an unwanted mid-line wrap.
        val contentWidth = (measuredWidth + 8f * context.resources.displayMetrics.density)
            .toInt()
            .coerceAtLeast(1)

        val layout = StaticLayout.Builder
            .obtain(text, 0, text.length, paint, contentWidth)
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setIncludePad(false)
            .build()

        val bitmap = Bitmap.createBitmap(
            contentWidth + padding * 2,
            layout.height + padding * 2,
            Bitmap.Config.ARGB_8888
        )
        val canvas = Canvas(bitmap)
        canvas.drawColor(BG_COLOR)
        canvas.translate(padding.toFloat(), padding.toFloat())
        layout.draw(canvas)
        return bitmap
    }

    /** Writes [bitmap] under the app cache dir and returns a FileProvider content Uri for it. */
    fun saveAndGetUri(context: Context, bitmap: Bitmap): Uri {
        val dir = File(context.cacheDir, "images").apply { mkdirs() }
        val file = File(dir, "japanglify_result.png")
        FileOutputStream(file).use { out -> bitmap.compress(Bitmap.CompressFormat.PNG, 100, out) }
        return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }
}
