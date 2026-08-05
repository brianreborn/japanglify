package com.japanglify.app.clipboard

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.core.app.NotificationManagerCompat
import com.japanglify.app.R
import com.japanglify.app.data.PreferencesRepository

/**
 * Handles notification actions for clipboard assist (stop / copy result).
 * Copy always goes through [LastResultStore.writeToClipboard] so listeners ignore it.
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

            ACTION_COPY_RESULT -> {
                val result = LastResultStore.load(context)
                if (result.isNullOrEmpty()) {
                    Toast.makeText(
                        context,
                        R.string.clipboard_assist_no_result,
                        Toast.LENGTH_SHORT
                    ).show()
                    return
                }
                // Suppress + label + ring buffer — must happen before setPrimaryClip
                LastResultStore.writeToClipboard(context, result)
                NotificationManagerCompat.from(context)
                    .cancel(ClipboardNotifications.ID_RESULT)
                Toast.makeText(
                    context,
                    R.string.notif_copied_ready_to_paste,
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    companion object {
        const val ACTION_STOP = "com.japanglify.app.action.STOP_CLIPBOARD_ASSIST"
        const val ACTION_COPY_RESULT = "com.japanglify.app.action.COPY_JAPANGLIFY_RESULT"
    }
}
