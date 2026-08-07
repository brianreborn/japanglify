package com.japanglify.app.domain.dictionary

/**
 * A downloadable, on-demand dictionary data source. Pure data — no
 * Android/network/DB dependency — so the Settings picker (and any future
 * second source) are purely additive: add an entry to [DictionarySources],
 * nothing else needs to change to make it selectable.
 */
data class DictionarySource(
    val id: String,
    val displayName: String,
    val description: String,
    val downloadUrl: String,
    val approxDownloadSizeBytes: Long,
    val approxOnDiskSizeBytes: Long,
    /** Surfaced in About/credits — required for CC-licensed sources like JMdict. */
    val license: String
)

object DictionarySources {
    /**
     * Standard open Japanese-English dictionary (EDRDG / Jim Breen's
     * project), CC BY-SA 4.0. Sourced via the jmdict-simplified project's
     * pre-built English-only JSON release — verified live this session
     * (fetched the real GitHub release listing and a sample of the actual
     * JSON, not assumed): a plain .zip, not .tgz, so Java's built-in
     * java.util.zip.ZipInputStream reads it directly with no TAR-parsing
     * dependency needed. downloadUrl points directly at jmdict-simplified's
     * own release for now — a working stand-in until re-packaged to a URL
     * this project controls (a maintainer-side task, not runtime code; see
     * the plan's "Data source: JMdict" section for why that matters for
     * long-term hosting reliability, separate from this being a genuinely
     * working URL today).
     */
    val JMDICT_ENGLISH = DictionarySource(
        id = "jmdict_en",
        displayName = "JMdict (English)",
        description = "Standard open Japanese–English dictionary " +
            "(EDRDG / Jim Breen's project). ~10–15 MB download, " +
            "fully offline afterward.",
        downloadUrl = "https://github.com/scriptin/jmdict-simplified/releases/" +
            "download/3.6.2%2B20260803141815/jmdict-eng-3.6.2%2B20260803141815.json.zip",
        approxDownloadSizeBytes = 11_475_140L,
        // Measured live from a real import, not estimated: ~451k rows
        // (one per kanji spelling plus a kana-reading row whenever it
        // differs from every kanji spelling -- needed so common words like
        // する resolve at all, since Kuromoji's baseForm for them is the
        // bare kana form even when JMdict itself files the entry under a
        // kanji spelling) came to ~40 MB on disk.
        approxOnDiskSizeBytes = 40_500_000L,
        license = "CC BY-SA 4.0 (JMdict / EDRDG)"
    )

    val ALL: List<DictionarySource> = listOf(JMDICT_ENGLISH)

    fun byId(id: String?): DictionarySource? = ALL.firstOrNull { it.id == id }
}
