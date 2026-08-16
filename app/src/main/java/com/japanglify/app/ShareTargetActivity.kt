package com.japanglify.app

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import com.japanglify.app.clipboard.ClipboardImageRenderCache
import com.japanglify.app.clipboard.ClipboardNotifications
import com.japanglify.app.clipboard.LastResultStore

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
 * Copies the result to the clipboard immediately (not gated behind a
 * notification tap) *and* posts the same rich result notification
 * (Copy/Copy image/Translate/…) [com.japanglify.app.clipboard.ClipboardProcessor]
 * already shows for the Copy-hook path — "ultimately reliable" means no
 * extra step is required to get a pasteable result, but the fuller options
 * (e.g. Copy image for a host that mangles plain-text CJK alignment) stay
 * one tap away instead of being dropped.
 */
class ShareTargetActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val source = if (Intent.ACTION_SEND == intent?.action && intent.type?.startsWith("text/") == true) {
            intent.getStringExtra(Intent.EXTRA_TEXT)?.trim()?.takeIf { it.isNotEmpty() }
        } else {
            null
        }
        if (source == null) {
            Toast.makeText(this, R.string.share_target_no_text, Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        LastResultStore.rememberHost(callingPackage, null)
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
}
