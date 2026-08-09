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
    /**
     * Tried only if [downloadUrl] fails outright (connect/HTTP-status
     * failure) -- not a load-balancer, just a second real host so one
     * origin having a bad day doesn't strand the download. Same content,
     * same format; a downloader never needs to know which one it used.
     */
    val fallbackDownloadUrl: String? = null,
    val approxDownloadSizeBytes: Long,
    val approxOnDiskSizeBytes: Long,
    /** Surfaced in About/credits — required for CC-licensed sources like JMdict. */
    val license: String,
    /** Which importer/schema `DictionaryDownloadService` should use for this source. */
    val format: DictionarySourceFormat
)

/** Distinguishes which downloader/schema a source needs — see `DictionaryDownloadService`. */
enum class DictionarySourceFormat { JMDICT_JSON, CLDR_EMOJI_XML, WORDNET_PROLOG }

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
        license = "CC BY-SA 4.0 (JMdict / EDRDG)",
        format = DictionarySourceFormat.JMDICT_JSON
    )

    /**
     * Unicode CLDR's English emoji annotations — the `type="tts"` (short
     * name) attribute per emoji is what backs the optional English→emoji
     * line ("emojifier"). Verified live this session (fetched the real
     * file, not assumed): ~295 KB uncompressed XML, small enough to fetch
     * and parse directly with no ZIP wrapper and no streaming parser (unlike
     * JMdict's 100 MB+ dataset). `downloadUrl` pins a specific CLDR release
     * tag rather than a moving branch, so a future CLDR update can't change
     * this app's behavior without an explicit version bump here.
     */
    val CLDR_EMOJI = DictionarySource(
        id = "cldr_emoji_en",
        displayName = "Emoji (English)",
        description = "Unicode CLDR's English emoji names, for the " +
            "optional emoji line under word/particle glosses. ~300 KB " +
            "download, fully offline afterward.",
        downloadUrl = "https://raw.githubusercontent.com/unicode-org/cldr/" +
            "release-48-2/common/annotations/en.xml",
        approxDownloadSizeBytes = 295_000L,
        approxOnDiskSizeBytes = 200_000L,
        license = "Unicode-3.0 (Unicode CLDR)",
        format = DictionarySourceFormat.CLDR_EMOJI_XML
    )

    /**
     * Princeton WordNet 3.0's synset-membership file (`s(id, num, 'word',
     * pos, sense, tag).` Prolog facts -- words sharing an `id` are
     * synonyms), used to expand the MEDIUM emoji-precision tier: a gloss
     * word with no exact CLDR match (e.g. "automobile") can still resolve
     * if exactly one of its real WordNet synonyms (e.g. "car") has one (see
     * [com.japanglify.app.domain.emoji.EmojiAnnotator]'s STRICT-then-MEDIUM
     * fallback). [downloadUrl]/[fallbackDownloadUrl] here cover only that
     * synset file -- `WordNetDownloadManager` separately downloads the same
     * mirror's `wn_hyp.pl` (immediate-hypernym relations, ~2 MB) to back
     * LOOSE's further one-hop-broader fallback (e.g. "jacket" via its
     * category "coat"); not modeled as a second [DictionarySource] since
     * both files are one inseparable download/status card, not something a
     * user would ever want independently. Verified live this session:
     * Princeton's own tarball (wordnetcode.princeton.edu) needs on-device
     * TAR parsing Android has no built-in support for and was very slow
     * from this network; the `ekaf/wordnet-prolog` GitHub mirror
     * re-publishes both files individually (WordNet license, unmodified) as
     * plain text needing no archive handling at all, with jsdelivr's
     * GitHub-mirroring CDN as a real second host (not a load balancer --
     * literally a different origin) if raw.githubusercontent.com has a bad
     * day.
     */
    val WORDNET_SYNONYMS = DictionarySource(
        id = "wordnet_synonyms_en",
        displayName = "Synonyms (English)",
        description = "Princeton WordNet's English synonym and category " +
            "data, used to widen emoji matching (Medium/Loose precision) " +
            "beyond exact names -- e.g. \"automobile\" matching via its " +
            "synonym \"car\", or \"jacket\" via its category \"coat\". " +
            "~9 MB download, fully offline afterward.",
        downloadUrl = "https://raw.githubusercontent.com/ekaf/wordnet-prolog/" +
            "WNprolog-3.0BF/prolog/wn_s.pl",
        fallbackDownloadUrl = "https://cdn.jsdelivr.net/gh/ekaf/wordnet-prolog@" +
            "WNprolog-3.0BF/prolog/wn_s.pl",
        approxDownloadSizeBytes = 9_600_000L,
        approxOnDiskSizeBytes = 11_500_000L,
        license = "WordNet 3.0 (Princeton University)",
        format = DictionarySourceFormat.WORDNET_PROLOG
    )

    val ALL: List<DictionarySource> = listOf(JMDICT_ENGLISH, CLDR_EMOJI, WORDNET_SYNONYMS)

    fun byId(id: String?): DictionarySource? = ALL.firstOrNull { it.id == id }
}
