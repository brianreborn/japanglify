package com.japanglify.app.dictionary

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteStatement
import android.os.Handler
import android.os.Looper
import com.japanglify.app.BuildConfig
import com.japanglify.app.data.PreferencesRepository
import com.japanglify.app.domain.dictionary.DictionarySource
import com.japanglify.app.domain.dictionary.PartOfSpeech
import org.json.JSONObject
import org.tukaani.xz.XZInputStream
import java.io.File
import java.io.InputStream
import java.io.InputStreamReader
import java.io.Reader
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors
import java.util.zip.ZipInputStream

/**
 * Downloads a [DictionarySource], streams it into a fresh SQLite database,
 * and atomically swaps it in — mirrors the deleted `Translator.kt`'s shape
 * (singleton `Executor` + `Handler(mainLooper)` callback) rather than
 * introducing WorkManager or coroutines, matching this codebase's
 * established idiom.
 *
 * The JSON is parsed with a hand-rolled streaming reader
 * ([streamWordObjects]/[readBalancedJsonObject]), not `org.json`'s normal
 * `JSONObject(fullText)` DOM parse — the full jmdict-eng dataset is
 * ~100 MB+ uncompressed with ~200,000 word entries, and materializing that
 * whole document as a live object graph just to visit each entry once and
 * discard it is real, avoidable memory risk on a phone. Streaming holds at
 * most one word object in memory at a time regardless of total dictionary
 * size; what's actually written to disk keeps only the handful of fields
 * a rendered gloss row needs (see [insertWord]) — smaller and simpler than
 * the source JSON, not a lossy cache of it.
 */
class DictionaryDownloadManager(private val context: Context) {

    private val executor = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())
    private val prefs = PreferencesRepository(context)

    /**
     * [onProgress] fires on the main thread for every UI-relevant tick
     * (percent-downloaded, words-imported). Only the coarse state
     * transitions (DOWNLOADING started / PARSING started / READY / FAILED)
     * are also written to [PreferencesRepository] — a status that must
     * survive the app process dying mid-download, not the fine-grained
     * ticks, which a killed process has no UI left to show anyway.
     */
    fun download(source: DictionarySource, onProgress: (DictionaryDownloadProgress) -> Unit) {
        executor.execute {
            fun post(progress: DictionaryDownloadProgress) {
                mainHandler.post { onProgress(progress) }
            }
            fun persist(status: DictionaryDownloadStatus, errorMessage: String? = null) {
                prefs.setDictionaryStatus(source.id, status, errorMessage)
            }
            DictionaryDownloadCancellation.reset(source.id)
            try {
                val buildingFile = File(
                    context.getDatabasePath(DictionaryDatabase.fileNameFor(source.id)).path + ".building"
                )
                buildingFile.delete() // stale leftover from a previous failed attempt

                // The bundled flavor skips the network (and the ZIP
                // container -- see BundledDictionaryAssets' doc comment on
                // why the asset is the bare LZMA2-compressed JSON, not a
                // re-zipped one) entirely, decompressing straight from the
                // APK asset into the importer with no intermediate
                // decompressed-JSON file ever touching disk.
                val wordCount = if (BuildConfig.DICTIONARIES_BUNDLED) {
                    persist(DictionaryDownloadStatus.PARSING)
                    post(DictionaryDownloadProgress(DictionaryDownloadStatus.PARSING))
                    context.assets.open("dictionaries/${source.id}.json.xz").use { raw ->
                        XZInputStream(raw).use { decompressed ->
                            importJsonStreamIntoDatabase(decompressed, buildingFile) { count ->
                                post(DictionaryDownloadProgress(DictionaryDownloadStatus.PARSING, wordsImported = count))
                            }
                        }
                    }
                } else {
                    persist(DictionaryDownloadStatus.DOWNLOADING)
                    post(DictionaryDownloadProgress(DictionaryDownloadStatus.DOWNLOADING, percent = 0))
                    val zipFile = downloadZip(source) { percent ->
                        post(DictionaryDownloadProgress(DictionaryDownloadStatus.DOWNLOADING, percent = percent))
                    }

                    persist(DictionaryDownloadStatus.PARSING)
                    post(DictionaryDownloadProgress(DictionaryDownloadStatus.PARSING))
                    val count = importZipIntoDatabase(zipFile, buildingFile) { count ->
                        post(DictionaryDownloadProgress(DictionaryDownloadStatus.PARSING, wordsImported = count))
                    }
                    zipFile.delete()
                    count
                }

                val liveFile = context.getDatabasePath(DictionaryDatabase.fileNameFor(source.id))
                // Same-directory rename is atomic on Android's filesystem, so a
                // crash/kill mid-import never corrupts a working database --
                // the old one (if any) simply stays live until this succeeds.
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
            } catch (e: DownloadCancelledException) {
                // A user-initiated cancel, not a failure -- leave it exactly
                // as if the download had never been started, so the status
                // card offers "Download" again rather than "Retry" with a
                // scary error message.
                persist(DictionaryDownloadStatus.NOT_DOWNLOADED)
                post(DictionaryDownloadProgress(DictionaryDownloadStatus.NOT_DOWNLOADED))
            } catch (e: Exception) {
                val message = "${e::class.simpleName}: ${e.message}"
                persist(DictionaryDownloadStatus.FAILED, message)
                post(DictionaryDownloadProgress(DictionaryDownloadStatus.FAILED, errorMessage = message))
            } finally {
                DictionaryDownloadCancellation.reset(source.id)
            }
        }
    }

    /**
     * Tries [DictionarySource.downloadUrl] first, [DictionarySource.fallbackDownloadUrl] only if that fails outright.
     * Only called for the "downloadable" flavor -- "bundled" never reaches this (see [download]'s branch).
     */
    private fun downloadZip(source: DictionarySource, onProgress: (Int) -> Unit): File {
        val urls = listOfNotNull(source.downloadUrl, source.fallbackDownloadUrl)
        var lastError: Exception? = null
        for (url in urls) {
            if (DictionaryDownloadCancellation.isCancelled(source.id)) throw DownloadCancelledException()
            try {
                return downloadZipFrom(url, source.id, onProgress)
            } catch (e: DownloadCancelledException) {
                throw e
            } catch (e: Exception) {
                lastError = e
            }
        }
        throw lastError ?: IllegalStateException("No download URL configured")
    }

    private fun downloadZipFrom(url: String, sourceId: String, onProgress: (Int) -> Unit): File {
        val cacheDir = File(context.cacheDir, "dictionaries").apply { mkdirs() }
        val zipFile = File(cacheDir, "$sourceId.zip.part")
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.connectTimeout = CONNECT_TIMEOUT_MS
        connection.readTimeout = READ_TIMEOUT_MS
        connection.instanceFollowRedirects = true
        DictionaryDownloadCancellation.beginAttempt(sourceId, connection)
        try {
            val code = connection.responseCode
            check(code == HttpURLConnection.HTTP_OK) { "HTTP $code downloading dictionary" }
            val total = connection.contentLength
            var downloaded = 0
            var lastPercent = -1
            // Per-read idle timeout (readTimeout above) only fires when the
            // socket goes fully silent -- a connection that keeps trickling
            // a byte or two just often enough to reset that timer never
            // trips it and downloads "forever" in practice. Confirmed as the
            // actual failure mode live: a stalled-but-not-silent GitHub
            // release-asset connection sat in DOWNLOADING at a fixed percent
            // for 20+ minutes with no timeout ever firing. This wall-clock
            // deadline is what actually bounds worst-case download time.
            val deadline = android.os.SystemClock.elapsedRealtime() + ABSOLUTE_TIMEOUT_MS
            connection.inputStream.use { input ->
                zipFile.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        if (DictionaryDownloadCancellation.isCancelled(sourceId)) throw DownloadCancelledException()
                        check(android.os.SystemClock.elapsedRealtime() < deadline) {
                            "Download stalled past ${ABSOLUTE_TIMEOUT_MS / 1000}s"
                        }
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
            DictionaryDownloadCancellation.endAttempt(sourceId, connection)
            connection.disconnect()
        }
        return zipFile
    }

    /** Returns the total number of words imported. */
    private fun importZipIntoDatabase(zipFile: File, dbFile: File, onProgress: (Int) -> Unit): Int {
        return withFreshDatabase(dbFile) { db ->
            var wordCount = 0
            ZipInputStream(zipFile.inputStream().buffered(1 shl 16)).use { zis ->
                var entry = zis.nextEntry
                var found = false
                while (entry != null) {
                    if (!entry.isDirectory && entry.name.endsWith(".json")) {
                        found = true
                        wordCount = importWordsFromStream(zis, db, onProgress)
                        break
                    }
                    entry = zis.nextEntry
                }
                check(found) { "No .json entry found in downloaded archive" }
            }
            wordCount
        }
    }

    /** Bundled-flavor counterpart of [importZipIntoDatabase] -- same word stream, no ZIP wrapper to unwrap first. */
    private fun importJsonStreamIntoDatabase(jsonStream: InputStream, dbFile: File, onProgress: (Int) -> Unit): Int {
        return withFreshDatabase(dbFile) { db -> importWordsFromStream(jsonStream, db, onProgress) }
    }

    /**
     * Shared scaffolding around either import path: create the table
     * (with `user_version` set -- see the comment this used to carry
     * inline, preserved on [DictionaryDatabase.DB_VERSION]'s assignment
     * below), run [body] to actually populate it, then build the index and
     * ambiguity-marking pass once at the end rather than incrementally.
     */
    private fun withFreshDatabase(dbFile: File, body: (SQLiteDatabase) -> Int): Int {
        val db = SQLiteDatabase.openOrCreateDatabase(dbFile, null)
        try {
            // Raw SQLiteDatabase.openOrCreateDatabase() never touches SQLite's
            // `user_version` pragma. Without this, the read side
            // (DictionaryDatabase, a SQLiteOpenHelper) sees version 0 on its
            // first open, treats that as older than DB_VERSION, and its own
            // onUpgrade() drops + recreates the table empty -- silently
            // wiping a just-imported dictionary the first time it's ever
            // read. Confirmed live: countEntries() reported 0 rows against a
            // dictionary that had genuinely finished importing successfully.
            db.version = DictionaryDatabase.DB_VERSION
            db.execSQL(DictionaryDatabase.CREATE_TABLE_SQL)
            val wordCount = body(db)
            // Built once, after the bulk insert -- indexing incrementally
            // during ~200k inserts would be far slower than indexing once.
            db.execSQL(DictionaryDatabase.CREATE_INDEX_SQL)
            // Benefits from that same index (scans by headword), so it runs
            // after -- see MARK_POS_AMBIGUOUS_SQL's doc comment.
            db.execSQL(DictionaryDatabase.MARK_POS_AMBIGUOUS_SQL)
            return wordCount
        } finally {
            db.close()
        }
    }

    private fun importWordsFromStream(stream: InputStream, db: SQLiteDatabase, onProgress: (Int) -> Unit): Int {
        var wordCount = 0
        db.beginTransaction()
        try {
            val stmt = db.compileStatement(INSERT_SQL)
            val source = CharSource(InputStreamReader(stream, Charsets.UTF_8))
            streamWordObjects(source) { word ->
                wordCount += insertWord(word, stmt)
                if (wordCount % PROGRESS_REPORT_INTERVAL == 0) onProgress(wordCount)
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        return wordCount
    }

    /**
     * Inserts one row per (kanji spelling × candidate sense) — kanji
     * spellings so Kuromoji's `baseForm` matches whichever orthographic
     * variant it returns for a given word, candidate senses (up to
     * [MAX_SENSES_PER_WORD]) so [SqliteDictionaryProvider] has more than
     * just JMdict's sense 0 to score at query time (see
     * [com.japanglify.app.domain.dictionary.SenseSelector]). Also inserts a
     * row keyed by the kana reading for kana-only words (particles, etc,
     * which JMdict never gives a kanji form). Returns how many rows were
     * inserted (0 if the word had nothing usable — e.g. no English gloss on
     * any sense).
     *
     * Persists exactly the fields [SqliteDictionaryProvider] reads to score
     * and build a rendered gloss row — each candidate sense's rank, first
     * English gloss, gloss count, "dated" flag, and first part-of-speech
     * code — and nothing past that: JMdict's `id` and the always-empty-in-
     * practice `related`/`antonym`/`field`/`dialect`/`info`/
     * `languageSource` arrays never reach a rendered row, so they're
     * dropped here rather than carried as dead weight into the persisted
     * database.
     */
    private fun insertWord(word: JSONObject, stmt: SQLiteStatement): Int {
        val senses = word.optJSONArray("sense") ?: return 0
        if (senses.length() == 0) return 0

        val kanaArr = word.optJSONArray("kana")
        val reading = if (kanaArr != null && kanaArr.length() > 0) {
            kanaArr.getJSONObject(0).optString("text").takeIf { it.isNotBlank() }
        } else null

        val kanjiArr = word.optJSONArray("kanji")
        val headwords = ArrayList<String>()
        var kanjiMatchesReading = false
        if (kanjiArr != null && kanjiArr.length() > 0) {
            for (k in 0 until kanjiArr.length()) {
                val headword = kanjiArr.getJSONObject(k).optString("text").takeIf { it.isNotBlank() } ?: continue
                headwords += headword
                if (headword == reading) kanjiMatchesReading = true
            }
        }
        // Also key a row by the bare kana reading whenever it differs from
        // every kanji spelling above -- not just when there's no kanji at
        // all. Kuromoji/IPADIC's `baseForm` for common verbs like する ("to
        // do") is reliably the plain-kana form even though JMdict itself
        // files that entry under a rare kanji spelling (為る); without this,
        // a kanji-only insert would make ordinary, extremely common words
        // unlookupable in practice. Confirmed live: する returned nothing
        // until this was added.
        if (reading != null && !kanjiMatchesReading) headwords += reading
        if (headwords.isEmpty()) return 0

        var inserted = 0
        for (rank in 0 until minOf(senses.length(), MAX_SENSES_PER_WORD)) {
            val sense = senses.getJSONObject(rank)
            val glossArr = sense.optJSONArray("gloss") ?: continue
            var gloss: String? = null
            var glossCount = 0
            for (g in 0 until glossArr.length()) {
                val entry = glossArr.getJSONObject(g)
                if (entry.optString("lang") != "eng") continue
                val text = entry.optString("text").takeIf { it.isNotBlank() } ?: continue
                glossCount++
                if (gloss == null) gloss = text
            }
            if (gloss == null) continue
            // JMdict often qualifies a gloss with a trailing clarifier, e.g.
            // "Japanese (language)" -- useful when several senses need
            // telling apart, but a single rendered row only ever shows one
            // sense's gloss at a time, so within that row there's nothing
            // left for it to disambiguate from; it just costs space. Strip
            // it at import time, matching the "persist only what actually
            // reaches a rendered row" policy rather than at render time.
            gloss = stripTrailingParenthetical(gloss)

            val posArr = sense.optJSONArray("partOfSpeech")
            val pos = if (posArr != null && posArr.length() > 0) posArr.optString(0) else null
            val posClass = pos?.let { PartOfSpeech.fromJmdictCode(it).name }

            val miscArr = sense.optJSONArray("misc")
            var dated = false
            if (miscArr != null) {
                for (m in 0 until miscArr.length()) {
                    if (miscArr.optString(m) in DATED_MISC_TAGS) {
                        dated = true
                        break
                    }
                }
            }

            for (headword in headwords) {
                bindAndExecute(stmt, headword, reading, pos, gloss, posClass, rank, glossCount, dated)
                inserted++
            }
        }
        return inserted
    }

    /**
     * Strips one trailing "(...)" qualifier, e.g. "Japanese (language)" ->
     * "Japanese". Nesting-aware -- confirmed live this session that a naive
     * non-nested regex silently fails on real JMdict glosses like
     * "dog (Canis (lupus) familiaris)" (species entries routinely carry a
     * taxonomic name with its own inner parens), leaving the whole
     * qualifier in place instead of stripping it. Walks backward from the
     * end tracking paren depth to find the *matching* opening paren for the
     * final ')', so "(Canis (lupus) familiaris)" strips as one balanced
     * unit -> "dog". Left unchanged if the trailing parens aren't balanced
     * back to a matching '(' (safety fallback, same as the blank-result
     * fallback below).
     */
    private fun stripTrailingParenthetical(gloss: String): String {
        val trimmedEnd = gloss.trimEnd()
        if (!trimmedEnd.endsWith(')')) return gloss
        var depth = 0
        var openIndex = -1
        for (i in trimmedEnd.indices.reversed()) {
            when (trimmedEnd[i]) {
                ')' -> depth++
                '(' -> {
                    depth--
                    if (depth == 0) {
                        openIndex = i
                        break
                    }
                }
            }
        }
        if (openIndex < 0) return gloss
        val stripped = trimmedEnd.substring(0, openIndex).trim()
        return stripped.ifBlank { gloss }
    }

    private fun bindAndExecute(
        stmt: SQLiteStatement,
        headword: String,
        reading: String?,
        pos: String?,
        gloss: String,
        posClass: String?,
        senseRank: Int,
        glossCount: Int,
        dated: Boolean
    ) {
        stmt.clearBindings()
        stmt.bindString(1, headword)
        if (reading != null) stmt.bindString(2, reading) else stmt.bindNull(2)
        if (pos != null) stmt.bindString(3, pos) else stmt.bindNull(3)
        stmt.bindString(4, gloss)
        if (posClass != null) stmt.bindString(5, posClass) else stmt.bindNull(5)
        stmt.bindLong(6, senseRank.toLong())
        stmt.bindLong(7, glossCount.toLong())
        stmt.bindLong(8, if (dated) 1L else 0L)
        stmt.executeInsert()
    }

    /**
     * Streams individual word objects out of a jmdict-simplified JSON
     * document's top-level "words" array without ever holding the whole
     * array (or the full document) in memory — reads character-by-character
     * to locate the "words" key (the preamble before it, "tags" etc., isn't
     * needed by this importer at all, so it's never parsed, just scanned
     * past), then extracts and parses one balanced-brace {...} object at a
     * time via a normal (memory-cheap, single-object) [JSONObject].
     */
    private fun streamWordObjects(source: CharSource, onWord: (JSONObject) -> Unit) {
        val marker = "\"words\""
        var matched = 0
        while (matched < marker.length) {
            val c = source.read()
            check(c >= 0) { "\"words\" key not found in dictionary JSON" }
            matched = if (c.toChar() == marker[matched]) matched + 1
                else if (c.toChar() == marker[0]) 1 else 0
        }
        check(skipWhitespaceAndReadNext(source).toChar() == ':') { "Expected ':' after \"words\"" }
        check(skipWhitespaceAndReadNext(source).toChar() == '[') { "Expected '[' to start words array" }

        while (true) {
            val next = skipWhitespaceAndReadNext(source)
            check(next >= 0) { "Unexpected end of stream in words array" }
            when (next.toChar()) {
                ']' -> return
                ',' -> continue
                '{' -> onWord(JSONObject(readBalancedJsonObject(source)))
                else -> error("Unexpected character '${next.toChar()}' in words array")
            }
        }
    }

    private fun skipWhitespaceAndReadNext(source: CharSource): Int {
        while (true) {
            val c = source.read()
            if (c < 0 || !c.toChar().isWhitespace()) return c
        }
    }

    /** Reads a JSON object's body given the opening '{' was already consumed; returns the full "{...}" text. */
    private fun readBalancedJsonObject(source: CharSource): String {
        val sb = StringBuilder("{")
        var depth = 1
        var inString = false
        var escaped = false
        while (depth > 0) {
            val ci = source.read()
            check(ci >= 0) { "Unexpected end of stream while reading a word object" }
            val c = ci.toChar()
            sb.append(c)
            when {
                escaped -> escaped = false
                inString && c == '\\' -> escaped = true
                c == '"' -> inString = !inString
                !inString && c == '{' -> depth++
                !inString && c == '}' -> depth--
            }
        }
        return sb.toString()
    }

    /**
     * Manually-buffered single-character reader. `java.io.Reader.read()`
     * (as used by [InputStreamReader]) is a synchronized method call that
     * decodes through `StreamDecoder` on every invocation -- fine for
     * occasional reads, but this scanner calls it once per character across
     * a ~100 MB+ document, and the per-call overhead dominates at that scale
     * (measured live: a full jmdict-eng import spent several CPU-minutes in
     * this scanner). Reading a large chunk from the underlying [Reader] at
     * once and then indexing a plain `CharArray` cuts that to one real
     * method call per [bufferSize] characters instead of one per character.
     */
    private class CharSource(private val reader: Reader, bufferSize: Int = 1 shl 16) {
        private val buffer = CharArray(bufferSize)
        private var pos = 0
        private var len = 0

        fun read(): Int {
            if (pos >= len) {
                len = reader.read(buffer)
                pos = 0
                if (len <= 0) return -1
            }
            return buffer[pos++].code
        }
    }

    companion object {
        private const val CONNECT_TIMEOUT_MS = 15_000
        private const val READ_TIMEOUT_MS = 30_000
        // ~10-15 MB should complete in well under this on any real
        // connection; generous headroom over that, not a tight bound.
        private const val ABSOLUTE_TIMEOUT_MS = 5 * 60_000L
        private const val PROGRESS_REPORT_INTERVAL = 2_000

        private const val INSERT_SQL =
            "INSERT INTO ${DictionaryDatabase.TABLE} (" +
                "${DictionaryDatabase.COL_HEADWORD}, ${DictionaryDatabase.COL_READING}, " +
                "${DictionaryDatabase.COL_POS}, ${DictionaryDatabase.COL_GLOSS}, " +
                "${DictionaryDatabase.COL_POS_CLASS}, ${DictionaryDatabase.COL_SENSE_RANK}, " +
                "${DictionaryDatabase.COL_GLOSS_COUNT}, ${DictionaryDatabase.COL_DATED}" +
                ") VALUES (?, ?, ?, ?, ?, ?, ?, ?)"

        /** Cap on candidate senses stored per headword — see [insertWord]'s doc. */
        private const val MAX_SENSES_PER_WORD = 3

        /** JMdict `misc` tags treated as "dated" for [SenseSelector]'s scoring. */
        private val DATED_MISC_TAGS = setOf("arch", "obs", "obsc", "dated")
    }
}
