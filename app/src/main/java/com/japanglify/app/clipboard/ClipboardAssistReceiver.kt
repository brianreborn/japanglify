package com.japanglify.app.clipboard

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.core.app.NotificationManagerCompat
import com.japanglify.app.R
import com.japanglify.app.data.PreferencesRepository

/**
 * Handles notification actions for clipboard assist that don't themselves
 * touch the clipboard (stop / replace-field / translate / pause toggle).
 * The two that DO write to the clipboard -- ACTION_COPY_RESULT and
 * ACTION_COPY_IMAGE -- are handled by [ClipboardWriteActivity] instead; see
 * its doc comment for why.
 */
class ClipboardAssistReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        when (intent?.action) {
            ACTION_STOP -> {
                androidx.preference.PreferenceManager
                    .getDefaultSharedPreferences(context)
                    .edit()
                    .putBoolean(PreferencesRepository.KEY_CLIPBOARD_ASSIST, false)
                    .apply()
                ClipboardAssistService.stop(context)
                NotificationManagerCompat.from(context)
                    .cancel(ClipboardNotifications.ID_LISTENING)
                Toast.makeText(
                    context,
                    R.string.clipboard_assist_stopped,
                    Toast.LENGTH_SHORT
                ).show()
            }

            // ACTION_COPY_RESULT / ACTION_COPY_IMAGE are handled by
            // ClipboardWriteActivity, not here -- see its doc comment for
            // why an actual clipboard write needs a focused window rather
            // than this headless receiver.

            ACTION_REPLACE_FIELD -> {
                val result = LastResultStore.load(context)
                val service = JapanglifyAccessibilityService.instance
                if (result.isNullOrEmpty() || service == null) {
                    Toast.makeText(
                        context,
                        R.string.clipboard_assist_no_result,
                        Toast.LENGTH_SHORT
                    ).show()
                    return
                }
                // Re-query the live focused node rather than trusting anything
                // captured at Copy time — see replaceFocusedField's doc.
                val ok = service.replaceFocusedField(result)
                if (ok) {
                    NotificationManagerCompat.from(context)
                        .cancel(ClipboardNotifications.ID_RESULT)
                    Toast.makeText(context, R.string.notif_field_replaced, Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(
                        context,
                        R.string.notif_field_replace_failed,
                        Toast.LENGTH_LONG
                    ).show()
                }
            }

            ACTION_TOGGLE_COPY_PAUSE -> {
                val prefs = androidx.preference.PreferenceManager.getDefaultSharedPreferences(context)
                val paused = !prefs.getBoolean(PreferencesRepository.KEY_COPY_HOOK_PAUSED, false)
                prefs.edit().putBoolean(PreferencesRepository.KEY_COPY_HOOK_PAUSED, paused).apply()
                ClipboardNotifications.showHookArmed(context)
            }

            ACTION_TRANSLATE -> {
                val source = LastResultStore.lastSource?.trim().orEmpty()
                val result = LastResultStore.load(context)?.trim().orEmpty()
                val textToTranslate = source.ifEmpty { result }
                if (textToTranslate.isNotEmpty()) {
                    NotificationManagerCompat.from(context)
                        .cancel(ClipboardNotifications.ID_RESULT)
                    TranslateHelper.launchGoogleTranslate(context, textToTranslate)
                } else {
                    Toast.makeText(
                        context,
                        R.string.clipboard_assist_no_result,
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    companion object {
        const val ACTION_STOP = "com.japanglify.app.action.STOP_CLIPBOARD_ASSIST"
        const val ACTION_COPY_RESULT = "com.japanglify.app.action.COPY_JAPANGLIFY_RESULT"
        const val ACTION_COPY_IMAGE = "com.japanglify.app.action.COPY_JAPANGLIFY_IMAGE"
        const val ACTION_REPLACE_FIELD = "com.japanglify.app.action.REPLACE_FIELD"
        const val ACTION_TRANSLATE = "com.japanglify.app.action.TRANSLATE"
        const val ACTION_TOGGLE_COPY_PAUSE = "com.japanglify.app.action.TOGGLE_COPY_PAUSE"
    }
}
