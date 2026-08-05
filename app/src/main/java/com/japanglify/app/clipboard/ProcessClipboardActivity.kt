package com.japanglify.app.clipboard

import android.app.Activity
import android.os.Bundle
import android.widget.Toast
import com.japanglify.app.R

/**
 * Brief focused activity so Android allows clipboard reads when the
 * accessibility path still cannot see the clip (rare OEM cases).
 */
class ProcessClipboardActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

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
            ClipboardProcessor.ProcessOutcome.TOO_LONG,
            ClipboardProcessor.ProcessOutcome.ERROR -> {
                Toast.makeText(this, R.string.error_processing_generic, Toast.LENGTH_SHORT).show()
            }
        }
        finish()
    }
}
