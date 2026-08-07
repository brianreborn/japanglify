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
     * pre-built English-only JSON releases, re-packaged to a URL this
     * project controls rather than depending on a third party's ongoing
     * hosting — see the plan's "Data source: JMdict" section for why, and
     * for the maintainer-side packaging step this placeholder URL assumes.
     */
    val JMDICT_ENGLISH = DictionarySource(
        id = "jmdict_en",
        displayName = "JMdict (English)",
        description = "Standard open Japanese–English dictionary " +
            "(EDRDG / Jim Breen's project). ~10–15 MB download, " +
            "fully offline afterward.",
        downloadUrl = "https://github.com/brianreborn/japanglify/releases/" +
            "download/dictionaries/jmdict-en-latest.json.gz",
        approxDownloadSizeBytes = 12_000_000L,
        approxOnDiskSizeBytes = 20_000_000L,
        license = "CC BY-SA 4.0 (JMdict / EDRDG)"
    )

    val ALL: List<DictionarySource> = listOf(JMDICT_ENGLISH)

    fun byId(id: String?): DictionarySource? = ALL.firstOrNull { it.id == id }
}
