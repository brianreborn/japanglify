package com.japanglify.app.debug

import android.app.Activity
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.TextView
import com.japanglify.app.data.PreferencesRepository
import com.japanglify.app.dictionary.DictionaryDownloadService
import com.japanglify.app.dictionary.DictionaryDownloadStatus
import com.japanglify.app.dictionary.EmojiDatabase
import com.japanglify.app.dictionary.SqliteDictionaryProvider
import com.japanglify.app.dictionary.SqliteEmojiProvider
import com.japanglify.app.dictionary.WordNetDatabase
import com.japanglify.app.domain.EmojiPrecisionTier
import com.japanglify.app.domain.dictionary.DictionarySource
import com.japanglify.app.domain.dictionary.DictionarySourceFormat
import com.japanglify.app.domain.dictionary.DictionarySources
import com.japanglify.app.domain.dictionary.GlossAnnotator
import com.japanglify.app.domain.dictionary.PartOfSpeech
import com.japanglify.app.domain.emoji.EmojiAnnotator

/**
 * Debug-build-only seam for live-testing [DictionaryDownloadService] end to
 * end before Phase 6's Settings UI exists. Launch with:
 *   adb shell am start -n com.japanglify.app/com.japanglify.app.debug.DictionaryDownloadTestActivity \
 *     --es source_id cldr_emoji_en
 * (omit the extra, or pass "jmdict_en", for the word dictionary)
 *
 * Deliberately stays resumed for the whole download, polling the persisted
 * status every 2s -- confirmed necessary by live testing on this device: a
 * foreground service *alone*, once this activity finished immediately and
 * left the app with zero visible window, still got reaped ("Stopping
 * service due to app idle") after ~74s despite `startForeground()` having
 * been called correctly. A screen staying open during download is also
 * just what the real Settings UI looks like (a download-status card the
 * user has open), not an artifact special-cased for this harness.
 *
 * Never present in a release build: lives under `app/src/debug/`.
 */
class DictionaryDownloadTestActivity : Activity() {

    private val handler = Handler(Looper.getMainLooper())
    private lateinit var textView: TextView
    private lateinit var prefs: PreferencesRepository
    private lateinit var source: DictionarySource

    private val poll = object : Runnable {
        override fun run() {
            val status = prefs.dictionaryStatus(source.id)
            textView.text = "status=$status error=${prefs.dictionaryErrorMessage(source.id)}"
            if (status == DictionaryDownloadStatus.READY) {
                textView.text = "${textView.text}\n${spotCheck()}"
            }
            if (status != DictionaryDownloadStatus.READY && status != DictionaryDownloadStatus.FAILED) {
                handler.postDelayed(this, POLL_INTERVAL_MS)
            }
        }
    }

    private fun spotCheck(): String = when (source.format) {
        DictionarySourceFormat.JMDICT_JSON -> {
            val provider = SqliteDictionaryProvider(applicationContext, source.id)
            val weights = com.japanglify.app.domain.JapanglifySettings.DEFAULT.effectiveSenseWeights
            SPOT_CHECK_WORDS_JA.joinToString("\n") { "lookup($it) = ${provider.lookup(it, null, weights)}" }
        }
        DictionarySourceFormat.CLDR_EMOJI_XML -> {
            val helper = EmojiDatabase(applicationContext, EmojiDatabase.fileNameFor(source.id))
            val db = helper.readableDatabase
            val counts = db.rawQuery(
                "SELECT ${EmojiDatabase.COL_TIER}, COUNT(*) FROM ${EmojiDatabase.TABLE} GROUP BY ${EmojiDatabase.COL_TIER}",
                null
            ).use { cursor ->
                buildString {
                    while (cursor.moveToNext()) append("${cursor.getString(0)}=${cursor.getInt(1)} ")
                }
            }
            val lookups = SPOT_CHECK_WORDS_EN.joinToString("\n") { word ->
                db.rawQuery(
                    "SELECT ${EmojiDatabase.COL_EMOJI}, ${EmojiDatabase.COL_TIER} FROM ${EmojiDatabase.TABLE} " +
                        "WHERE ${EmojiDatabase.COL_WORD} = ?",
                    arrayOf(word)
                ).use { cursor ->
                    val results = generateSequence { if (cursor.moveToNext()) cursor else null }
                        .map { "${it.getString(0)}(${it.getString(1)})" }
                        .toList()
                    "lookup($word) = ${if (results.isEmpty()) "null" else results.joinToString(", ")}"
                }
            }
            val provider = SqliteEmojiProvider(applicationContext, source.id)
            val annotator = EmojiAnnotator(provider)
            val allPos = PartOfSpeech.entries.toSet()
            val tiered = MEDIUM_TEST_WORDS.joinToString("\n") { word ->
                val strict = provider.lookup(word, EmojiPrecisionTier.STRICT)
                val medium = provider.lookup(word, EmojiPrecisionTier.MEDIUM)
                val loose = provider.lookup(word, EmojiPrecisionTier.LOOSE)
                // annotate() wraps provider.lookup() with CategoryEmoji's
                // static fallback -- only visible through this path, not
                // through provider.lookup() directly (see EmojiAnnotator).
                val viaAnnotator = annotator.annotate(
                    listOf(GlossAnnotator.GlossResult(word, PartOfSpeech.NOUN)),
                    allPos,
                    EmojiPrecisionTier.LOOSE
                )[0]
                "tiered($word) strict=$strict medium=$medium loose=$loose viaAnnotator=$viaAnnotator"
            }
            "counts: $counts\n$lookups\n$tiered"
        }
        DictionarySourceFormat.WORDNET_PROLOG -> {
            val helper = WordNetDatabase(applicationContext, WordNetDatabase.fileNameFor(source.id))
            val db = helper.readableDatabase
            val synCount = db.rawQuery("SELECT COUNT(*) FROM ${WordNetDatabase.TABLE}", null).use { cursor ->
                cursor.moveToFirst(); cursor.getInt(0)
            }
            val hypCount = db.rawQuery("SELECT COUNT(*) FROM ${WordNetDatabase.TABLE_HYPERNYMS}", null).use { cursor ->
                cursor.moveToFirst(); cursor.getInt(0)
            }
            val lookups = SPOT_CHECK_SYNONYM_WORDS.joinToString("\n") { word ->
                db.rawQuery(
                    "SELECT ${WordNetDatabase.COL_SYNONYM} FROM ${WordNetDatabase.TABLE} " +
                        "WHERE ${WordNetDatabase.COL_WORD} = ?",
                    arrayOf(word)
                ).use { cursor ->
                    val results = generateSequence { if (cursor.moveToNext()) cursor else null }
                        .map { it.getString(0) }
                        .toList()
                    "synonyms($word) = ${if (results.isEmpty()) "none" else results.joinToString(", ")}"
                }
            }
            val hypLookups = SPOT_CHECK_HYPERNYM_WORDS.joinToString("\n") { word ->
                db.rawQuery(
                    "SELECT ${WordNetDatabase.COL_HYPERNYM_SIBLING} FROM ${WordNetDatabase.TABLE_HYPERNYMS} " +
                        "WHERE ${WordNetDatabase.COL_WORD} = ?",
                    arrayOf(word)
                ).use { cursor ->
                    val results = generateSequence { if (cursor.moveToNext()) cursor else null }
                        .map { it.getString(0) }
                        .toList()
                    "hypernymSiblings($word) = ${if (results.isEmpty()) "none" else results.joinToString(", ")}"
                }
            }
            "counts: pairs=$synCount hypernymPairs=$hypCount\n$lookups\n$hypLookups"
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = PreferencesRepository(this)
        source = DictionarySources.byId(intent?.getStringExtra(EXTRA_SOURCE_ID))
            ?: DictionarySources.JMDICT_ENGLISH
        textView = TextView(this).apply {
            text = "starting…"
            textSize = 14f
            setPadding(32, 32, 32, 32)
        }
        setContentView(textView)
        if (prefs.dictionaryStatus(source.id) != DictionaryDownloadStatus.READY) {
            DictionaryDownloadService.start(this, source)
        }
        handler.post(poll)
    }

    override fun onDestroy() {
        handler.removeCallbacks(poll)
        super.onDestroy()
    }

    companion object {
        private const val EXTRA_SOURCE_ID = "source_id"
        private const val POLL_INTERVAL_MS = 2000L
        private val SPOT_CHECK_WORDS_JA = listOf("日本語", "勉強", "する", "食べる", "犬", "な", "だ", "より", "も", "かけ", "かける", "わし")
        private val SPOT_CHECK_WORDS_EN = listOf("dog", "water", "paper", "book", "study", "animal", "hole", "mountain")
        private val SPOT_CHECK_SYNONYM_WORDS = listOf("automobile", "car", "study", "sofa")
        private val SPOT_CHECK_HYPERNYM_WORDS = listOf("jacket", "sofa", "building")
        private val MEDIUM_TEST_WORDS =
            listOf(
                "car", "couch", "automobile", "bike", "phone", "kid", "jacket", "plate", "study", "table",
                "animal", "vehicle", "worker", "bicycle", "insect"
            )
    }
}
