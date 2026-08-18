package com.japanglify.app.data

import android.content.Context
import androidx.preference.PreferenceManager
import com.japanglify.app.domain.ElisionMarker
import com.japanglify.app.domain.EmojiPrecisionTier
import com.japanglify.app.domain.FuriganaPunctuationStyle
import com.japanglify.app.domain.JapanglifySettings
import com.japanglify.app.domain.MoraSeamStyle
import com.japanglify.app.domain.OutputFormat
import com.japanglify.app.domain.RomajiPosition
import com.japanglify.app.domain.RomanizationSystem
import com.japanglify.app.domain.ShareTarget
import com.japanglify.app.domain.ShareTargetAction
import com.japanglify.app.domain.WritingOrientation
import com.japanglify.app.domain.dictionary.PartOfSpeech
import com.japanglify.app.domain.dictionary.SenseSelectionPreset
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/**
 * CRUD storage for user-defined [ShareTarget]s, backed by one JSON-array
 * SharedPreferences key (org.json — already a transitive dependency via
 * [com.japanglify.app.dictionary.DictionaryDownloadManager]'s JMdict
 * parsing, no new dependency needed). A target's [JapanglifySettings] is
 * serialized field-for-field using each enum's own `.id`/`fromId`
 * round-trip convention, the same one [PreferencesRepository] uses for the
 * global settings screen — so a target really is a full, faithful snapshot,
 * not just the handful of fields the "save as target" UI happens to expose.
 *
 * Every write goes through [com.japanglify.app.share.ShareTargetShortcuts]
 * to keep the OS's dynamic Share-sheet shortcuts in sync — see that class's
 * doc for why a target only really "exists" once both sides agree.
 */
class ShareTargetRepository(context: Context) {

    private val prefs = PreferenceManager.getDefaultSharedPreferences(context.applicationContext)

    fun list(): List<ShareTarget> {
        val raw = prefs.getString(KEY_TARGETS, null) ?: return emptyList()
        val array = runCatching { JSONArray(raw) }.getOrNull() ?: return emptyList()
        return (0 until array.length()).mapNotNull { i ->
            runCatching { targetFromJson(array.getJSONObject(i)) }.getOrNull()
        }
    }

    fun find(id: String): ShareTarget? = list().firstOrNull { it.id == id }

    /** Creates and persists a new target snapshotting [settings], returning it. */
    fun create(label: String, settings: JapanglifySettings, action: ShareTargetAction = ShareTargetAction.COPY_TEXT): ShareTarget {
        val target = ShareTarget(id = UUID.randomUUID().toString(), label = label, settings = settings, action = action)
        saveAll(list() + target)
        return target
    }

    /** Upsert by [ShareTarget.id] — replaces an existing target with the same id, or appends. */
    fun save(target: ShareTarget) {
        val existing = list()
        val next = if (existing.any { it.id == target.id }) {
            existing.map { if (it.id == target.id) target else it }
        } else {
            existing + target
        }
        saveAll(next)
    }

    fun delete(id: String) {
        saveAll(list().filterNot { it.id == id })
    }

    private fun saveAll(targets: List<ShareTarget>) {
        val array = JSONArray()
        targets.forEach { array.put(targetToJson(it)) }
        prefs.edit().putString(KEY_TARGETS, array.toString()).apply()
    }

    private fun targetToJson(target: ShareTarget): JSONObject {
        val s = target.settings
        return JSONObject().apply {
            put("id", target.id)
            put("label", target.label)
            put("action", target.action.id)
            put("romanizationSystem", s.romanizationSystem.id)
            put("romajiPosition", s.romajiPosition.id)
            put("outputFormat", s.outputFormat.id)
            put("writingOrientation", s.writingOrientation.id)
            put("includeFurigana", s.includeFurigana)
            put("includeRomaji", s.includeRomaji)
            put("includeGlosses", s.includeGlosses)
            put("includeEmoji", s.includeEmoji)
            put("emojiAlwaysShowBoth", s.emojiAlwaysShowBoth)
            put("emojiPosScope", JSONArray(s.emojiPosScope.map { it.name }))
            put("emojiPrecisionTier", s.emojiPrecisionTier.id)
            put("furiganaKanjiOnly", s.furiganaKanjiOnly)
            put("capitalizeRomaji", s.capitalizeRomaji)
            put("furiganaPunctuationStyle", s.furiganaPunctuationStyle.id)
            put("elisionMarker", s.elisionMarker.id)
            put("moraSeamStyle", s.moraSeamStyle.id)
            put("maxLineWidthFullwidth", s.maxLineWidthFullwidth)
            put("cjkDisplayWidthUnits", s.cjkDisplayWidthUnits)
            put("senseSelectionPreset", s.senseSelectionPreset.id)
            put("customSenseRichnessWeight", s.customSenseRichnessWeight)
            put("customSensePositionWeight", s.customSensePositionWeight)
            put("customSenseDatedWeight", s.customSenseDatedWeight)
        }
    }

    private fun targetFromJson(json: JSONObject): ShareTarget {
        val defaults = JapanglifySettings.DEFAULT
        val settings = JapanglifySettings(
            romanizationSystem = RomanizationSystem.fromId(json.optString("romanizationSystem")),
            romajiPosition = RomajiPosition.fromId(json.optString("romajiPosition")),
            outputFormat = OutputFormat.fromId(json.optString("outputFormat")),
            writingOrientation = WritingOrientation.fromId(json.optString("writingOrientation")),
            includeFurigana = json.optBoolean("includeFurigana", defaults.includeFurigana),
            includeRomaji = json.optBoolean("includeRomaji", defaults.includeRomaji),
            includeGlosses = json.optBoolean("includeGlosses", defaults.includeGlosses),
            includeEmoji = json.optBoolean("includeEmoji", defaults.includeEmoji),
            emojiAlwaysShowBoth = json.optBoolean("emojiAlwaysShowBoth", defaults.emojiAlwaysShowBoth),
            emojiPosScope = json.optJSONArray("emojiPosScope")?.let { arr ->
                (0 until arr.length()).mapNotNull { i ->
                    PartOfSpeech.entries.firstOrNull { it.name == arr.optString(i) }
                }.toSet()
            }?.takeIf { it.isNotEmpty() } ?: defaults.emojiPosScope,
            emojiPrecisionTier = EmojiPrecisionTier.fromId(json.optString("emojiPrecisionTier")),
            furiganaKanjiOnly = json.optBoolean("furiganaKanjiOnly", defaults.furiganaKanjiOnly),
            capitalizeRomaji = json.optBoolean("capitalizeRomaji", defaults.capitalizeRomaji),
            furiganaPunctuationStyle = FuriganaPunctuationStyle.fromId(json.optString("furiganaPunctuationStyle")),
            elisionMarker = ElisionMarker.fromId(json.optString("elisionMarker")),
            moraSeamStyle = MoraSeamStyle.fromId(json.optString("moraSeamStyle")),
            maxLineWidthFullwidth = if (json.has("maxLineWidthFullwidth")) {
                json.optInt("maxLineWidthFullwidth", defaults.maxLineWidthFullwidth)
            } else {
                defaults.maxLineWidthFullwidth
            },
            cjkDisplayWidthUnits = if (json.has("cjkDisplayWidthUnits")) {
                json.optInt("cjkDisplayWidthUnits", defaults.cjkDisplayWidthUnits)
            } else {
                defaults.cjkDisplayWidthUnits
            },
            senseSelectionPreset = SenseSelectionPreset.fromId(json.optString("senseSelectionPreset")),
            customSenseRichnessWeight = json.optDouble("customSenseRichnessWeight", defaults.customSenseRichnessWeight),
            customSensePositionWeight = json.optDouble("customSensePositionWeight", defaults.customSensePositionWeight),
            customSenseDatedWeight = json.optDouble("customSenseDatedWeight", defaults.customSenseDatedWeight)
        )
        return ShareTarget(
            id = json.getString("id"),
            label = json.optString("label", "Japanglify"),
            settings = settings,
            action = ShareTargetAction.fromId(json.optString("action"))
        )
    }

    companion object {
        private const val KEY_TARGETS = "share_targets"
    }
}
