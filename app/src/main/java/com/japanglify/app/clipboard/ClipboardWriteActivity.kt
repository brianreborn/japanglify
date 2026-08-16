package com.japanglify.app.clipboard

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.os.Bundle
import android.widget.Toast
import androidx.core.app.NotificationManagerCompat
import com.japanglify.app.R

/**
 * Performs the actual clipboard write for the "Copy" / "Copy image" result
 * notification actions -- moved out of [ClipboardAssistReceiver] (a headless
 * `BroadcastReceiver`, no window ever) into this brief, transparent,
 * genuinely-focused Activity for the exact reason [ProcessClipboardActivity]
 * already exists: reported live, repeatedly, this session -- tapping "Copy
 * image" from the result notification while another app (observed with X)
 * has focus could leave the clipboard empty/unreadable by the time the user
 * pastes, moments after the system's own "copied" preview chip disappears.
 *
 * [ProcessClipboardActivity]'s doc comment already nails the platform
 * mechanism for the *read* side of this exact class of bug, confirmed live
 * via logcat in an earlier session (`ClipboardService: Denying clipboard
 * access to com.japanglify.app, application is not in focus`) -- a
 * `ClipData` carrying a `content://` URI additionally depends on the
 * *receiving* app being able to resolve a URI permission grant through
 * `ClipboardService`, and a write issued from a component with no window at
 * all is the least favorable state to be doing that from. Rather than
 * depend on capturing the identical log line for the *write* side (blocked
 * this session by not wanting to keep prodding a real, in-use device to
 * force a timing-sensitive repro), this applies the same fix pattern
 * unconditionally: doing the write from a real focused window is strictly
 * safer than doing it from a receiver, and it's the one thing this project
 * has already verified actually resolves this class of platform quirk.
 *
 * `onWindowFocusChanged`, not `onCreate` -- same reason as
 * [ProcessClipboardActivity]: a just-created Activity's window doesn't have
 * focus yet.
 */
class ClipboardWriteActivity : Activity() {

    private var handled = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // No content view -- Theme.Japanglify.Transparent keeps this
        // invisible for its brief lifetime, matching ProcessClipboardActivity.
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (!hasFocus || handled) return
        handled = true

        when (intent?.action) {
            ClipboardAssistReceiver.ACTION_COPY_RESULT -> copyResult()
            ClipboardAssistReceiver.ACTION_COPY_IMAGE -> copyImage()
        }
        finish()
    }

    private fun copyResult() {
        val result = LastResultStore.load(this)
        if (result.isNullOrEmpty()) {
            Toast.makeText(this, R.string.clipboard_assist_no_result, Toast.LENGTH_SHORT).show()
            return
        }
        // Suppress + label + ring buffer — must happen before setPrimaryClip
        LastResultStore.writeToClipboard(this, result)
        NotificationManagerCompat.from(this).cancel(ClipboardNotifications.ID_RESULT)
        Toast.makeText(this, R.string.notif_copied_ready_to_paste, Toast.LENGTH_LONG).show()
    }

    private fun copyImage() {
        // load(), not the raw property: the process may have been
        // recycled since the result was shown, which resets in-memory
        // state — load() falls back to SharedPreferences and also
        // repopulates lastSource as a side effect.
        LastResultStore.load(this)
        val source = LastResultStore.lastSource
        if (source.isNullOrEmpty()) {
            Toast.makeText(this, R.string.clipboard_assist_no_result, Toast.LENGTH_SHORT).show()
            return
        }
        // Usually already finished by now -- see ClipboardImageRenderCache's
        // doc comment: prerender() was kicked off back when the textual
        // result first became ready, well before the user could have
        // noticed the notification and tapped this action.
        val uri = runCatching { ClipboardImageRenderCache.await(this, source) }.getOrElse {
            Toast.makeText(this, R.string.error_processing_generic, Toast.LENGTH_SHORT).show()
            return
        }
        // Suppress + ring-buffer the URI text form so our own clipboard write
        // is never mistaken for new user text by the Copy hook.
        LastResultStore.beginOutgoingWrite(uri.toString())
        val cm = getSystemService(ClipboardManager::class.java)
        if (cm == null) {
            Toast.makeText(this, R.string.error_processing_generic, Toast.LENGTH_SHORT).show()
            return
        }
        cm.setPrimaryClip(ClipData.newUri(contentResolver, LastResultStore.CLIP_LABEL, uri))
        NotificationManagerCompat.from(this).cancel(ClipboardNotifications.ID_RESULT)
        Toast.makeText(this, R.string.notif_copied_image_ready, Toast.LENGTH_LONG).show()
    }
}
