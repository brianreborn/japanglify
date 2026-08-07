package com.japanglify.app.dictionary

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteStatement
import android.os.Handler
import android.os.Looper
import com.japanglify.app.data.PreferencesRepository
import com.japanglify.app.domain.dictionary.DictionarySource
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

/**
 * Downloads a WordNet synset-membership [DictionarySource] (see
 * [com.japanglify.app.domain.dictionary.DictionarySources.WORDNET_SYNONYMS])
 * and atomically swaps it into a [WordNetDatabase] file. Same overall shape
 * as [EmojiDownloadManager] -- singleton `Executor` + `Handler(mainLooper)`,
 * `.building` file + atomic rename, `db.version` set explicitly on the
 * raw-built file (see [EmojiDownloadManager]'s comment for why that's not
 * optional) -- but plain-text line parsing instead of XML, since the source
 * file is Prolog facts, one per line: `s(id, wordNum, 'word', pos, sense,
 * tagCount).`, where every distinct word sharing the same synset `id` is a
 * synonym of every other.
 */
class WordNetDownloadManager(private val context: Context) {

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
                val textFile = downloadText(source) { percent ->
                    post(DictionaryDownloadProgress(DictionaryDownloadStatus.DOWNLOADING, percent = percent))
                }

                persist(DictionaryDownloadStatus.PARSING)
                post(DictionaryDownloadProgress(DictionaryDownloadStatus.PARSING))
                val buildingFile = File(
                    context.getDatabasePath(WordNetDatabase.fileNameFor(source.id)).path + ".building"
                )
                buildingFile.delete() // stale leftover from a previous failed attempt
                val pairCount = importTextIntoDatabase(textFile, buildingFile)
                textFile.delete()

                val liveFile = context.getDatabasePath(WordNetDatabase.fileNameFor(source.id))
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
                post(DictionaryDownloadProgress(DictionaryDownloadStatus.READY, wordsImported = pairCount))
            } catch (e: Exception) {
                val message = "${e::class.simpleName}: ${e.message}"
                persist(DictionaryDownloadStatus.FAILED, message)
                post(DictionaryDownloadProgress(DictionaryDownloadStatus.FAILED, errorMessage = message))
            }
        }
    }

    /** Tries [DictionarySource.downloadUrl] first, [DictionarySource.fallbackDownloadUrl] only if that fails outright. */
    private fun downloadText(source: DictionarySource, onProgress: (Int) -> Unit): File {
        val urls = listOfNotNull(source.downloadUrl, source.fallbackDownloadUrl)
        var lastError: Exception? = null
        for (url in urls) {
            try {
                return downloadFrom(url, source.id, onProgress)
            } catch (e: Exception) {
                lastError = e
            }
        }
        throw lastError ?: IllegalStateException("No download URL configured")
    }

    private fun downloadFrom(url: String, sourceId: String, onProgress: (Int) -> Unit): File {
        val cacheDir = File(context.cacheDir, "dictionaries").apply { mkdirs() }
        val textFile = File(cacheDir, "$sourceId.txt.part")
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.connectTimeout = CONNECT_TIMEOUT_MS
        connection.readTimeout = READ_TIMEOUT_MS
        connection.instanceFollowRedirects = true
        try {
            val code = connection.responseCode
            check(code == HttpURLConnection.HTTP_OK) { "HTTP $code downloading $url" }
            val total = connection.contentLength
            var downloaded = 0
            var lastPercent = -1
            connection.inputStream.use { input ->
                textFile.outputStream().use { output ->
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
        return textFile
    }

    /** Returns the number of word→synonym pairs imported. */
    private fun importTextIntoDatabase(textFile: File, dbFile: File): Int {
        val db = SQLiteDatabase.openOrCreateDatabase(dbFile, null)
        try {
            // See EmojiDownloadManager's identical line for why this is
            // required, not optional.
            db.version = WordNetDatabase.DB_VERSION
            db.execSQL(WordNetDatabase.CREATE_TABLE_SQL)
            val pairCount: Int
            db.beginTransaction()
            try {
                val stmt = db.compileStatement(INSERT_SQL)
                pairCount = textFile.inputStream().buffered(1 shl 16).use { input ->
                    parseAndInsert(BufferedReader(InputStreamReader(input, Charsets.UTF_8)), stmt)
                }
                db.setTransactionSuccessful()
            } finally {
                db.endTransaction()
            }
            db.execSQL(WordNetDatabase.CREATE_INDEX_SQL)
            return pairCount
        } finally {
            db.close()
        }
    }

    /**
     * Parses `s(id, wordNum, 'word', pos, sense, tagCount).` lines, groups
     * by synset `id`, and inserts a directed row for every ordered pair of
     * distinct words within a synset (so `lookup("car")` and
     * `lookup("automobile")` both work without the caller needing to know
     * which direction was stored). Synsets above [MAX_SYNSET_SIZE] are
     * skipped -- a defensive bound against a pathological/malformed line
     * count, not something real WordNet data is expected to hit.
     */
    private fun parseAndInsert(reader: BufferedReader, stmt: SQLiteStatement): Int {
        val bySynset = HashMap<Long, MutableList<String>>()
        var line = reader.readLine()
        while (line != null) {
            val m = LINE_PATTERN.matcher(line)
            if (m.matches()) {
                val synsetId = m.group(1)!!.toLong()
                val word = unescape(m.group(2)!!).lowercase()
                if (word.isNotBlank()) {
                    bySynset.getOrPut(synsetId) { ArrayList() }.add(word)
                }
            }
            line = reader.readLine()
        }

        var inserted = 0
        for (words in bySynset.values) {
            val distinct = words.distinct()
            if (distinct.size < 2 || distinct.size > MAX_SYNSET_SIZE) continue
            for (word in distinct) {
                for (synonym in distinct) {
                    if (word == synonym) continue
                    stmt.clearBindings()
                    stmt.bindString(1, word)
                    stmt.bindString(2, synonym)
                    stmt.executeInsert()
                    inserted++
                }
            }
        }
        return inserted
    }

    private fun unescape(rawWord: String): String =
        rawWord.replace("\\'", "'").replace("\\\\", "\\")

    companion object {
        private const val CONNECT_TIMEOUT_MS = 15_000
        private const val READ_TIMEOUT_MS = 60_000
        private const val MAX_SYNSET_SIZE = 25

        // s(100001740,1,'entity',n,1,11). -- group 1 = synset id, group 2 = word
        private val LINE_PATTERN =
            java.util.regex.Pattern.compile("""s\((\d+),\d+,'((?:\\.|[^'\\])*)',[nvasr],\d+,\d+\)\.""")

        private const val INSERT_SQL =
            "INSERT INTO ${WordNetDatabase.TABLE} (" +
                "${WordNetDatabase.COL_WORD}, ${WordNetDatabase.COL_SYNONYM}) VALUES (?, ?)"
    }
}
