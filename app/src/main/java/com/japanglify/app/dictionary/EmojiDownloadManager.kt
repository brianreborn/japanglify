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
     * Reads CLDR's `<annotation cp="🐕" type="tts">dog</annotation>`
     * elements (only the `type="tts"` short-name ones). The untyped
     * `<annotation cp="🐕">animal | animals | dog | ...</annotation>`
     * keyword-list siblings were tried as a second ("MEDIUM") tier in an
     * earlier version of this class and abandoned: CLDR assigns a `tts` to
     * essentially every emoji (verified live: 1967 of 1967), so a
     * keyword-derived candidate for a *different* word almost always
     * collides with an emoji already claimed by its own `tts` word, and
     * "no symbol reused for a different word" correctly drops it -- net
     * result was real coverage of ~0. MEDIUM's real fallback now comes from
     * [WordNetDownloadManager]'s synonym expansion instead (see
     * [SqliteEmojiProvider]), which is a genuine synonym relation rather
     * than CLDR's mostly-hyponym keyword lists.
     *
     * Builds an in-memory word→emoji map (a few thousand entries at most --
     * no memory concern at this scale) and drops any word claimed by more
     * than one distinct emoji, so the persisted map stays genuinely 1:1.
     */
    private fun parseAndInsert(reader: java.io.Reader, stmt: SQLiteStatement): Int {
        val parser: XmlPullParser = Xml.newPullParser()
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
        parser.setInput(reader)

        val claimedBy = HashMap<String, String>()
        val ambiguous = HashSet<String>()
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
                    if (currentIsTts && cp != null) {
                        val word = parser.text?.trim()?.lowercase()?.takeIf { it.isNotBlank() }
                        if (word != null) {
                            val existing = claimedBy[word]
                            if (existing == null) {
                                claimedBy[word] = cp
                            } else if (existing != cp) {
                                ambiguous += word
                            }
                        }
                    }
                }
                XmlPullParser.END_TAG -> if (parser.name == "annotation") {
                    currentCp = null
                    currentIsTts = false
                }
            }
            eventType = parser.next()
        }

        var inserted = 0
        for ((word, emoji) in claimedBy) {
            if (word in ambiguous) continue
            stmt.clearBindings()
            stmt.bindString(1, word)
            stmt.bindString(2, emoji)
            stmt.bindString(3, EmojiDatabase.TIER_STRICT)
            stmt.executeInsert()
            inserted++
        }
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
