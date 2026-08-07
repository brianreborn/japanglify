package com.japanglify.app.dictionary

import android.content.Context
import com.japanglify.app.data.PreferencesRepository
import com.japanglify.app.domain.dictionary.DictionarySources
import com.japanglify.app.domain.dictionary.GlossAnnotator
import com.japanglify.app.domain.emoji.EmojiAnnotator

/**
 * Debug-build implementation of the debug/release-split `DictionaryBootstrap`
 * (same package + file name, defined once per build type — deliberately
 * *not* also in `main`, since Kotlin source files don't support the kind of
 * override that manifest/resource merging does; two same-named classes in
 * `main` and `debug` would just be a duplicate-class compile error for the
 * debug variant. Android Gradle Plugin instead merges `main ∪ <buildType>`
 * per variant, so defining this once in `debug/` and once in `release/`
 * — see that file's doc comment — gives each variant exactly one
 * implementation with zero conflict).
 *
 * Prefers a real downloaded dictionary (per the Settings screen's source
 * picker) when one is actually READY; only falls back to a tiny (~20-entry)
 * fixed seed dictionary, seeded into a dedicated debug-only SQLite file on
 * first use, when nothing has been downloaded yet -- so word/particle
 * glosses stay live-device-testable (Try-It card, `include_glosses` on)
 * with zero setup, without that fallback silently masking a real downloaded
 * dictionary once one exists. Never present in a release build.
 */
object DictionaryBootstrap {
    private const val DEBUG_SOURCE_ID = "debug_seed"
    private const val DEBUG_EMOJI_SOURCE_ID = "debug_seed_emoji"

    fun createGlossAnnotator(context: Context): GlossAnnotator {
        val prefs = PreferencesRepository(context)
        val sourceId = prefs.selectedDictionarySourceId()
        if (prefs.dictionaryStatus(sourceId) == DictionaryDownloadStatus.READY) {
            return GlossAnnotator(SqliteDictionaryProvider(context, sourceId))
        }
        DebugSeedDictionary.ensureSeeded(context)
        return GlossAnnotator(SqliteDictionaryProvider(context, DEBUG_SOURCE_ID))
    }

    /** Same real-download-first, debug-seed-fallback shape as [createGlossAnnotator]. */
    fun createEmojiAnnotator(context: Context): EmojiAnnotator {
        val prefs = PreferencesRepository(context)
        val sourceId = DictionarySources.CLDR_EMOJI.id
        if (prefs.dictionaryStatus(sourceId) == DictionaryDownloadStatus.READY) {
            return EmojiAnnotator(SqliteEmojiProvider(context, sourceId))
        }
        DebugSeedEmoji.ensureSeeded(context)
        return EmojiAnnotator(SqliteEmojiProvider(context, DEBUG_EMOJI_SOURCE_ID))
    }
}
