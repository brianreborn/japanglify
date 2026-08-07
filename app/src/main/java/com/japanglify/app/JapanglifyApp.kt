package com.japanglify.app

import android.app.Application
import android.content.ComponentName
import android.content.pm.PackageManager
import android.net.http.HttpResponseCache
import com.japanglify.app.data.KuromojiReadingProvider
import com.japanglify.app.data.PreferencesRepository
import com.japanglify.app.dictionary.DictionaryBootstrap
import com.japanglify.app.domain.JapanglifyEngine
import com.japanglify.app.domain.JapaneseAnalyzer
import java.io.File

class JapanglifyApp : Application() {

    lateinit var preferences: PreferencesRepository
        private set

    lateinit var engine: JapanglifyEngine
        private set

    private var readingProvider: JapaneseAnalyzer.ReadingProvider? = null

    override fun onCreate() {
        super.onCreate()
        instance = this
        preferences = PreferencesRepository(this)

        // Keep PROCESS_TEXT entry point enabled after OEM “optimize” / disable.
        runCatching {
            packageManager.setComponentEnabledSetting(
                ComponentName(this, ProcessTextActivity::class.java),
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                PackageManager.DONT_KILL_APP
            )
        }

        // Standard platform HTTP response cache -- transparently honors
        // whatever ETag/Last-Modified/Cache-Control a dictionary source's
        // server actually sends (verified live: CLDR's raw.githubusercontent.com
        // URL sends a real etag + cache-control, so a re-download of unchanged
        // data becomes a cheap conditional request instead of re-fetching the
        // whole file) via HttpURLConnection/HttpsURLConnection process-wide --
        // no changes needed in DictionaryDownloadManager/EmojiDownloadManager
        // themselves. A version bump on the source still gets picked up
        // normally: this only skips re-transferring bytes the server itself
        // confirms are unchanged, it never serves stale data past what the
        // server's own cache-control says is fresh.
        runCatching {
            HttpResponseCache.install(File(cacheDir, "http_cache"), HTTP_CACHE_SIZE_BYTES)
        }

        // Load dictionary once; first PROCESS_TEXT may still pay if never opened.
        readingProvider = runCatching { KuromojiReadingProvider() }.getOrNull()
        rebuildEngine()
    }

    /**
     * Re-resolves [DictionaryBootstrap.createGlossAnnotator]/
     * [DictionaryBootstrap.createEmojiAnnotator] and rebuilds [engine]
     * around them. `onCreate` only runs once per process, but a download
     * can finish *while the app is already running* (that's the whole
     * point of running it in a foreground service rather than tying it to
     * any one screen's lifecycle) -- without calling this again, a
     * just-downloaded dictionary/emoji map would silently do nothing until
     * the user force-kills and reopens the app. [SettingsFragment] calls
     * this right after a download reaches READY, and after a delete.
     */
    fun rebuildEngine() {
        val glossAnnotator = runCatching { DictionaryBootstrap.createGlossAnnotator(this) }.getOrNull()
        val emojiAnnotator = runCatching { DictionaryBootstrap.createEmojiAnnotator(this) }.getOrNull()
        val analyzer = JapaneseAnalyzer(readingProvider, glossAnnotator, emojiAnnotator)
        engine = JapanglifyEngine(analyzer)
    }

    companion object {
        @Volatile
        private var instance: JapanglifyApp? = null

        private const val HTTP_CACHE_SIZE_BYTES = 20L * 1024 * 1024

        fun get(): JapanglifyApp =
            instance ?: error("JapanglifyApp not initialized")
    }
}
