package com.japanglify.app

import android.app.Application
import android.content.ComponentName
import android.content.pm.PackageManager
import com.japanglify.app.data.KuromojiReadingProvider
import com.japanglify.app.data.PreferencesRepository
import com.japanglify.app.domain.JapanglifyEngine
import com.japanglify.app.domain.JapaneseAnalyzer
import com.japanglify.app.translate.Translator

class JapanglifyApp : Application() {

    lateinit var preferences: PreferencesRepository
        private set

    lateinit var engine: JapanglifyEngine
        private set

    /**
     * Lazy, not built in [onCreate] — the translation feature is off by
     * default, and most users are expected to leave it off. Nothing here
     * (including its background thread) is constructed unless a call site's
     * `isTranslationEnabled()` guard actually passes.
     */
    val translator: Translator by lazy { Translator() }

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

        // Load dictionary once; first PROCESS_TEXT may still pay if never opened.
        val provider = runCatching { KuromojiReadingProvider() }.getOrNull()
        val analyzer = JapaneseAnalyzer(provider)
        engine = JapanglifyEngine(analyzer)
    }

    companion object {
        @Volatile
        private var instance: JapanglifyApp? = null

        fun get(): JapanglifyApp =
            instance ?: error("JapanglifyApp not initialized")
    }
}
