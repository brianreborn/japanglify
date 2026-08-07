package com.japanglify.app.debug

import android.app.Activity
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.TextView
import com.japanglify.app.data.PreferencesRepository
import com.japanglify.app.dictionary.DictionaryDownloadService
import com.japanglify.app.dictionary.DictionaryDownloadStatus
import com.japanglify.app.dictionary.SqliteDictionaryProvider
import com.japanglify.app.domain.dictionary.DictionarySources

/**
 * Debug-build-only seam for live-testing [DictionaryDownloadService] end to
 * end before Phase 6's Settings UI exists. Launch with:
 *   adb shell am start -n com.japanglify.app/com.japanglify.app.debug.DictionaryDownloadTestActivity
 *
 * Deliberately stays resumed for the whole download, polling the persisted
 * status every 2s -- confirmed necessary by live testing on this device: a
 * foreground service *alone*, once this activity finished immediately and
 * left the app with zero visible window, still got reaped ("Stopping
 * service due to app idle") after ~74s despite `startForeground()` having
 * been called correctly. A screen staying open during download is also
 * just what Phase 6's real Settings UI will look like (a download-status
 * card the user has open), not an artifact special-cased for this harness.
 *
 * Never present in a release build: lives under `app/src/debug/`.
 */
class DictionaryDownloadTestActivity : Activity() {

    private val handler = Handler(Looper.getMainLooper())
    private lateinit var textView: TextView
    private lateinit var prefs: PreferencesRepository
    private val sourceId = DictionarySources.JMDICT_ENGLISH.id

    private val poll = object : Runnable {
        override fun run() {
            val status = prefs.dictionaryStatus(sourceId)
            textView.text = "status=$status error=${prefs.dictionaryErrorMessage(sourceId)}"
            if (status == DictionaryDownloadStatus.READY) {
                val provider = SqliteDictionaryProvider(applicationContext, sourceId)
                val lookups = SPOT_CHECK_WORDS.joinToString("\n") { "lookup($it) = ${provider.lookup(it)}" }
                textView.text = "${textView.text}\n$lookups"
            }
            if (status != DictionaryDownloadStatus.READY && status != DictionaryDownloadStatus.FAILED) {
                handler.postDelayed(this, POLL_INTERVAL_MS)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = PreferencesRepository(this)
        textView = TextView(this).apply {
            text = "starting…"
            textSize = 14f
            setPadding(32, 32, 32, 32)
        }
        setContentView(textView)
        DictionaryDownloadService.start(this, DictionarySources.JMDICT_ENGLISH)
        handler.post(poll)
    }

    override fun onDestroy() {
        handler.removeCallbacks(poll)
        super.onDestroy()
    }

    companion object {
        private const val POLL_INTERVAL_MS = 2000L
        private val SPOT_CHECK_WORDS = listOf("日本語", "勉強", "する", "食べる")
    }
}
