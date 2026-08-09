package com.japanglify.app.dictionary

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteStatement
import android.os.Handler
import android.os.Looper
import android.util.Xml
import com.japanglify.app.data.PreferencesRepository
import com.japanglify.app.domain.dictionary.DictionarySource
import org.xmlpull.v1.XmlPullParser
import java.io.File
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

/**
 * Downloads a CLDR emoji-annotations [DictionarySource] and atomically swaps
 * it into an [EmojiDatabase] file. Same overall shape as
 * [DictionaryDownloadManager] (singleton `Executor` + `Handler(mainLooper)`,
 * `.building` file + atomic rename, coarse status persisted via
 * [PreferencesRepository]) -- kept as its own class rather than sharing code
 * with [DictionaryDownloadManager] because the two genuinely differ in every
 * format-specific step (no ZIP wrapper, XML not JSON, a flat in-memory map
 * instead of a character-level streaming scanner): CLDR's `en.xml` is
 * ~295 KB, three orders of magnitude smaller than jmdict-eng, so none of
 * JMdict's memory-conscious streaming machinery is needed here.
 */
class EmojiDownloadManager(private val context: Context) {

    private val executor = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())
    private val prefs = PreferencesRepository(context)

    fun download(source: DictionarySource, onProgress: (DictionaryDownloadProgress) -> Unit) {
        executor.execute {
            fun post(progress: DictionaryDownloadProgress) {
                mainHandler.post { onProgress(progress) }
            }
            fun persist(status: DictionaryDownloadStatus, errorMessage: String? = null) {
                prefs.setDictionaryStatus(source.id, status, errorMessage)
            }
            try {
                persist(DictionaryDownloadStatus.DOWNLOADING)
                post(DictionaryDownloadProgress(DictionaryDownloadStatus.DOWNLOADING, percent = 0))
                val xmlFile = downloadXml(source) { percent ->
                    post(DictionaryDownloadProgress(DictionaryDownloadStatus.DOWNLOADING, percent = percent))
                }

                persist(DictionaryDownloadStatus.PARSING)
                post(DictionaryDownloadProgress(DictionaryDownloadStatus.PARSING))
                val buildingFile = File(
                    context.getDatabasePath(EmojiDatabase.fileNameFor(source.id)).path + ".building"
                )
                buildingFile.delete() // stale leftover from a previous failed attempt
                val wordCount = importXmlIntoDatabase(xmlFile, buildingFile)
                xmlFile.delete()

                val liveFile = context.getDatabasePath(EmojiDatabase.fileNameFor(source.id))
                if (!buildingFile.renameTo(liveFile)) {
                    persist(DictionaryDownloadStatus.FAILED, "Could not finalize database file")
                    post(
                        DictionaryDownloadProgress(
                            DictionaryDownloadStatus.FAILED,
                            errorMessage = "Could not finalize database file"
                        )
                    )
                    return@execute
                }

                persist(DictionaryDownloadStatus.READY)
                post(DictionaryDownloadProgress(DictionaryDownloadStatus.READY, wordsImported = wordCount))
            } catch (e: Exception) {
                val message = "${e::class.simpleName}: ${e.message}"
                persist(DictionaryDownloadStatus.FAILED, message)
                post(DictionaryDownloadProgress(DictionaryDownloadStatus.FAILED, errorMessage = message))
            }
        }
    }

    private fun downloadXml(source: DictionarySource, onProgress: (Int) -> Unit): File {
        val cacheDir = File(context.cacheDir, "dictionaries").apply { mkdirs() }
        val xmlFile = File(cacheDir, "${source.id}.xml.part")
        val connection = URL(source.downloadUrl).openConnection() as HttpURLConnection
        connection.connectTimeout = CONNECT_TIMEOUT_MS
        connection.readTimeout = READ_TIMEOUT_MS
        connection.instanceFollowRedirects = true
        try {
            val code = connection.responseCode
            check(code == HttpURLConnection.HTTP_OK) { "HTTP $code downloading emoji data" }
            val total = connection.contentLength
            var downloaded = 0
            var lastPercent = -1
            connection.inputStream.use { input ->
                xmlFile.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        val n = input.read(buffer)
                        if (n < 0) break
                        output.write(buffer, 0, n)
                        downloaded += n
                        if (total > 0) {
                            val percent = (downloaded * 100L / total).toInt()
                            if (percent != lastPercent) {
                                lastPercent = percent
                                onProgress(percent)
                            }
                        }
                    }
                }
            }
        } finally {
            connection.disconnect()
        }
        return xmlFile
    }

    /** Returns the number of word→emoji rows imported. */
    private fun importXmlIntoDatabase(xmlFile: File, dbFile: File): Int {
        val db = SQLiteDatabase.openOrCreateDatabase(dbFile, null)
        try {
            // See DictionaryDownloadManager's identical line for why this is
            // required, not optional: a raw-built file with no user_version
            // set gets silently emptied by SQLiteOpenHelper's onUpgrade() the
            // first time EmojiDatabase (a SQLiteOpenHelper) opens it to read.
            db.version = EmojiDatabase.DB_VERSION
            db.execSQL(EmojiDatabase.CREATE_TABLE_SQL)
            val wordCount: Int
            db.beginTransaction()
            try {
                val stmt = db.compileStatement(INSERT_SQL)
                wordCount = xmlFile.inputStream().buffered(1 shl 16).use { input ->
                    parseAndInsert(InputStreamReader(input, Charsets.UTF_8), stmt)
                }
                db.setTransactionSuccessful()
            } finally {
                db.endTransaction()
            }
            db.execSQL(EmojiDatabase.CREATE_INDEX_SQL)
            return wordCount
        } finally {
            db.close()
        }
    }

    /**
     * Reads every `<annotation cp="🐕" ...>` element and builds two tiers:
     *
     * - STRICT: only `type="tts"` short-name elements (CLDR's one canonical
     *   name per emoji), exact match, word -> emoji.
     * - LOOSE: the untyped `<annotation cp="🐕">animal | animals | dog |
     *   dogs | pet</annotation>` keyword-list siblings, split into exact
     *   tokens (not substring matching -- "hat" must equal a whole keyword,
     *   not appear inside "what"). A first attempt at using these for MEDIUM
     *   required a candidate's emoji not already be some other word's exact
     *   `tts` match, on a "one emoji, one word, ever" theory -- and that
     *   theory is what killed it: CLDR assigns a `tts` to essentially every
     *   emoji (verified live: 1967 of 1967), so nearly every keyword-derived
     *   candidate got excluded this way, leaving real coverage at ~0.
     *   Rebuilt as LOOSE with that constraint dropped entirely (only reached
     *   when STRICT and [WordNetDatabase]'s synonym/hypernym relations all
     *   miss -- see [SqliteEmojiProvider]): "couch" resolving to 🛋 even
     *   though 🛋's own `tts` is "couch and lamp", not "couch", is fine --
     *   there's no rule against two related words sharing a symbol, only
     *   against picking arbitrarily *among competing candidates for the
     *   same word*. That's still enforced: a word's keyword-derived
     *   candidate must be the single emoji whose keyword list contains that
     *   exact word, same as STRICT's `claimUnique` below.
     *
     * Both tiers still drop any *word* claimed by more than one distinct
     * emoji (genuine ambiguity about what that word itself means); STRICT
     * words are excluded from LOOSE's map purely because STRICT already
     * wins first at query time, not to protect STRICT's symbols from reuse.
     */
    private fun parseAndInsert(reader: java.io.Reader, stmt: SQLiteStatement): Int {
        data class Annotation(val cp: String, val isTts: Boolean, val text: String)

        val parser: XmlPullParser = Xml.newPullParser()
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
        parser.setInput(reader)

        val annotations = ArrayList<Annotation>()
        var currentCp: String? = null
        var currentIsTts = false
        var eventType = parser.eventType
        while (eventType != XmlPullParser.END_DOCUMENT) {
            when (eventType) {
                XmlPullParser.START_TAG -> if (parser.name == "annotation") {
                    currentCp = parser.getAttributeValue(null, "cp")
                    currentIsTts = parser.getAttributeValue(null, "type") == "tts"
                }
                XmlPullParser.TEXT -> {
                    val cp = currentCp
                    if (cp != null) {
                        val text = parser.text
                        if (!text.isNullOrBlank()) annotations += Annotation(cp, currentIsTts, text)
                    }
                }
                XmlPullParser.END_TAG -> if (parser.name == "annotation") {
                    currentCp = null
                    currentIsTts = false
                }
            }
            eventType = parser.next()
        }

        fun claimUnique(pairs: List<Pair<String, String>>): Map<String, String> {
            val claimedBy = HashMap<String, String>()
            val ambiguous = HashSet<String>()
            for ((word, cp) in pairs) {
                val existing = claimedBy[word]
                if (existing == null) claimedBy[word] = cp
                else if (existing != cp) ambiguous += word
            }
            return claimedBy.filterKeys { it !in ambiguous }
        }

        val strict = claimUnique(
            annotations.filter { it.isTts }
                .map { it.text.trim().lowercase() to it.cp }
                .filter { it.first.isNotBlank() }
        )

        val looseCandidates = annotations.filter { !it.isTts }
            .flatMap { ann -> ann.text.split('|').map { it.trim().lowercase() to ann.cp } }
            .filter { it.first.isNotBlank() }
        val loose = claimUnique(looseCandidates).filterKeys { it !in strict.keys }

        var inserted = 0
        fun insertAll(map: Map<String, String>, tier: String) {
            for ((word, emoji) in map) {
                stmt.clearBindings()
                stmt.bindString(1, word)
                stmt.bindString(2, emoji)
                stmt.bindString(3, tier)
                stmt.executeInsert()
                inserted++
            }
        }
        insertAll(strict, EmojiDatabase.TIER_STRICT)
        insertAll(loose, EmojiDatabase.TIER_LOOSE)
        return inserted
    }

    companion object {
        private const val CONNECT_TIMEOUT_MS = 15_000
        private const val READ_TIMEOUT_MS = 30_000

        private const val INSERT_SQL =
            "INSERT INTO ${EmojiDatabase.TABLE} (" +
                "${EmojiDatabase.COL_WORD}, ${EmojiDatabase.COL_EMOJI}, ${EmojiDatabase.COL_TIER}) " +
                "VALUES (?, ?, ?)"
    }
}
