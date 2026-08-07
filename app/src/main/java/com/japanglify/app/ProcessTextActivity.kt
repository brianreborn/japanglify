package com.japanglify.app

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.Toast

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

        finishWithResult(expanded, readOnly)
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
}
