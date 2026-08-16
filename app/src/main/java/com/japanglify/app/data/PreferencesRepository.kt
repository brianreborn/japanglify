package com.japanglify.app.data

import android.content.Context
import android.content.SharedPreferences
import androidx.preference.PreferenceManager
import com.japanglify.app.dictionary.DictionaryDownloadStatus
import com.japanglify.app.domain.EmojiPrecisionTier
import com.japanglify.app.domain.FuriganaPunctuationStyle
import com.japanglify.app.domain.dictionary.DictionarySources
import com.japanglify.app.domain.JapanglifySettings
import com.japanglify.app.domain.OutputFormat
import com.japanglify.app.domain.RomajiPosition
import com.japanglify.app.domain.RomanizationSystem
import com.japanglify.app.domain.WritingOrientation
import com.japanglify.app.domain.dictionary.PartOfSpeech

/**
 * Reads/writes [JapanglifySettings] via the default SharedPreferences store
 * shared with the PreferenceFragmentCompat settings screen.
 */
class PreferencesRepository(context: Context) {

    private val prefs: SharedPreferences =
        PreferenceManager.getDefaultSharedPreferences(context.applicationContext)

    fun load(): JapanglifySettings = JapanglifySettings(
        romanizationSystem = RomanizationSystem.fromId(prefs.getString(KEY_ROMANIZATION, null)),
        romajiPosition = RomajiPosition.fromId(prefs.getString(KEY_ROMAJI_POSITION, null)),
        outputFormat = OutputFormat.fromId(prefs.getString(KEY_OUTPUT_FORMAT, null)),
        writingOrientation = WritingOrientation.fromId(
            prefs.getString(KEY_WRITING_ORIENTATION, null)
        ),
        includeFurigana = prefs.getBoolean(KEY_INCLUDE_FURIGANA, true),
        includeRomaji = prefs.getBoolean(KEY_INCLUDE_ROMAJI, true),
        furiganaKanjiOnly = prefs.getBoolean(KEY_FURIGANA_KANJI_ONLY, true),
        capitalizeRomaji = prefs.getBoolean(KEY_CAPITALIZE_ROMAJI, false),
        furiganaPunctuationStyle = FuriganaPunctuationStyle.fromId(
            prefs.getString(KEY_FURIGANA_PUNCTUATION_STYLE, null)
        ),
        maxLineWidthFullwidth = parseMaxLineWidth(prefs.getString(KEY_MAX_LINE_WIDTH, null)),
        // Read side only for now — no preferences.xml entry yet (that's the
        // Settings UI phase, alongside the dictionary-source picker this
        // toggle is meant to sit next to). Defaults false like every other
        // opt-in setting; wiring the read side now means the eventual UI
        // toggle needs zero changes here, just an XML entry writing to the
        // same key.
        includeGlosses = prefs.getBoolean(KEY_INCLUDE_GLOSSES, false),
        includeEmoji = prefs.getBoolean(KEY_INCLUDE_EMOJI, false),
        emojiAlwaysShowBoth = prefs.getBoolean(KEY_EMOJI_ALWAYS_SHOW_BOTH, false),
        // getStringSet returns null only when the key was never written (the
        // MultiSelectListPreference has never been touched) -- default to
        // every part of speech then, per the user's explicit choice. An
        // empty (non-null) set means the user genuinely deselected
        // everything, which is honored as-is, not treated as "unset".
        emojiPosScope = prefs.getStringSet(KEY_EMOJI_POS_SCOPE, null)
            ?.mapNotNull { name -> PartOfSpeech.entries.firstOrNull { it.name == name } }
            ?.toSet()
            ?: PartOfSpeech.entries.toSet(),
        emojiPrecisionTier = EmojiPrecisionTier.fromId(prefs.getString(KEY_EMOJI_PRECISION_TIER, null))
    )

    /**
     * Download status persists across app restarts (a "READY" dictionary
     * must still read as ready after the process dies) — kept outside
     * [JapanglifySettings] for the same reason [includeGlosses]'s toggle
     * lives in preferences.xml rather than that class's own defaults: this
     * is app-layer/network/storage state, not a pure offline rendering
     * input. Per-[sourceId] since the registry supports multiple sources.
     */
    fun dictionaryStatus(sourceId: String): DictionaryDownloadStatus {
        val raw = prefs.getString(keyDictionaryStatus(sourceId), null)
        return DictionaryDownloadStatus.entries.firstOrNull { it.name == raw }
            ?: DictionaryDownloadStatus.NOT_DOWNLOADED
    }

    fun dictionaryErrorMessage(sourceId: String): String? =
        prefs.getString(keyDictionaryError(sourceId), null)

    fun setDictionaryStatus(sourceId: String, status: DictionaryDownloadStatus, errorMessage: String? = null) {
        prefs.edit()
            .putString(keyDictionaryStatus(sourceId), status.name)
            .putString(keyDictionaryError(sourceId), errorMessage)
            .apply()
    }

    private fun keyDictionaryStatus(sourceId: String) = "${KEY_DICTIONARY_STATUS_PREFIX}$sourceId"
    private fun keyDictionaryError(sourceId: String) = "${KEY_DICTIONARY_ERROR_PREFIX}$sourceId"

    /** Which [com.japanglify.app.domain.dictionary.DictionarySource] the Settings picker has selected. */
    fun selectedDictionarySourceId(): String =
        prefs.getString(KEY_DICTIONARY_SOURCE, null) ?: DictionarySources.JMDICT_ENGLISH.id

    companion object {
        const val KEY_ROMANIZATION = "romanization_system"
        const val KEY_ROMAJI_POSITION = "romaji_position"
        const val KEY_OUTPUT_FORMAT = "output_format"
        const val KEY_WRITING_ORIENTATION = "writing_orientation"
        const val KEY_INCLUDE_FURIGANA = "include_furigana"
        const val KEY_INCLUDE_ROMAJI = "include_romaji"
        const val KEY_FURIGANA_KANJI_ONLY = "furigana_kanji_only"
        const val KEY_CAPITALIZE_ROMAJI = "capitalize_romaji"
        const val KEY_FURIGANA_PUNCTUATION_STYLE = "furigana_punctuation_style"
        const val KEY_MAX_LINE_WIDTH = "max_line_width_fullwidth"
        const val KEY_CLIPBOARD_ASSIST = "clipboard_assist"
        const val KEY_CLIPBOARD_FGS_FALLBACK = "clipboard_fgs_fallback"
        const val KEY_OPEN_ACCESSIBILITY = "open_accessibility"
        const val KEY_A11Y_STATUS = "a11y_status"
        const val KEY_COPY_HOOK_PAUSED = "copy_hook_paused"
        const val KEY_INCLUDE_GLOSSES = "include_glosses"
        const val KEY_DICTIONARY_STATUS_PREFIX = "dictionary_status_"
        const val KEY_DICTIONARY_ERROR_PREFIX = "dictionary_error_"
        const val KEY_DICTIONARY_SOURCE = "dictionary_source"
        const val KEY_INCLUDE_EMOJI = "include_emoji"
        const val KEY_EMOJI_ALWAYS_SHOW_BOTH = "emoji_always_show_both"
        const val KEY_EMOJI_POS_SCOPE = "emoji_pos_scope"
        const val KEY_EMOJI_PRECISION_TIER = "emoji_precision_tier"
        /** Whether the first-launch license/donate dialog has already been shown once. */
        const val KEY_LICENSE_DIALOG_SHOWN = "license_dialog_shown"

        fun parseMaxLineWidth(raw: String?): Int {
            val n = raw?.toIntOrNull()
            return when {
                n == null -> JapanglifySettings.DEFAULT_MAX_LINE_WIDTH_FULLWIDTH
                n < 0 -> 0
                n > 64 -> 64
                else -> n
            }
        }
    }
}
