package com.japanglify.app.dictionary

import android.content.Context
import com.japanglify.app.data.PreferencesRepository
import com.japanglify.app.domain.dictionary.DictionarySources
import com.japanglify.app.domain.dictionary.GlossAnnotator
import com.japanglify.app.domain.emoji.EmojiAnnotator

/**
 * Release-build implementation of the debug/release-split `DictionaryBootstrap`
 * (same package + file name, defined once per build type — never in `main` —
 * so Android Gradle Plugin's `main ∪ <buildType>` source-set merge picks
 * exactly one implementation per variant with no duplicate-class conflict).
 *
 * Constructs the live [GlossAnnotator]/[EmojiAnnotator] against the user's
 * actually-downloaded data, or null when nothing is READY yet -- unlike the
 * debug build's counterpart, there's no seed-data fallback here.
 */
object DictionaryBootstrap {
    fun createGlossAnnotator(context: Context): GlossAnnotator? {
        val prefs = PreferencesRepository(context)
        val sourceId = prefs.selectedDictionarySourceId()
        if (prefs.dictionaryStatus(sourceId) != DictionaryDownloadStatus.READY) return null
        return GlossAnnotator(SqliteDictionaryProvider(context, sourceId))
    }

    fun createEmojiAnnotator(context: Context): EmojiAnnotator? {
        val prefs = PreferencesRepository(context)
        val sourceId = DictionarySources.CLDR_EMOJI.id
        if (prefs.dictionaryStatus(sourceId) != DictionaryDownloadStatus.READY) return null
        return EmojiAnnotator(SqliteEmojiProvider(context, sourceId))
    }
}
