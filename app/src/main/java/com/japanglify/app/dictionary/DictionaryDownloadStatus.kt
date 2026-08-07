package com.japanglify.app.dictionary

enum class DictionaryDownloadStatus { NOT_DOWNLOADED, DOWNLOADING, PARSING, READY, FAILED }

/**
 * [percent] is 0-100 during DOWNLOADING (`Content-Length` is known upfront).
 * During PARSING it's null — the importer streams the JSON one word object
 * at a time (see [DictionaryDownloadManager]) specifically so it never has
 * to hold the whole ~200k-entry array in memory just to count it first, so
 * there's no total to compute a percentage against; [wordsImported] is the
 * running count callers can show instead ("Importing… 45,231 words").
 */
data class DictionaryDownloadProgress(
    val status: DictionaryDownloadStatus,
    val percent: Int? = null,
    val wordsImported: Int = 0,
    val errorMessage: String? = null
)
