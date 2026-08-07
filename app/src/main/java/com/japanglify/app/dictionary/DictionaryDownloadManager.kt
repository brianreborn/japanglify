package com.japanglify.app.dictionary

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteStatement
import android.os.Handler
import android.os.Looper
import com.japanglify.app.data.PreferencesRepository
import com.japanglify.app.domain.dictionary.DictionarySource
import com.japanglify.app.domain.dictionary.PartOfSpeech
import org.json.JSONObject
import java.io.File
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
            try {
                persist(DictionaryDownloadStatus.DOWNLOADING)
                post(DictionaryDownloadProgress(DictionaryDownloadStatus.DOWNLOADING, percent = 0))
                val zipFile = downloadZip(source) { percent ->
                    post(DictionaryDownloadProgress(DictionaryDownloadStatus.DOWNLOADING, percent = percent))
                }

                persist(DictionaryDownloadStatus.PARSING)
                post(DictionaryDownloadProgress(DictionaryDownloadStatus.PARSING))
                val buildingFile = File(
                    context.getDatabasePath(DictionaryDatabase.fileNameFor(source.id)).path + ".building"
                )
                buildingFile.delete() // stale leftover from a previous failed attempt
                val wordCount = importZipIntoDatabase(zipFile, buildingFile) { count ->
                    post(DictionaryDownloadProgress(DictionaryDownloadStatus.PARSING, wordsImported = count))
                }
                zipFile.delete()

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
            } catch (e: Exception) {
                val message = "${e::class.simpleName}: ${e.message}"
                persist(DictionaryDownloadStatus.FAILED, message)
                post(DictionaryDownloadProgress(DictionaryDownloadStatus.FAILED, errorMessage = message))
            }
        }
    }

    private fun downloadZip(source: DictionarySource, onProgress: (Int) -> Unit): File {
        val cacheDir = File(context.cacheDir, "dictionaries").apply { mkdirs() }
        val zipFile = File(cacheDir, "${source.id}.zip.part")
        val connection = URL(source.downloadUrl).openConnection() as HttpURLConnection
        connection.connectTimeout = CONNECT_TIMEOUT_MS
        connection.readTimeout = READ_TIMEOUT_MS
        connection.instanceFollowRedirects = true
        try {
            val code = connection.responseCode
            check(code == HttpURLConnection.HTTP_OK) { "HTTP $code downloading dictionary" }
            val total = connection.contentLength
            var downloaded = 0
            var lastPercent = -1
            connection.inputStream.use { input ->
                zipFile.outputStream().use { output ->
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
        return zipFile
    }

    /** Returns the total number of words imported. */
    private fun importZipIntoDatabase(zipFile: File, dbFile: File, onProgress: (Int) -> Unit): Int {
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
            var wordCount = 0
            ZipInputStream(zipFile.inputStream().buffered(1 shl 16)).use { zis ->
                var entry = zis.nextEntry
                var found = false
                while (entry != null) {
                    if (!entry.isDirectory && entry.name.endsWith(".json")) {
                        found = true
                        db.beginTransaction()
                        try {
                            val stmt = db.compileStatement(INSERT_SQL)
                            val source = CharSource(InputStreamReader(zis, Charsets.UTF_8))
                            streamWordObjects(source) { word ->
                                wordCount += insertWord(word, stmt)
                                if (wordCount % PROGRESS_REPORT_INTERVAL == 0) onProgress(wordCount)
                            }
                            db.setTransactionSuccessful()
                        } finally {
                            db.endTransaction()
                        }
                        break
                    }
                    entry = zis.nextEntry
                }
                check(found) { "No .json entry found in downloaded archive" }
            }
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

    /**
     * Inserts one row per kanji spelling (so Kuromoji's `baseForm` matches
     * whichever orthographic variant it returns for a given word), or one
     * row keyed by the kana reading for kana-only words (particles, etc,
     * which JMdict never gives a kanji form). Returns how many rows were
     * inserted (0 if the word had nothing usable — e.g. no English gloss).
     *
     * Persists exactly the fields [SqliteDictionaryProvider] reads to build
     * a rendered gloss row — first sense, first English gloss, first
     * part-of-speech code — and nothing past that: JMdict's `id`, extra
     * senses/glosses/POS codes, and the always-empty-in-practice `related`/
     * `antonym`/`field`/`dialect`/`misc`/`info`/`languageSource` arrays never
     * reach a rendered row in v1, so they're dropped here rather than carried
     * as dead weight into the persisted database. (Multi-sense support is a
     * named backlog item — "sense disambiguation" in the plan — and would
     * be added here deliberately if picked up, not carried speculatively now.)
     */
    private fun insertWord(word: JSONObject, stmt: SQLiteStatement): Int {
        val senses = word.optJSONArray("sense") ?: return 0
        if (senses.length() == 0) return 0
        val firstSense = senses.getJSONObject(0)

        val glossArr = firstSense.optJSONArray("gloss") ?: return 0
        var gloss: String? = null
        for (g in 0 until glossArr.length()) {
            val entry = glossArr.getJSONObject(g)
            if (entry.optString("lang") == "eng") {
                gloss = entry.optString("text").takeIf { it.isNotBlank() }
                if (gloss != null) break
            }
        }
        if (gloss == null) return 0
        // JMdict often qualifies a gloss with a trailing clarifier, e.g.
        // "Japanese (language)" -- useful when several senses need telling
        // apart, but v1 only ever shows this one (first) sense, so within a
        // single displayed row there's nothing left for it to disambiguate
        // from; it just costs space. Strip it at import time, matching the
        // "persist only what actually reaches a rendered row" policy
        // (see insertWord's own doc comment) rather than at render time.
        gloss = stripTrailingParenthetical(gloss)

        val posArr = firstSense.optJSONArray("partOfSpeech")
        val pos = if (posArr != null && posArr.length() > 0) posArr.optString(0) else null
        val posClass = pos?.let { PartOfSpeech.fromJmdictCode(it).name }

        val kanaArr = word.optJSONArray("kana")
        val reading = if (kanaArr != null && kanaArr.length() > 0) {
            kanaArr.getJSONObject(0).optString("text").takeIf { it.isNotBlank() }
        } else null

        val kanjiArr = word.optJSONArray("kanji")
        var inserted = 0
        var kanjiMatchesReading = false
        if (kanjiArr != null && kanjiArr.length() > 0) {
            for (k in 0 until kanjiArr.length()) {
                val headword = kanjiArr.getJSONObject(k).optString("text").takeIf { it.isNotBlank() } ?: continue
                bindAndExecute(stmt, headword, reading, pos, gloss, posClass)
                inserted++
                if (headword == reading) kanjiMatchesReading = true
            }
        }
        // Also insert a row keyed by the bare kana reading whenever it
        // differs from every kanji spelling already inserted above -- not
        // just when there's no kanji at all. Kuromoji/IPADIC's `baseForm`
        // for common verbs like する ("to do") is reliably the plain-kana
        // form even though JMdict itself files that entry under a rare
        // kanji spelling (為る); without this, a kanji-only insert would
        // make ordinary, extremely common words unlookupable in practice.
        // Confirmed live: する returned nothing until this was added.
        if (reading != null && !kanjiMatchesReading) {
            bindAndExecute(stmt, reading, reading, pos, gloss, posClass)
            inserted++
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
        posClass: String?
    ) {
        stmt.clearBindings()
        stmt.bindString(1, headword)
        if (reading != null) stmt.bindString(2, reading) else stmt.bindNull(2)
        if (pos != null) stmt.bindString(3, pos) else stmt.bindNull(3)
        stmt.bindString(4, gloss)
        if (posClass != null) stmt.bindString(5, posClass) else stmt.bindNull(5)
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
        private const val PROGRESS_REPORT_INTERVAL = 2_000

        private const val INSERT_SQL =
            "INSERT INTO ${DictionaryDatabase.TABLE} (" +
                "${DictionaryDatabase.COL_HEADWORD}, ${DictionaryDatabase.COL_READING}, " +
                "${DictionaryDatabase.COL_POS}, ${DictionaryDatabase.COL_GLOSS}, " +
                "${DictionaryDatabase.COL_POS_CLASS}) VALUES (?, ?, ?, ?, ?)"
    }
}
