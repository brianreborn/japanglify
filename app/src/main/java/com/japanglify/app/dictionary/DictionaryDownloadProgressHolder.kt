package com.japanglify.app.dictionary

/**
 * In-process, main-thread-only holder for the latest [DictionaryDownloadProgress]
 * per [com.japanglify.app.domain.dictionary.DictionarySource.id] -- lets
 * SettingsFragment's poll (see its own doc comment for why it polls rather
 * than getting a direct callback) show a live percent/word-count instead of
 * only the coarse status [PreferencesRepository] persists. Not persisted
 * itself: fine-grained progress has no meaning to restore after the app
 * process dies, unlike the coarse status.
 *
 * Written from [DictionaryDownloadService]'s `onProgress` callback, read
 * from `SettingsFragment`'s poll -- both always run on the main thread (the
 * callback is posted via each download manager's `mainHandler`, and the
 * poll runs on its own main-thread `Handler`), so this needs no
 * synchronization.
 */
object DictionaryDownloadProgressHolder {
    private val progressBySourceId = HashMap<String, DictionaryDownloadProgress>()

    fun update(sourceId: String, progress: DictionaryDownloadProgress) {
        progressBySourceId[sourceId] = progress
    }

    fun get(sourceId: String): DictionaryDownloadProgress? = progressBySourceId[sourceId]

    fun clear(sourceId: String) {
        progressBySourceId.remove(sourceId)
    }
}
