package com.japanglify.app.clipboard

import android.app.Activity
import android.widget.Toast
import com.japanglify.app.R

/**
 * Brief focused activity so Android allows clipboard reads when the
 * accessibility path still cannot see the clip (rare OEM cases).
 *
 * The read must happen in [onWindowFocusChanged], not [onCreate]: Android's
 * ClipboardService gates `getPrimaryClip()` on the window *currently* having
 * input focus, and a just-created Activity's window doesn't have it yet —
 * confirmed live via logcat (`ActivityTaskManager: START ... isFocused=false`
 * immediately followed by `ClipboardService: Denying clipboard access to
 * com.japanglify.app, application is not in focus`). Reading in onCreate()
 * defeated the entire point of this activity, failing almost every time it
 * was launched from a notification action.
 */
class ProcessClipboardActivity : Activity() {

    private var handled = false

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (!hasFocus || handled) return
        handled = true

        val outcome = ClipboardProcessor.processClipboardIfNew(this)
        when (outcome) {
            ClipboardProcessor.ProcessOutcome.SUCCESS -> {
                Toast.makeText(this, R.string.notif_result_ready, Toast.LENGTH_SHORT).show()
            }
            ClipboardProcessor.ProcessOutcome.EMPTY_OR_UNREADABLE -> {
                Toast.makeText(this, R.string.clipboard_empty, Toast.LENGTH_LONG).show()
            }
            ClipboardProcessor.ProcessOutcome.SELF_WRITE -> {
                Toast.makeText(this, R.string.clipboard_assist_skip_self, Toast.LENGTH_SHORT).show()
            }
            ClipboardProcessor.ProcessOutcome.DUPLICATE -> {
                val existing = LastResultStore.load(this)
                if (!existing.isNullOrEmpty()) {
                    ClipboardNotifications.showResult(this, existing)
                    Toast.makeText(this, R.string.notif_result_ready, Toast.LENGTH_SHORT).show()
                }
            }
            ClipboardProcessor.ProcessOutcome.DISABLED -> {
                Toast.makeText(this, R.string.clipboard_assist_disabled_hint, Toast.LENGTH_LONG).show()
            }
            ClipboardProcessor.ProcessOutcome.NO_JAPANESE -> {
                Toast.makeText(this, R.string.clipboard_no_japanese, Toast.LENGTH_SHORT).show()
            }
            ClipboardProcessor.ProcessOutcome.TOO_LONG,
            ClipboardProcessor.ProcessOutcome.ERROR -> {
                Toast.makeText(this, R.string.error_processing_generic, Toast.LENGTH_SHORT).show()
            }
        }
        finish()
    }
}
