package com.japanglify.app.clipboard

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.japanglify.app.R
import com.japanglify.app.SettingsActivity

object ClipboardNotifications {
    const val CHANNEL_ASSIST = "clipboard_assist"
    const val CHANNEL_RESULT = "clipboard_result"

    const val ID_LISTENING = 1001
    const val ID_RESULT = 1002
    const val ID_TAP_TO_PROCESS = 1003
    const val ID_HOOK_ARMED = 1004

    fun ensureChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ASSIST,
                context.getString(R.string.notif_channel_assist),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = context.getString(R.string.notif_channel_assist_desc)
                setShowBadge(false)
            }
        )
        nm.createNotificationChannel(
            NotificationChannel(
                CHANNEL_RESULT,
                context.getString(R.string.notif_channel_result),
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = context.getString(R.string.notif_channel_result_desc)
            }
        )
    }

    fun listeningNotification(context: Context): Notification {
        ensureChannels(context)
        val open = PendingIntent.getActivity(
            context,
            0,
            Intent(context, SettingsActivity::class.java),
            pendingFlags()
        )
        val stop = PendingIntent.getBroadcast(
            context,
            1,
            Intent(context, ClipboardAssistReceiver::class.java).setAction(
                ClipboardAssistReceiver.ACTION_STOP
            ),
            pendingFlags()
        )
        return NotificationCompat.Builder(context, CHANNEL_ASSIST)
            .setSmallIcon(R.drawable.ic_japanglify_action)
            .setContentTitle(context.getString(R.string.notif_listening_title))
            .setContentText(context.getString(R.string.notif_listening_text))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(open)
            .addAction(0, context.getString(R.string.notif_action_stop), stop)
            .addAction(
                0,
                context.getString(R.string.notif_action_process_now),
                PendingIntent.getActivity(
                    context,
                    2,
                    Intent(context, ProcessClipboardActivity::class.java),
                    pendingFlags()
                )
            )
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    fun showResult(context: Context, result: String) {
        ensureChannels(context)
        LastResultStore.save(context, LastResultStore.lastSource.orEmpty(), result)

        val copyIntent = Intent(context, ClipboardAssistReceiver::class.java).setAction(
            ClipboardAssistReceiver.ACTION_COPY_RESULT
        )
        val copyPi = PendingIntent.getBroadcast(context, 3, copyIntent, pendingFlags())

        val preview = result.replace('\n', ' ').let {
            if (it.length > 180) it.take(177) + "…" else it
        }

        val notification = NotificationCompat.Builder(context, CHANNEL_RESULT)
            .setSmallIcon(R.drawable.ic_japanglify_action)
            .setContentTitle(context.getString(R.string.notif_result_title))
            .setContentText(preview)
            .setStyle(NotificationCompat.BigTextStyle().bigText(result))
            .setAutoCancel(true)
            .setContentIntent(copyPi)
            .addAction(0, context.getString(R.string.notif_action_copy), copyPi)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        notifySafe(context, ID_RESULT, notification)
    }

    fun showTapToProcess(context: Context) {
        ensureChannels(context)
        val processPi = PendingIntent.getActivity(
            context,
            4,
            Intent(context, ProcessClipboardActivity::class.java),
            pendingFlags()
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_RESULT)
            .setSmallIcon(R.drawable.ic_japanglify_action)
            .setContentTitle(context.getString(R.string.notif_tap_process_title))
            .setContentText(context.getString(R.string.notif_tap_process_text))
            .setAutoCancel(true)
            .setContentIntent(processPi)
            .addAction(0, context.getString(R.string.notif_action_process_now), processPi)
            .build()
        notifySafe(context, ID_TAP_TO_PROCESS, notification)
    }

    fun cancelTapToProcess(context: Context) {
        NotificationManagerCompat.from(context).cancel(ID_TAP_TO_PROCESS)
    }

    /** One-shot proof that the Accessibility Copy hook is alive. */
    fun showHookArmed(context: Context) {
        ensureChannels(context)
        val open = PendingIntent.getActivity(
            context,
            10,
            Intent(context, SettingsActivity::class.java),
            pendingFlags()
        )
        val processPi = PendingIntent.getActivity(
            context,
            11,
            Intent(context, ProcessClipboardActivity::class.java),
            pendingFlags()
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ASSIST)
            .setSmallIcon(R.drawable.ic_japanglify_action)
            .setContentTitle(context.getString(R.string.notif_hook_armed_title))
            .setContentText(context.getString(R.string.notif_hook_armed_text))
            .setAutoCancel(true)
            .setContentIntent(open)
            .addAction(0, context.getString(R.string.notif_action_process_now), processPi)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
        notifySafe(context, ID_HOOK_ARMED, notification)
    }

    private fun notifySafe(context: Context, id: Int, notification: Notification) {
        try {
            NotificationManagerCompat.from(context).notify(id, notification)
        } catch (_: SecurityException) {
            // POST_NOTIFICATIONS not granted
        }
    }

    private fun pendingFlags(): Int {
        return PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    }
}
