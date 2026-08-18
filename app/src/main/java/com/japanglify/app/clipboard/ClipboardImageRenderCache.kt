package com.japanglify.app.clipboard

import android.content.Context
import android.net.Uri
import com.japanglify.app.JapanglifyApp
import com.japanglify.app.domain.OutputFormat
import java.util.concurrent.Callable
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit

/**
 * Renders the "Copy image" bitmap in the background, kicked off as soon as
 * the textual result exists (see [ClipboardProcessor.processText]) rather
 * than waiting for the user to actually tap "Copy image" -- rendering
 * ([ClipboardImageRenderer.renderInterlinearToBitmap]/`renderToBitmap`, PNG
 * compression, and the file write) is real, measurable work for anything
 * beyond a short result, and doing all of it synchronously on
 * [ClipboardWriteActivity]'s main thread was both janky and, worse, delayed
 * the one call ([android.content.ClipboardManager.setPrimaryClip]) this
 * session's clipboard-emptying investigation specifically wants to happen
 * as soon as possible after the window gains focus (see
 * [ClipboardWriteActivity]'s doc comment). By the time a user actually taps
 * "Copy image" -- always at least the time it takes to notice and read the
 * result notification -- the background render has normally already
 * finished, so [await] usually returns immediately.
 */
object ClipboardImageRenderCache {
    private val executor = Executors.newSingleThreadExecutor()

    private data class Job(val source: String, val future: Future<Uri>)

    @Volatile
    private var current: Job? = null

    /** Call as soon as the textual result for [source] is ready. */
    fun prerender(context: Context, source: String) {
        val appContext = context.applicationContext
        current = Job(source, executor.submit(Callable { renderNow(appContext, source) }))
    }

    /**
     * Returns the rendered image's Uri for [source] -- the in-flight/finished
     * prerender job if one matches, otherwise renders synchronously as a
     * fallback (e.g. the process was recycled between [prerender] and this
     * call, so no job survived).
     */
    fun await(context: Context, source: String): Uri {
        val job = current
        if (job != null && job.source == source) {
            try {
                return job.future.get(AWAIT_TIMEOUT_MS, TimeUnit.MILLISECONDS)
            } catch (e: ExecutionException) {
                throw e.cause ?: e
            }
            // A timeout falls through to the synchronous fallback below
            // rather than propagating -- an unusually slow background
            // render (an overloaded device) shouldn't also break the
            // fallback path that would otherwise have worked.
        }
        return renderNow(context.applicationContext, source)
    }

    private fun renderNow(context: Context, source: String): Uri {
        val app = context.applicationContext as JapanglifyApp
        // Re-wrap at a column budget sized to the host's own input width when
        // we captured one, so the pasted image looks native there rather than
        // reusing whatever width the on-screen notification text happened to use.
        val baseSettings = app.preferences.load()
        val hostWidthPx = LastResultStore.lastHostFieldWidthPx
        val unitPx = ClipboardImageRenderer.fullwidthUnitPx(context)
        val settingsForImage = if (hostWidthPx != null && hostWidthPx > 0 && unitPx > 0f) {
            val usablePx = hostWidthPx - ClipboardImageRenderer.paddingPx(context) * 2
            val units = (usablePx / unitPx).toInt().coerceIn(6, 40)
            baseSettings.copy(maxLineWidthFullwidth = units)
        } else {
            baseSettings
        }
        val bitmap = if (settingsForImage.outputFormat == OutputFormat.INTERLINEAR) {
            val rows = checkNotNull(app.engine.buildInterlinearRows(source, settingsForImage).takeIf { it.isNotEmpty() }) {
                "No interlinear rows for source"
            }
            ClipboardImageRenderer.renderInterlinearToBitmap(context, rows, settingsForImage)
        } else {
            val rendered = checkNotNull(app.engine.expand(source, settingsForImage).takeIf { it.isNotEmpty() }) {
                "Empty expansion for source"
            }
            ClipboardImageRenderer.renderToBitmap(context, rendered, settingsForImage.effectiveImageColorScheme)
        }
        return ClipboardImageRenderer.saveAndGetUri(context, bitmap)
    }

    private const val AWAIT_TIMEOUT_MS = 5_000L
}
