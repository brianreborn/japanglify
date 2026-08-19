package com.japanglify.app.clipboard

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
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
            ClipboardAssistReceiver.ACTION_COPY_RESULT -> {
                copyResult()
                finish()
            }
            ClipboardAssistReceiver.ACTION_COPY_IMAGE -> {
                // Critical: finish this transparent Activity *right now*,
                // before any potentially long image render. Keeping it alive
                // (even transparent) on the task stack while renderInterlinearToBitmap
                // runs is what causes "locking up when going back to the app screen".
                // The actual render + clipboard write continues from the app context
                // on a background worker.
                startImageCopyAsync()
                finish()
            }
            else -> finish()
        }
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

    private fun startImageCopyAsync() {
        // load(), not the raw property: the process may have been
        // recycled since the result was shown, which resets in-memory
        // state — load() falls back to SharedPreferences and also
        // repopulates lastSource as a side effect.
        LastResultStore.load(this)
        val source = LastResultStore.lastSource
        if (source.isNullOrEmpty()) {
            Toast.makeText(this, R.string.clipboard_assist_no_result, Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        // Start the potentially very long image render on a background thread
        // *immediately*. The render (full interlinear layout + measurement +
        // draw + PNG) for a long or complex selection can take many seconds.
        // This must never block the main thread.
        val appCtx = applicationContext
        ClipboardImageRenderCache.prerender(appCtx, source)

        // Finish this transparent Activity *right now*, before we do any
        // blocking or long-running work. Leaving it on the task stack
        // (even though it is transparent) while the image is being generated
        // is exactly what causes the observed symptom: the app "locks up"
        // when the user tries to go back to the previous screen or to
        // Japanglify itself.
        //
        // The render + clipboard write continue from the application context
        // on a background executor. FileProvider URIs remain usable for
        // clipboard grants after the originating Activity has finished.
        finish()

        // Off the main thread: wait for the render (if not already done) and
        // perform the clipboard write. Only final user feedback is posted
        // back to the main looper.
        ClipboardImageRenderCache.fullRenderExecutorForWrite.submit {
            // Long image work must not starve the rest of the app.
            android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_BACKGROUND)

            val uri = runCatching {
                ClipboardImageRenderCache.awaitForWrite(appCtx, source)
            }.getOrNull()

            mainHandler.post {
                if (uri == null) {
                    Toast.makeText(appCtx, R.string.error_processing_generic, Toast.LENGTH_SHORT).show()
                    return@post
                }

                LastResultStore.beginOutgoingWrite(uri.toString())
                val cm = appCtx.getSystemService(ClipboardManager::class.java)
                if (cm != null) {
                    // Use the application context's contentResolver so the
                    // ClipData stays valid after this Activity is gone.
                    cm.setPrimaryClip(
                        ClipData.newUri(appCtx.contentResolver, LastResultStore.CLIP_LABEL, uri)
                    )
                    NotificationManagerCompat.from(appCtx)
                        .cancel(ClipboardNotifications.ID_RESULT)
                    Toast.makeText(appCtx, R.string.notif_copied_image_ready, Toast.LENGTH_LONG).show()
                } else {
                    Toast.makeText(appCtx, R.string.error_processing_generic, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private val mainHandler = Handler(Looper.getMainLooper())
}
