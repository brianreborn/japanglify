package com.japanglify.app

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Transparent activity registered for [Intent.ACTION_PROCESS_TEXT].
 *
 * No UI, no confirmation: expands the selection with furigana + romaji
 * using the user's saved settings, then either replaces the selection
 * (editable hosts) or copies the result to the clipboard (read-only hosts).
 *
 * Uses platform [Activity] (not AppCompat) so the process-text result path
 * stays as light as possible and does not depend on AppCompat window
 * installation for a translucent theme.
 */
class ProcessTextActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val raw = intent?.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT)
        if (raw.isNullOrEmpty()) {
            setResult(RESULT_CANCELED)
            finish()
            return
        }

        val readOnly = intent.getBooleanExtra(Intent.EXTRA_PROCESS_TEXT_READONLY, true)
        com.japanglify.app.clipboard.LastResultStore.rememberHost(callingPackage, null)
        val app = application as JapanglifyApp
        val settings = app.preferences.load()
        val source = raw.toString()

        val expanded = runCatching {
            app.engine.expand(source, settings)
        }.getOrElse { err ->
            Toast.makeText(
                this,
                getString(R.string.error_processing, err.message ?: "unknown"),
                Toast.LENGTH_SHORT
            ).show()
            setResult(RESULT_CANCELED)
            finish()
            return
        }

        // Fastpath: translation defaults off, and this is the one entry
        // point (the interactive selection-toolbar action) where the user
        // is waiting synchronously. When disabled, this is byte-for-byte
        // the same path as before the feature existed — no thread hand-off,
        // `app.translator` is never touched.
        if (!app.preferences.isTranslationEnabled()) {
            finishWithResult(expanded, readOnly)
            return
        }

        // Enabled: block a short-lived background thread on a bounded wait
        // for the translation, then finish on the main thread either way —
        // never hang, never risk the offline result already in hand.
        Thread {
            val latch = CountDownLatch(1)
            var translation: String? = null
            app.translator.translateAsync(source) { result ->
                translation = result
                latch.countDown()
            }
            latch.await(TRANSLATE_TIMEOUT_MS, TimeUnit.MILLISECONDS)
            val finalText = translation?.let { "$expanded\n\n$it" } ?: expanded
            runOnUiThread { finishWithResult(finalText, readOnly) }
        }.start()
    }

    private fun finishWithResult(expanded: String, readOnly: Boolean) {
        if (!readOnly) {
            val result = Intent().putExtra(Intent.EXTRA_PROCESS_TEXT, expanded)
            setResult(RESULT_OK, result)
        } else {
            com.japanglify.app.clipboard.LastResultStore.writeToClipboard(this, expanded)
            Toast.makeText(this, R.string.copied_to_clipboard, Toast.LENGTH_SHORT).show()
            // Read-only hosts do not consume a result; still OK RESULT.
            setResult(RESULT_OK)
        }
        finish()
    }

    companion object {
        private const val TRANSLATE_TIMEOUT_MS = 3_000L
    }
}
