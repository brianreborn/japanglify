package com.japanglify.app.dictionary

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.japanglify.app.R
import com.japanglify.app.domain.dictionary.DictionarySource

/** Progress notification for [DictionaryDownloadService], mirroring [com.japanglify.app.clipboard.ClipboardNotifications]'s channel-setup pattern. */
object DictionaryDownloadNotifications {
    const val CHANNEL_DICTIONARY = "dictionary_download"
    const val ID_PROGRESS = 2001

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(
                CHANNEL_DICTIONARY,
                context.getString(R.string.notif_channel_dictionary),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = context.getString(R.string.notif_channel_dictionary_desc)
                setShowBadge(false)
            }
        )
    }

    fun forProgress(context: Context, source: DictionarySource, progress: DictionaryDownloadProgress?): Notification {
        ensureChannel(context)
        val builder = NotificationCompat.Builder(context, CHANNEL_DICTIONARY)
            .setSmallIcon(R.drawable.ic_japanglify_action)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)

        when (progress?.status) {
            null, DictionaryDownloadStatus.DOWNLOADING -> builder
                .setContentTitle(context.getString(R.string.notif_dictionary_downloading_title, source.displayName))
                .setOngoing(true)
                .setProgress(100, progress?.percent ?: 0, progress?.percent == null)

            DictionaryDownloadStatus.PARSING -> builder
                .setContentTitle(context.getString(R.string.notif_dictionary_parsing_title, source.displayName))
                .setContentText(context.getString(R.string.notif_dictionary_parsing_text, progress.wordsImported))
                .setOngoing(true)
                .setProgress(0, 0, true)

            DictionaryDownloadStatus.READY -> builder
                .setContentTitle(context.getString(R.string.notif_dictionary_ready_title, source.displayName))
                .setOngoing(false)
                .setAutoCancel(true)

            DictionaryDownloadStatus.FAILED -> builder
                .setContentTitle(context.getString(R.string.notif_dictionary_failed_title, source.displayName))
                .setContentText(progress.errorMessage)
                .setOngoing(false)
                .setAutoCancel(true)

            // Only reached with a non-null progress -- i.e. a user cancel
            // came back through (see DictionaryDownloadService.onProgress),
            // never the pre-download state, which is the `null` branch above.
            DictionaryDownloadStatus.NOT_DOWNLOADED -> builder
                .setContentTitle(context.getString(R.string.notif_dictionary_cancelled_title, source.displayName))
                .setOngoing(false)
                .setAutoCancel(true)
        }
        return builder.build()
    }

    fun update(context: Context, source: DictionarySource, progress: DictionaryDownloadProgress) {
        try {
            NotificationManagerCompat.from(context).notify(ID_PROGRESS, forProgress(context, source, progress))
        } catch (_: SecurityException) {
            // POST_NOTIFICATIONS not granted -- the download itself still proceeds.
        }
    }
}
