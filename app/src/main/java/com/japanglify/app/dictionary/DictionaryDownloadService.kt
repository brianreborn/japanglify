package com.japanglify.app.dictionary

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.ServiceCompat
import com.japanglify.app.domain.dictionary.DictionarySource
import com.japanglify.app.domain.dictionary.DictionarySourceFormat
import com.japanglify.app.domain.dictionary.DictionarySources

/**
 * Runs the download + import for any [DictionarySource] inside a foreground
 * service rather than a plain background thread -- dispatches to
 * [DictionaryDownloadManager], [EmojiDownloadManager], or
 * [WordNetDownloadManager] based on [DictionarySource.format]; they differ
 * only in how they parse and store their source data (JMdict JSON vs CLDR
 * XML vs WordNet Prolog facts), never in the
 * lifecycle/notification/status-persistence ceremony this service owns, so
 * that part stays shared across every source rather than duplicated per
 * format.
 *
 * This isn't precautionary — it's a direct fix for a real failure observed
 * live: a bare background-thread version of this same work got killed
 * mid-import (confirmed via `adb logcat`: `Killing ... excessive cpu 261560
 * during 300001 ... limit 25`) once its hosting activity's task lost
 * foreground focus and the process dropped into Android's "cached"
 * importance tier. A multi-minute, CPU-heavy one-time download+import is
 * exactly the case `dataSync`-type foreground services exist for; a plain
 * thread has no protection from the background-CPU watchdog once its
 * process isn't foreground/cached-exempt.
 */
class DictionaryDownloadService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val source = DictionarySources.byId(intent?.getStringExtra(EXTRA_SOURCE_ID))
        if (source == null) {
            stopSelf(startId)
            return START_NOT_STICKY
        }

        val notification = DictionaryDownloadNotifications.forProgress(this, source, null)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceCompat.startForeground(
                this,
                DictionaryDownloadNotifications.ID_PROGRESS,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            @Suppress("DEPRECATION")
            startForeground(DictionaryDownloadNotifications.ID_PROGRESS, notification)
        }

        var lastNotifiedAt = 0L
        val onProgress: (DictionaryDownloadProgress) -> Unit = { progress ->
            // NOT_DOWNLOADED here means a cancel came back through, not that
            // the download never started -- see DictionaryDownloadManager's
            // (and its siblings') DownloadCancelledException handling, which
            // deliberately reuses this status rather than adding a new one.
            val terminal = progress.status == DictionaryDownloadStatus.READY ||
                progress.status == DictionaryDownloadStatus.FAILED ||
                progress.status == DictionaryDownloadStatus.NOT_DOWNLOADED
            val now = android.os.SystemClock.elapsedRealtime()
            // Android silently drops notification updates past ~5/sec per
            // app (confirmed live: "Shedding notify ... rate limit (5.0)
            // exceeded" spam once the faster buffered importer made
            // wordsImported ticks arrive far more than 5x/sec) -- gate
            // updates client-side instead of relying on the OS to do it,
            // always allowing the final terminal state through.
            if (terminal || now - lastNotifiedAt >= NOTIFY_INTERVAL_MS) {
                lastNotifiedAt = now
                DictionaryDownloadNotifications.update(this, source, progress)
            }
            if (terminal) {
                stopForeground(STOP_FOREGROUND_DETACH)
                stopSelf(startId)
            }
        }
        when (source.format) {
            DictionarySourceFormat.JMDICT_JSON ->
                DictionaryDownloadManager(applicationContext).download(source, onProgress)
            DictionarySourceFormat.CLDR_EMOJI_XML ->
                EmojiDownloadManager(applicationContext).download(source, onProgress)
            DictionarySourceFormat.WORDNET_PROLOG ->
                WordNetDownloadManager(applicationContext).download(source, onProgress)
        }

        return START_NOT_STICKY
    }

    companion object {
        const val EXTRA_SOURCE_ID = "source_id"
        private const val NOTIFY_INTERVAL_MS = 400L

        fun start(context: Context, source: DictionarySource) {
            val intent = Intent(context, DictionaryDownloadService::class.java)
                .putExtra(EXTRA_SOURCE_ID, source.id)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        /**
         * Doesn't need to reach this Service instance at all -- the
         * cancellation flag/connection registry
         * ([DictionaryDownloadCancellation]) is a plain in-process singleton,
         * and this app never runs the service in a separate `:process` (see
         * AndroidManifest), so setting it here is visible to whichever
         * executor thread the running [DictionaryDownloadManager]/
         * [EmojiDownloadManager]/[WordNetDownloadManager] is blocked in.
         */
        fun cancel(source: DictionarySource) {
            DictionaryDownloadCancellation.cancel(source.id)
        }
    }
}
