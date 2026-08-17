package com.japanglify.app

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Patterns
import android.widget.Toast
import com.japanglify.app.clipboard.ClipboardImageRenderCache
import com.japanglify.app.clipboard.ClipboardNotifications
import com.japanglify.app.clipboard.LastResultStore
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

    private fun handlePlainText(source: String) {
        val app = application as JapanglifyApp
        val expanded = runCatching {
            app.engine.expand(source, app.preferences.load())
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
        // is normally already done by the time it's tapped.
        ClipboardImageRenderCache.prerender(this, source)
        ClipboardNotifications.showResult(this, expanded)
        LastResultStore.writeToClipboard(this, expanded)
        Toast.makeText(this, R.string.notif_copied_ready_to_paste, Toast.LENGTH_LONG).show()
        finish()
    }

    companion object {
        private val EXECUTOR = Executors.newSingleThreadExecutor()
    }
}
