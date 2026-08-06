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

        val copyPi = PendingIntent.getBroadcast(
            context, 3,
            Intent(context, ClipboardAssistReceiver::class.java)
                .setAction(ClipboardAssistReceiver.ACTION_COPY_RESULT),
            pendingFlags()
        )
        val copyImagePi = PendingIntent.getBroadcast(
            context, 6,
            Intent(context, ClipboardAssistReceiver::class.java)
                .setAction(ClipboardAssistReceiver.ACTION_COPY_IMAGE),
            pendingFlags()
        )
        val translatePi = PendingIntent.getBroadcast(
            context, 5,
            Intent(context, ClipboardAssistReceiver::class.java)
                .setAction(ClipboardAssistReceiver.ACTION_TRANSLATE),
            pendingFlags()
        )
        val replaceFieldPi = PendingIntent.getBroadcast(
            context, 7,
            Intent(context, ClipboardAssistReceiver::class.java)
                .setAction(ClipboardAssistReceiver.ACTION_REPLACE_FIELD),
            pendingFlags()
        )

        val preview = result.replace('\n', ' ').let {
            if (it.length > 180) it.take(177) + "…" else it
        }

        // Hosts that mangle mixed CJK/Latin plain-text alignment get the image
        // option prioritized; everyone else gets plain-text copy prioritized.
        val imageFirst = LastResultStore.hostPrefersImage()
        val fieldReplaceAvailable = LastResultStore.lastHostFieldEditable &&
            JapanglifyAccessibilityService.isRunning()

        data class Action(val label: Int, val icon: Int, val pi: PendingIntent)

        // Android's default (collapsed) notification view only reliably shows
        // 3 actions — a 4th is silently dropped rather than pushed into an
        // overflow menu, so we must pick 3, not just order by preference.
        // Ordered by usefulness: replacing the field (when we can) needs no
        // manual paste at all, then whichever copy mechanism suits this host,
        // then Translate. The *other* copy format is dropped once field-replace
        // is offered — rarely needed once there's a one-tap in-place option,
        // and keeping it would be what silently bumps Translate off the list.
        val actions = buildList {
            if (fieldReplaceAvailable) {
                add(Action(R.string.notif_action_replace_field, R.drawable.ic_action_replace_field, replaceFieldPi))
                add(
                    if (imageFirst) Action(R.string.notif_action_copy_image, R.drawable.ic_action_copy_image, copyImagePi)
                    else Action(R.string.notif_action_copy, R.drawable.ic_action_copy_text, copyPi)
                )
            } else if (imageFirst) {
                add(Action(R.string.notif_action_copy_image, R.drawable.ic_action_copy_image, copyImagePi))
                add(Action(R.string.notif_action_copy, R.drawable.ic_action_copy_text, copyPi))
            } else {
                add(Action(R.string.notif_action_copy, R.drawable.ic_action_copy_text, copyPi))
                add(Action(R.string.notif_action_copy_image, R.drawable.ic_action_copy_image, copyImagePi))
            }
            add(Action(R.string.notif_action_translate, R.drawable.ic_action_translate, translatePi))
        }.take(3)

        // Tapping the notification body always does the safe, reversible thing
        // (copy) rather than the destructive field-replace, even when that
        // action is offered as an explicit button.
        val defaultTapPi = if (imageFirst) copyImagePi else copyPi

        val builder = NotificationCompat.Builder(context, CHANNEL_RESULT)
            .setSmallIcon(R.drawable.ic_japanglify_action)
            .setContentTitle(context.getString(R.string.notif_result_title))
            .setContentText(preview)
            .setStyle(NotificationCompat.BigTextStyle().bigText(result))
            .setAutoCancel(true)
            .setContentIntent(defaultTapPi)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)

        for (action in actions) {
            builder.addAction(
                NotificationCompat.Action.Builder(
                    androidx.core.graphics.drawable.IconCompat.createWithResource(context, action.icon),
                    context.getString(action.label),
                    action.pi
                ).build()
            )
        }

        notifySafe(context, ID_RESULT, builder.build())
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

    /**
     * Status + quick-control notification for the Accessibility Copy hook.
     * Re-shown (same ID) whenever a toggle action fires so it always reflects
     * current state without needing the Settings screen.
     */
    fun showHookArmed(context: Context) {
        ensureChannels(context)
        val prefs = androidx.preference.PreferenceManager.getDefaultSharedPreferences(context)
        val paused = prefs.getBoolean(
            com.japanglify.app.data.PreferencesRepository.KEY_COPY_HOOK_PAUSED, false
        )

        val open = PendingIntent.getActivity(
            context, 10, Intent(context, SettingsActivity::class.java), pendingFlags()
        )
        val processPi = PendingIntent.getActivity(
            context, 11, Intent(context, ProcessClipboardActivity::class.java), pendingFlags()
        )
        val togglePausePi = PendingIntent.getBroadcast(
            context, 12,
            Intent(context, ClipboardAssistReceiver::class.java)
                .setAction(ClipboardAssistReceiver.ACTION_TOGGLE_COPY_PAUSE),
            pendingFlags()
        )

        val title = context.getString(
            if (paused) R.string.notif_hook_paused_title else R.string.notif_hook_armed_title
        )
        val text = context.getString(
            if (paused) R.string.notif_hook_paused_text else R.string.notif_hook_armed_text
        )
        val pauseLabel = context.getString(
            if (paused) R.string.notif_action_resume else R.string.notif_action_pause
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ASSIST)
            .setSmallIcon(R.drawable.ic_japanglify_action)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setAutoCancel(false)
            .setContentIntent(open)
            .addAction(0, pauseLabel, togglePausePi)
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
