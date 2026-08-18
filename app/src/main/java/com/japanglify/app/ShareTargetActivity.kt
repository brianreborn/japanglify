package com.japanglify.app

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Patterns
import android.widget.Toast
import com.japanglify.app.clipboard.ClipboardImageRenderCache
import com.japanglify.app.clipboard.ClipboardImageRenderer
import com.japanglify.app.clipboard.ClipboardNotifications
import com.japanglify.app.clipboard.LastResultStore
import com.japanglify.app.data.ShareTargetRepository
import com.japanglify.app.domain.OutputFormat
import com.japanglify.app.domain.ShareTarget
import com.japanglify.app.domain.ShareTargetAction
import com.japanglify.app.web.UrlTextExtractor
import java.util.concurrent.Executors

/**
 * Transparent activity registered for [Intent.ACTION_SEND] (any `text/…` mime) —
 * the "Share to Japanglify" entry every app's native Share sheet offers,
 * requested explicitly as an *ultimately reliable* alternative to the
 * PROCESS_TEXT selection-menu item ([ProcessTextActivity]): whether that
 * item shows up in a given host's text-selection menu at all is entirely
 * that host's own choice (its own `<queries>` package-visibility
 * declaration decides what it even looks for — see the in-app Copy-hook
 * diagnostics card, which already documents this as platform behavior, not
 * a Japanglify registration bug). Share is a first-class OS-level surface
 * essentially every app supports, so it doesn't depend on that per-host
 * variability at all.
 *
 * Deliberately bypasses [com.japanglify.app.clipboard.ClipboardProcessor]'s
 * `isAssistWanted` gate (the Copy-assist enabled/disabled preference) --
 * same reasoning as [ProcessTextActivity] not depending on it either: this
 * is its own explicit, always-available entry point a user reaches by
 * deliberately choosing "Share → Japanglify," not something that should
 * silently no-op just because Copy-assist happens to be off.
 *
 * Two shapes, depending on what was actually shared:
 * - **Plain text** (the common case: text selected somewhere and shared
 *   directly): converted immediately, copied to the clipboard right away
 *   (not gated behind a notification tap), and the same rich result
 *   notification (Copy/Copy image/Translate/…) the Copy-hook path shows.
 *   "Ultimately reliable" means no extra step for a pasteable result.
 * - **A URL** (a browser's "Share" on a whole page — see [UrlTextExtractor]):
 *   fetched and its visible text extracted, then handed to
 *   [com.japanglify.app.ui.SettingsFragment]'s Try-It card for the user to
 *   trim/edit before converting, rather than blindly Japanglifying a raw
 *   URL string (nonsense) or guessing which part of the page is "the
 *   article" (a losing per-site heuristic battle) -- see
 *   [UrlTextExtractor]'s doc comment for why trimming is manual, not automatic.
 */
class ShareTargetActivity : Activity() {

    private val mainHandler = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val shared = if (Intent.ACTION_SEND == intent?.action && intent.type?.startsWith("text/") == true) {
            intent.getStringExtra(Intent.EXTRA_TEXT)?.trim()?.takeIf { it.isNotEmpty() }
        } else {
            null
        }
        if (shared == null) {
            Toast.makeText(this, R.string.share_target_no_text, Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        LastResultStore.rememberHost(callingPackage, null)

        if (Patterns.WEB_URL.matcher(shared).matches()) {
            handleUrl(withScheme(shared))
        } else {
            handlePlainText(shared)
        }
    }

    /**
     * [Patterns.WEB_URL] matches scheme-less domain-looking text too (e.g.
     * "example.com/page"), but `java.net.URL` requires an explicit scheme --
     * constructing one from a bare match throws `MalformedURLException`.
     * Falling through to Japanglifying the literal text as if it were
     * Japanese prose would also be wrong: a bare domain the user shared is
     * never meant as text to convert, it's a page to go fetch. Assuming
     * `https://` for a scheme-less match -- the same convention every
     * modern browser/site already applies to bare-domain input -- keeps a
     * shared "example.com" routed to "go fetch that page" instead of either
     * crashing or being treated as plain text.
     */
    private fun withScheme(candidate: String): String =
        if (candidate.startsWith("http://", ignoreCase = true) || candidate.startsWith("https://", ignoreCase = true)) {
            candidate
        } else {
            "https://$candidate"
        }

    private fun handleUrl(url: String) {
        EXECUTOR.execute {
            val text = UrlTextExtractor.fetchAndExtractText(url)
            mainHandler.post {
                if (text == null) {
                    Toast.makeText(this, R.string.url_fetch_failed, Toast.LENGTH_LONG).show()
                    finish()
                    return@post
                }
                startActivity(
                    Intent(this, SettingsActivity::class.java)
                        .putExtra(SettingsActivity.EXTRA_PREFILL_TEXT, text)
                )
                finish()
            }
        }
    }

    /**
     * A user-defined [ShareTarget]'s dynamic shortcut, when picked from the
     * Share sheet, arrives here as the *original* ACTION_SEND intent plus
     * this platform-standard extra set to the shortcut's own id — see
     * [com.japanglify.app.share.ShareTargetShortcuts]'s doc for why that
     * (not the shortcut's own declared intent) is what actually fires. Null
     * for the default "Japanglify" Share entry (no shortcut involved) or any
     * other host that doesn't go through Direct Share.
     */
    private fun resolvedTarget(): ShareTarget? {
        val shortcutId = intent?.getStringExtra(Intent.EXTRA_SHORTCUT_ID) ?: return null
        return ShareTargetRepository(this).find(shortcutId)
    }

    private fun handlePlainText(source: String) {
        val app = application as JapanglifyApp
        val target = resolvedTarget()
        val settings = target?.settings ?: app.preferences.load()
        val expanded = runCatching {
            app.engine.expand(source, settings)
        }.getOrElse { err ->
            Toast.makeText(
                this,
                getString(R.string.error_processing, err.message ?: "unknown"),
                Toast.LENGTH_SHORT
            ).show()
            finish()
            return
        }

        LastResultStore.save(this, source, expanded)
        // Same reasoning as the Copy-hook path (see ClipboardImageRenderCache's
        // doc comment): kicked off now so "Copy image" from the notification
        // is normally already done by the time it's tapped. Harmless to also
        // do this for a COPY_IMAGE target below -- it renders with the
        // *global* settings for that later notification tap, independent of
        // the target-aware render this method does for the immediate write.
        ClipboardImageRenderCache.prerender(this, source)
        ClipboardNotifications.showResult(this, expanded)

        if (target?.action == ShareTargetAction.COPY_IMAGE) {
            copyImageForTarget(source, settings)
        } else {
            LastResultStore.writeToClipboard(this, expanded)
            Toast.makeText(this, R.string.notif_copied_ready_to_paste, Toast.LENGTH_LONG).show()
            finish()
        }
    }

    /**
     * Renders [source] to a PNG with [settings] (the target's own frozen
     * snapshot, not necessarily the current global settings) and puts that
     * image on the clipboard instead of text — see [ShareTargetAction.COPY_IMAGE].
     * Doesn't reuse [ClipboardImageRenderCache] directly: that cache always
     * renders with the *global* live settings (see its own doc), which would
     * silently ignore a target's frozen snapshot, the same class of bug this
     * whole feature exists to avoid. Mirrors its render logic minus the
     * host-field-width adjustment (no accessibility-captured host field here
     * to size against).
     */
    private fun copyImageForTarget(source: String, settings: com.japanglify.app.domain.JapanglifySettings) {
        val app = application as JapanglifyApp
        EXECUTOR.execute {
            val result = runCatching {
                val bitmap = if (settings.outputFormat == OutputFormat.INTERLINEAR) {
                    val rows = app.engine.buildInterlinearRows(source, settings)
                    ClipboardImageRenderer.renderInterlinearToBitmap(this, rows, settings)
                } else {
                    ClipboardImageRenderer.renderToBitmap(this, app.engine.expand(source, settings))
                }
                ClipboardImageRenderer.saveAndGetUri(this, bitmap)
            }
            mainHandler.post {
                val uri = result.getOrNull()
                if (uri == null) {
                    Toast.makeText(this, R.string.error_processing_generic, Toast.LENGTH_SHORT).show()
                    finish()
                    return@post
                }
                LastResultStore.beginOutgoingWrite(uri.toString())
                val cm = getSystemService(ClipboardManager::class.java)
                if (cm == null) {
                    Toast.makeText(this, R.string.error_processing_generic, Toast.LENGTH_SHORT).show()
                    finish()
                    return@post
                }
                cm.setPrimaryClip(ClipData.newUri(contentResolver, LastResultStore.CLIP_LABEL, uri))
                Toast.makeText(this, R.string.notif_copied_image_ready, Toast.LENGTH_LONG).show()
                finish()
            }
        }
    }

    companion object {
        private val EXECUTOR = Executors.newSingleThreadExecutor()
    }
}
