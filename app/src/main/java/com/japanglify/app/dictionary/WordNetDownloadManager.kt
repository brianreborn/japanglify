package com.japanglify.app.dictionary

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteStatement
import android.os.Handler
import android.os.Looper
import com.japanglify.app.BuildConfig
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
            DictionaryDownloadCancellation.reset(source.id)
            try {
                persist(DictionaryDownloadStatus.DOWNLOADING)
                post(DictionaryDownloadProgress(DictionaryDownloadStatus.DOWNLOADING, percent = 0))
                // Two files share the 0-100% progress range (synsets ~7 MB,
                // hypernym relations ~2 MB) -- weighted so the much bigger
                // synset file dominates the visible progress bar.
                val synsetFile = downloadText(source.downloadUrl, source.fallbackDownloadUrl, source.id) { percent ->
                    post(DictionaryDownloadProgress(DictionaryDownloadStatus.DOWNLOADING, percent = percent * 8 / 10))
                }
                val hypernymFile = downloadText(HYPERNYM_URL, HYPERNYM_FALLBACK_URL, "${source.id}_hyp") { percent ->
                    post(DictionaryDownloadProgress(DictionaryDownloadStatus.DOWNLOADING, percent = 80 + percent * 2 / 10))
                }

                persist(DictionaryDownloadStatus.PARSING)
                post(DictionaryDownloadProgress(DictionaryDownloadStatus.PARSING))
                val buildingFile = File(
                    context.getDatabasePath(WordNetDatabase.fileNameFor(source.id)).path + ".building"
                )
                buildingFile.delete() // stale leftover from a previous failed attempt
                val pairCount = importTextIntoDatabase(synsetFile, hypernymFile, buildingFile)
                synsetFile.delete()
                hypernymFile.delete()

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
            } catch (e: DownloadCancelledException) {
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

    /** Tries [primaryUrl] first, [fallbackUrl] only if that fails outright. */
    private fun downloadText(primaryUrl: String, fallbackUrl: String?, fileId: String, onProgress: (Int) -> Unit): File {
        if (BuildConfig.DICTIONARIES_BUNDLED) {
            val file = BundledDictionaryAssets.decompressToCache(context, "$fileId.txt.xz", "$fileId.txt.part")
            onProgress(100)
            return file
        }
        val urls = listOfNotNull(primaryUrl, fallbackUrl)
        var lastError: Exception? = null
        for (url in urls) {
            try {
                return downloadFrom(url, fileId, onProgress)
            } catch (e: DownloadCancelledException) {
                throw e
            } catch (e: Exception) {
                lastError = e
            }
        }
        throw lastError ?: IllegalStateException("No download URL configured")
    }

    private fun downloadFrom(url: String, sourceId: String, onProgress: (Int) -> Unit): File {
        if (DictionaryDownloadCancellation.isCancelled(sourceId)) throw DownloadCancelledException()
        val cacheDir = File(context.cacheDir, "dictionaries").apply { mkdirs() }
        val textFile = File(cacheDir, "$sourceId.txt.part")
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.connectTimeout = CONNECT_TIMEOUT_MS
        connection.readTimeout = READ_TIMEOUT_MS
        connection.instanceFollowRedirects = true
        DictionaryDownloadCancellation.beginAttempt(sourceId, connection)
        try {
            val code = connection.responseCode
            check(code == HttpURLConnection.HTTP_OK) { "HTTP $code downloading $url" }
            val total = connection.contentLength
            var downloaded = 0
            var lastPercent = -1
            val deadline = android.os.SystemClock.elapsedRealtime() + ABSOLUTE_TIMEOUT_MS
            connection.inputStream.use { input ->
                textFile.outputStream().use { output ->
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
        return textFile
    }

    /** Returns the number of word->related-word pairs imported (both tables combined). */
    private fun importTextIntoDatabase(synsetFile: File, hypernymFile: File, dbFile: File): Int {
        val db = SQLiteDatabase.openOrCreateDatabase(dbFile, null)
        try {
            // See EmojiDownloadManager's identical line for why this is
            // required, not optional.
            db.version = WordNetDatabase.DB_VERSION
            db.execSQL(WordNetDatabase.CREATE_TABLE_SQL)
            db.execSQL(WordNetDatabase.CREATE_HYPERNYM_TABLE_SQL)
            var pairCount: Int
            db.beginTransaction()
            try {
                val (bySynset, primarySynsetOf) = synsetFile.inputStream().buffered(1 shl 16).use { input ->
                    parseSynsets(BufferedReader(InputStreamReader(input, Charsets.UTF_8)))
                }
                pairCount = insertSynonyms(bySynset, primarySynsetOf, db.compileStatement(SYNONYM_INSERT_SQL))
                pairCount += hypernymFile.inputStream().buffered(1 shl 16).use { input ->
                    insertHypernymSiblings(
                        BufferedReader(InputStreamReader(input, Charsets.UTF_8)),
                        bySynset,
                        primarySynsetOf,
                        db.compileStatement(HYPERNYM_INSERT_SQL)
                    )
                }
                db.setTransactionSuccessful()
            } finally {
                db.endTransaction()
            }
            db.execSQL(WordNetDatabase.CREATE_INDEX_SQL)
            db.execSQL(WordNetDatabase.CREATE_HYPERNYM_INDEX_SQL)
            return pairCount
        } finally {
            db.close()
        }
    }

    /**
     * Parses `s(id, wordNum, 'word', pos, sense, tagCount).` lines and
     * groups by synset `id` (words sharing an `id` are synonyms). A
     * polysemous word (e.g. "car" -- both "automobile" and "elevator car")
     * appears in several synsets, and using *all* of them indiscriminately
     * produces bad matches: verified live this session that unioning every
     * sense either wrongly makes "car" ambiguous against an unrelated
     * "elevator" match, or -- worse -- lets a rare, obscure sense sneak
     * through as a word's *only* candidate (real example found live:
     * "jacket" has a dental "jacket crown" sense with `tagCount=1`, whose
     * sibling "crown" has a CLDR match, so "jacket" incorrectly resolved to
     * 👑 with nothing to flag it as dubious).
     *
     * `tagCount` is WordNet's own corpus-frequency count for that word in
     * that specific sense, so each word is only ever expanded from its own
     * *single most frequent* sense (e.g. "car"'s tagCount=71 "automobile"
     * sense beats its tagCount=2 "elevator car" sense) rather than every
     * sense it happens to have -- this is what actually fixed both cases
     * above, confirmed against the real downloaded data.
     */
    private fun parseSynsets(reader: BufferedReader): Pair<Map<Long, List<String>>, Map<String, Pair<Long, Int>>> {
        val bySynset = HashMap<Long, MutableList<String>>()
        // Each word's best (synsetId, tagCount) seen so far -- its most
        // frequent/primary sense, used as the only source of synonyms and
        // hypernym-siblings (see class doc for why *all* senses is wrong).
        val primarySynsetOf = HashMap<String, Pair<Long, Int>>()
        var line = reader.readLine()
        while (line != null) {
            val m = LINE_PATTERN.matcher(line)
            if (m.matches()) {
                val synsetId = m.group(1)!!.toLong()
                val word = unescape(m.group(2)!!).lowercase()
                val tagCount = m.group(3)!!.toInt()
                if (word.isNotBlank()) {
                    bySynset.getOrPut(synsetId) { ArrayList() }.add(word)
                    val current = primarySynsetOf[word]
                    if (current == null || tagCount > current.second) {
                        primarySynsetOf[word] = synsetId to tagCount
                    }
                }
            }
            line = reader.readLine()
        }
        return bySynset to primarySynsetOf
    }

    private fun insertSynonyms(
        bySynset: Map<Long, List<String>>,
        primarySynsetOf: Map<String, Pair<Long, Int>>,
        stmt: SQLiteStatement
    ): Int {
        var inserted = 0
        for ((word, primary) in primarySynsetOf) {
            val siblings = bySynset[primary.first]?.distinct()?.filter { it != word } ?: continue
            if (siblings.isEmpty() || siblings.size > MAX_SYNSET_SIZE) continue
            for (synonym in siblings) {
                stmt.clearBindings()
                stmt.bindString(1, word)
                stmt.bindString(2, synonym)
                stmt.executeInsert()
                inserted++
            }
        }
        return inserted
    }

    /**
     * Parses `hyp(childSynsetId,parentSynsetId).` lines and, for each word's
     * *own primary* synset (same selection as [insertSynonyms]), looks up
     * its immediate hypernym parent -- only when that child synset has
     * exactly one parent, so a word with genuinely ambiguous categorization
     * doesn't get an arbitrary pick -- and inserts every other word in that
     * parent synset as a "hypernym sibling" (e.g. "jacket"'s primary sense
     * -> parent "coat" -> sibling "coat" itself). This is a broader,
     * one-hop-looser relation than [insertSynonyms]'s same-synset synonymy
     * (a jacket IS-A coat, not IS a coat), which is exactly why it only
     * backs [com.japanglify.app.domain.EmojiPrecisionTier.LOOSE], not
     * MEDIUM -- see [SqliteEmojiProvider].
     */
    private fun insertHypernymSiblings(
        reader: BufferedReader,
        bySynset: Map<Long, List<String>>,
        primarySynsetOf: Map<String, Pair<Long, Int>>,
        stmt: SQLiteStatement
    ): Int {
        val parentsOf = HashMap<Long, MutableList<Long>>()
        var line = reader.readLine()
        while (line != null) {
            val m = HYPERNYM_LINE_PATTERN.matcher(line)
            if (m.matches()) {
                val child = m.group(1)!!.toLong()
                val parent = m.group(2)!!.toLong()
                parentsOf.getOrPut(child) { ArrayList() }.add(parent)
            }
            line = reader.readLine()
        }

        var inserted = 0
        for ((word, primary) in primarySynsetOf) {
            val parents = parentsOf[primary.first] ?: continue
            if (parents.size != 1) continue
            val siblings = bySynset[parents[0]]?.distinct()?.filter { it != word } ?: continue
            if (siblings.isEmpty() || siblings.size > MAX_SYNSET_SIZE) continue
            for (sibling in siblings) {
                stmt.clearBindings()
                stmt.bindString(1, word)
                stmt.bindString(2, sibling)
                stmt.executeInsert()
                inserted++
            }
        }
        return inserted
    }

    private fun unescape(rawWord: String): String =
        rawWord.replace("\\'", "'").replace("\\\\", "\\")

    companion object {
        private const val CONNECT_TIMEOUT_MS = 15_000
        private const val READ_TIMEOUT_MS = 60_000
        private const val ABSOLUTE_TIMEOUT_MS = 5 * 60_000L
        private const val MAX_SYNSET_SIZE = 25

        // s(100001740,1,'entity',n,1,11). -- group 1 = synset id, group 2 =
        // word, group 3 = tagCount (corpus frequency for this word in this
        // sense; see parseSynsets's primary-sense selection).
        private val LINE_PATTERN =
            java.util.regex.Pattern.compile("""s\((\d+),\d+,'((?:\\.|[^'\\])*)',[nvasr],\d+,(\d+)\)\.""")

        // hyp(100001930,100001740). -- group 1 = child (more specific)
        // synset id, group 2 = parent (immediate hypernym) synset id.
        private val HYPERNYM_LINE_PATTERN =
            java.util.regex.Pattern.compile("""hyp\((\d+),(\d+)\)\.""")

        /**
         * Same GitHub mirror as [com.japanglify.app.domain.dictionary.
         * DictionarySources.WORDNET_SYNONYMS] -- verified live this session
         * this specific file is served individually (not just the source
         * tarball's own wn_s.pl), same WordNet license, same repo.
         */
        private const val HYPERNYM_URL =
            "https://raw.githubusercontent.com/ekaf/wordnet-prolog/WNprolog-3.0BF/prolog/wn_hyp.pl"
        private const val HYPERNYM_FALLBACK_URL =
            "https://cdn.jsdelivr.net/gh/ekaf/wordnet-prolog@WNprolog-3.0BF/prolog/wn_hyp.pl"

        private const val SYNONYM_INSERT_SQL =
            "INSERT INTO ${WordNetDatabase.TABLE} (" +
                "${WordNetDatabase.COL_WORD}, ${WordNetDatabase.COL_SYNONYM}) VALUES (?, ?)"
        private const val HYPERNYM_INSERT_SQL =
            "INSERT INTO ${WordNetDatabase.TABLE_HYPERNYMS} (" +
                "${WordNetDatabase.COL_WORD}, ${WordNetDatabase.COL_HYPERNYM_SIBLING}) VALUES (?, ?)"
    }
}
