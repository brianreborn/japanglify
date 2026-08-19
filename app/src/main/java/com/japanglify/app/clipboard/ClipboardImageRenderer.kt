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
import com.japanglify.app.domain.ImageColorRole
import com.japanglify.app.domain.ImageColorScheme
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
    // Fallback ink color for a freshly-built paint before a caller assigns a
    // scheme role color; background + every role color come from the resolved
    // [ImageColorScheme] at draw time (see renderInterlinearToBitmap /
    // renderToBitmap), not from a fixed constant.
    private const val TEXT_COLOR = Color.BLACK

    /**
     * Real furigana is a small reading annotation, not a same-size third
     * line — shared with [com.japanglify.app.ui.SettingsFragment]'s in-app
     * Try-It preview ([android.text.style.RelativeSizeSpan]) so both
     * renderings of the same output agree on how much smaller.
     */
    const val FURIGANA_RELATIVE_SIZE = 0.62f

    /** One full-width kana glyph, used to size the wrap budget to a target pixel width. */
    private const val SAMPLE_FULLWIDTH_CHAR = "あ"

    private fun buildPaint(context: Context, sizeSp: Float = TEXT_SIZE_SP): TextPaint {
        val density = context.resources.displayMetrics.density
        return TextPaint().apply {
            isAntiAlias = true
            color = TEXT_COLOR
            textSize = sizeSp * density
        }
    }

    /** Target longest side for a notification largeIcon preview (px). Keeps memory and draw cost low. */
    private const val NOTIF_PREVIEW_MAX_PX = 144

    /** Scale down a full render for use as notification largeIcon. */
    fun createNotificationPreview(src: Bitmap): Bitmap {
        val w = src.width
        val h = src.height
        if (w <= NOTIF_PREVIEW_MAX_PX && h <= NOTIF_PREVIEW_MAX_PX) return src
        val scale = minOf(NOTIF_PREVIEW_MAX_PX.toFloat() / w, NOTIF_PREVIEW_MAX_PX.toFloat() / h)
        val nw = (w * scale).toInt().coerceAtLeast(1)
        val nh = (h * scale).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(src, nw, nh, true)
    }

    /**
     * Cooperative cancellation hook for preemptive background renders.
     * When this returns true the renderer should stop as soon as it can
     * (between rows for full renders, before heavy work for previews).
     * Default (null) means "never abort".
     */
    fun interface AbortCheck { fun shouldAbort(): Boolean }

    private val NEVER_ABORT: AbortCheck? = null

    /** Full-width kana glyph width in px at our render text size — for sizing the wrap budget. */
    fun fullwidthUnitPx(context: Context): Float =
        buildPaint(context).measureText(SAMPLE_FULLWIDTH_CHAR)

    /**
     * Ultra-cheap preview renderer used **only** for the notification largeIcon.
     * Much smaller text, capped rows, tight padding. Never used for the actual copy image.
     *
     * [abort] (when non-null) is polled before starting heavy measurement.
     */
    fun renderInterlinearToBitmapPreview(
        context: Context,
        rows: List<TripleScriptRenderer.InterlinearRowData>,
        settings: JapanglifySettings,
        abort: AbortCheck? = null
    ): Bitmap {
        if (abort?.shouldAbort() == true) {
            // Return a tiny 1x1 placeholder; callers treat null-or-small as "no preview".
            return Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
        }
        val density = context.resources.displayMetrics.density
        val previewSp = 9f
        val paint = buildPaint(context, previewSp)
        val furiganaPaint = buildPaint(context, previewSp * FURIGANA_RELATIVE_SIZE)
        val padding = (4f * density).toInt()
        val wordGapPx = 3f * density
        val rowGroupGapPx = 2f * density
        val lineHeight = paint.fontSpacing
        val order = lineOrder(settings)

        val measuredRows = rows.map { row ->
            data class Raw(
                val cell: MeasuredCell,
                val natural: Float,
                val baseDriven: Boolean
            )
            val raw = row.cells.map { c ->
                val furi = if (settings.includeFurigana) c.furigana else ""
                val roma = if (settings.includeRomaji) c.romaji else ""
                val gloss = if (settings.includeGlosses) c.gloss else ""
                val emoji = if (settings.includeEmoji) c.emoji else ""
                val baseW = paint.measureText(c.base)
                val furiW = furiganaPaint.measureText(furi)
                val natural = maxOf(baseW, furiW).coerceAtLeast(1f)
                Raw(
                    MeasuredCell(
                        furi, c.base, roma, c.isWordStart, gloss, emoji,
                        c.isPunctuation, c.isSameSegmentContinuation, natural
                    ),
                    natural,
                    baseDriven = baseW >= furiW
                )
            }
            val widths = FloatArray(raw.size)
            var i = 0
            while (i < raw.size) {
                var end = i + 1
                while (end < raw.size && raw[end].cell.isSameSegmentContinuation) end++
                val first = raw[i].cell
                val naturalTotal = (i until end).sumOf { raw[it].natural.toDouble() }.toFloat()
                val groupDemand = maxOf(
                    naturalTotal,
                    paint.measureText(first.romaji),
                    paint.measureText(first.gloss),
                    paint.measureText(first.emoji)
                )
                val extra = (groupDemand - naturalTotal).coerceAtLeast(0f)
                val slice = raw.subList(i, end)
                val distributed = distributeExtraWidthPx(slice.map { it.natural }, extra, slice.map { it.baseDriven })
                for (k in distributed.indices) widths[i + k] = distributed[k]
                i = end
            }
            raw.mapIndexed { idx, r -> r.cell.copy(width = widths[idx]) }
        }

        fun rowWidth(row: List<MeasuredCell>): Float {
            var w = 0f
            row.forEachIndexed { i, cell -> if (i > 0 && cell.isWordStart) w += wordGapPx; w += cell.width }
            return w
        }

        fun rowVisibleLines(row: List<MeasuredCell>): List<Line> = order.filter { line ->
            line == Line.BASE || row.any { it.text(line).isNotBlank() }
        }

        val capped = measuredRows.take(3)

        val contentWidth = capped.maxOfOrNull(::rowWidth) ?: 0f
        val rowLineCounts = capped.map { rowVisibleLines(it).size }
        val totalHeight = rowLineCounts.sum() * lineHeight +
            (capped.size - 1).coerceAtLeast(0) * rowGroupGapPx

        val bitmap = Bitmap.createBitmap(
            (contentWidth + padding * 2).toInt().coerceAtLeast(1),
            (totalHeight + padding * 2).toInt().coerceAtLeast(1),
            Bitmap.Config.ARGB_8888
        )
        val canvas = Canvas(bitmap)
        val scheme = settings.effectiveImageColorScheme
        canvas.drawColor(scheme.color(ImageColorRole.BACKGROUND))

        val baselineOffset = -paint.fontMetrics.top
        var y = padding.toFloat()
        for (row in capped) {
            for (line in rowVisibleLines(row)) {
                val linePaint = if (line == Line.FURIGANA) furiganaPaint else paint
                linePaint.color = scheme.color(line.role())
                var x = padding.toFloat()
                row.forEachIndexed { i, cell ->
                    if (i > 0 && cell.isWordStart) x += wordGapPx
                    val spansGroup = line == Line.ROMAJI || line == Line.GLOSS || line == Line.EMOJI
                    if (spansGroup && cell.isSameSegmentContinuation) {
                        x += cell.width
                        return@forEachIndexed
                    }
                    val text = cell.text(line)
                    if (text.isNotEmpty()) {
                        val spanWidth = if (spansGroup) {
                            var w = cell.width
                            var j = i + 1
                            while (j < row.size && row[j].isSameSegmentContinuation) {
                                w += row[j].width
                                j++
                            }
                            w
                        } else {
                            cell.width
                        }
                        val tx = if (cell.isPunctuation) {
                            x
                        } else {
                            val tw = linePaint.measureText(text)
                            x + (spanWidth - tw) / 2f
                        }
                        canvas.drawText(text, tx, y + baselineOffset, linePaint)
                    }
                    x += cell.width
                }
                y += lineHeight
            }
            y += rowGroupGapPx
        }
        return bitmap
    }

    fun paddingPx(context: Context): Int =
        (PADDING_DP * context.resources.displayMetrics.density).toInt()

    private data class MeasuredCell(
        val furigana: String,
        val base: String,
        val romaji: String,
        val isWordStart: Boolean,
        val gloss: String,
        val emoji: String,
        val isPunctuation: Boolean,
        val isSameSegmentContinuation: Boolean,
        val width: Float
    )

    /**
     * Which lines to draw, top to bottom. Gloss and emoji always trail last,
     * after furigana/base/romaji regardless of [RomajiPosition] -- matches
     * every other [com.japanglify.app.domain.OutputFormat]'s "own line, at
     * the bottom" placement for these two rather than being tied to romaji's
     * position-relative logic.
     */
    private enum class Line { FURIGANA, BASE, ROMAJI, GLOSS, EMOJI }

    private fun lineOrder(settings: JapanglifySettings): List<Line> =
        (when (settings.romajiPosition) {
            RomajiPosition.ABOVE, RomajiPosition.BEFORE -> listOf(Line.ROMAJI, Line.FURIGANA, Line.BASE)
            RomajiPosition.BELOW, RomajiPosition.AFTER -> listOf(Line.FURIGANA, Line.BASE, Line.ROMAJI)
        }) + listOf(Line.GLOSS, Line.EMOJI)

    private fun MeasuredCell.text(line: Line): String = when (line) {
        Line.FURIGANA -> furigana
        Line.BASE -> base
        Line.ROMAJI -> romaji
        Line.GLOSS -> gloss
        Line.EMOJI -> emoji
    }

    /**
     * The [ImageColorRole] a drawn [Line] paints with. Emoji map to BASE: a
     * real color-emoji glyph renders from its own COLR/CBDT tables and ignores
     * the paint color anyway, so tinting it is a harmless no-op, and a
     * mono-fallback emoji then at least matches the base ink.
     */
    private fun Line.role(): ImageColorRole = when (this) {
        Line.FURIGANA -> ImageColorRole.FURIGANA
        Line.BASE -> ImageColorRole.BASE
        Line.ROMAJI -> ImageColorRole.ROMAJI
        Line.GLOSS -> ImageColorRole.GLOSS
        Line.EMOJI -> ImageColorRole.BASE
    }

    /**
     * Pixel-precise interlinear render — measures every glyph itself instead of
     * trusting a font's fullwidth/halfwidth ratio or a text layout engine's own
     * line-breaking (which can silently insert an unwanted wrap when its
     * internal measurement of a run differs by even a pixel from a standalone
     * [android.graphics.Paint.measureText] call, e.g. across BiDi-adjacent
     * punctuation).
     *
     * [abort] (when non-null) is polled between major row groups so a
     * preemptive background job can exit early when a newer result has arrived.
     */
    fun renderInterlinearToBitmap(
        context: Context,
        rows: List<TripleScriptRenderer.InterlinearRowData>,
        settings: JapanglifySettings,
        abort: AbortCheck? = null
    ): Bitmap {
        val paint = buildPaint(context)
        val furiganaPaint = buildPaint(context, TEXT_SIZE_SP * FURIGANA_RELATIVE_SIZE)
        val density = context.resources.displayMetrics.density
        val padding = paddingPx(context)
        val wordGapPx = WORD_GAP_DP * density
        val rowGroupGapPx = ROW_GROUP_GAP_DP * density
        val lineHeight = paint.fontSpacing
        val order = lineOrder(settings)

        // Mirror TripleScriptRenderer.buildMeasuredRows: romaji/gloss/emoji
        // are word-level labels that live on the first sub-cell of a split
        // word (kanji + okurigana). Measuring each cell independently piles
        // that label's entire pixel width onto the first sub-cell and leaves
        // the rest snug — found live on 1.0.0-beta1: 懐 widened to fit
        // "na·tsu·ka·shi·i" while かしい sat in its own narrow column, so
        // the word read as two islands. Natural width is base/furigana only;
        // any extra the word-level labels demand is spread across the group.
        val measuredRows = rows.map { row ->
            data class Raw(
                val cell: MeasuredCell,
                val natural: Float,
                val baseDriven: Boolean
            )
            val raw = row.cells.map { c ->
                val furi = if (settings.includeFurigana) c.furigana else ""
                val roma = if (settings.includeRomaji) c.romaji else ""
                val gloss = if (settings.includeGlosses) c.gloss else ""
                val emoji = if (settings.includeEmoji) c.emoji else ""
                val baseW = paint.measureText(c.base)
                val furiW = furiganaPaint.measureText(furi)
                val natural = maxOf(baseW, furiW).coerceAtLeast(1f)
                Raw(
                    MeasuredCell(
                        furi, c.base, roma, c.isWordStart, gloss, emoji,
                        c.isPunctuation, c.isSameSegmentContinuation, natural
                    ),
                    natural,
                    baseDriven = baseW >= furiW
                )
            }
            val widths = FloatArray(raw.size)
            var i = 0
            while (i < raw.size) {
                var end = i + 1
                while (end < raw.size && raw[end].cell.isSameSegmentContinuation) end++
                val first = raw[i].cell
                val naturalTotal = (i until end).sumOf { raw[it].natural.toDouble() }.toFloat()
                val groupDemand = maxOf(
                    naturalTotal,
                    paint.measureText(first.romaji),
                    paint.measureText(first.gloss),
                    paint.measureText(first.emoji)
                )
                val extra = (groupDemand - naturalTotal).coerceAtLeast(0f)
                val slice = raw.subList(i, end)
                val distributed = distributeExtraWidthPx(slice.map { it.natural }, extra, slice.map { it.baseDriven })
                for (k in distributed.indices) widths[i + k] = distributed[k]
                i = end
            }
            raw.mapIndexed { idx, r -> r.cell.copy(width = widths[idx]) }
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
        val scheme = settings.effectiveImageColorScheme
        canvas.drawColor(scheme.color(ImageColorRole.BACKGROUND))

        val baselineOffset = -paint.fontMetrics.top
        var y = padding.toFloat()
        for ((rowIdx, row) in measuredRows.withIndex()) {
            // Cooperative abort between rows for long preemptive renders.
            if (abort?.shouldAbort() == true) {
                // Return the (partially drawn) bitmap; callers that care about
                // preemption will drop it via lastSource/generation checks.
                return bitmap
            }
            for (line in rowVisibleLines(row)) {
                val linePaint = if (line == Line.FURIGANA) furiganaPaint else paint
                linePaint.color = scheme.color(line.role())
                var x = padding.toFloat()
                row.forEachIndexed { i, cell ->
                    if (i > 0 && cell.isWordStart) x += wordGapPx
                    val spansGroup = line == Line.ROMAJI || line == Line.GLOSS || line == Line.EMOJI
                    if (spansGroup && cell.isSameSegmentContinuation) {
                        x += cell.width
                        return@forEachIndexed
                    }
                    val text = cell.text(line)
                    if (text.isNotEmpty()) {
                        // Word-level labels (romaji/gloss/emoji) center across
                        // the whole kanji+okurigana group. Per-cell labels
                        // (furigana/base) still center in their own cell.
                        // Punctuation stays left-anchored: centering let "、"
                        // and "," (different glyph widths) drift apart.
                        val spanWidth = if (spansGroup) {
                            var w = cell.width
                            var j = i + 1
                            while (j < row.size && row[j].isSameSegmentContinuation) {
                                w += row[j].width
                                j++
                            }
                            w
                        } else {
                            cell.width
                        }
                        val tx = if (cell.isPunctuation) {
                            x
                        } else {
                            val tw = linePaint.measureText(text)
                            x + (spanWidth - tw) / 2f
                        }
                        canvas.drawText(text, tx, y + baselineOffset, linePaint)
                    }
                    x += cell.width
                }
                y += lineHeight
            }
            y += rowGroupGapPx
        }
        return bitmap
    }

    /**
     * Spreads [extra] px across [natural] widths, preferring [growable]
     * cells (base-driven: growing them does not shift a kanji off the
     * middle of its furigana). Falls back to every cell if none qualify.
     * Last eligible cell absorbs the remainder so the group sums exactly.
     */
    private fun distributeExtraWidthPx(
        natural: List<Float>,
        extra: Float,
        growable: List<Boolean>
    ): List<Float> {
        if (extra <= 0f || natural.isEmpty()) return natural
        val eligible = natural.indices.filter { growable[it] }.ifEmpty { natural.indices.toList() }
        val eligibleTotal = eligible.sumOf { natural[it].toDouble() }.toFloat().coerceAtLeast(1f)
        val out = natural.toMutableList()
        var remaining = extra
        for ((k, idx) in eligible.withIndex()) {
            val share = if (k == eligible.lastIndex) remaining else extra * (natural[idx] / eligibleTotal)
            out[idx] += share
            remaining -= share
        }
        return out
    }

    /** Plain rasterization for non-columnar output formats (no cell alignment to preserve). */
    fun renderToBitmap(
        context: Context,
        text: String,
        scheme: ImageColorScheme = ImageColorScheme.DEFAULT.withGuaranteedContrast()
    ): Bitmap {
        val paint = buildPaint(context)
        paint.color = scheme.color(ImageColorRole.BASE)
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
        canvas.drawColor(scheme.color(ImageColorRole.BACKGROUND))
        canvas.translate(padding.toFloat(), padding.toFloat())
        layout.draw(canvas)
        return bitmap
    }

    /** Writes [bitmap] under the app cache dir and returns a FileProvider content Uri for it. */
    fun saveAndGetUri(context: Context, bitmap: Bitmap): Uri {
        val dir = File(context.cacheDir, "images").apply { mkdirs() }
        // Unique per call, not a fixed "japanglify_result.png" -- a second
        // Copy-image tap (a new result replacing an old one still on the
        // clipboard, or the same result copied twice) used to overwrite the
        // same file another app could still be mid-read on for a URI grant
        // that's still valid. Never cleaned up proactively here: an app that
        // received the URI may still legitimately hold a read grant on it
        // (e.g. queued in a draft that hasn't uploaded yet) -- like this
        // app's other cache usage, left for the OS to reclaim under storage
        // pressure rather than deleted the moment a newer one is written.
        val file = File(dir, "japanglify_result_${System.currentTimeMillis()}.png")
        FileOutputStream(file).use { out -> bitmap.compress(Bitmap.CompressFormat.PNG, 100, out) }
        return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }
}
