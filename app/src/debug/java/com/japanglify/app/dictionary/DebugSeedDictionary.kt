package com.japanglify.app.dictionary

import android.content.Context
import com.japanglify.app.domain.dictionary.PartOfSpeech

/**
 * Inserts a small, fixed set of real dictionary entries the first time it's
 * called, so [DictionaryBootstrap] (debug builds only) has real data to
 * look up against without needing the download pipeline. Idempotent — a
 * non-empty table means a previous run already seeded it, so this is a
 * cheap no-op on every subsequent app start.
 *
 * Covers common words from this project's own live-testing sentences this
 * session (日本語を勉強する; すべての被造物に福音を語れは、多分マルコの
 * 一番最後の章だったと思う。 — the [com.japanglify.app.domain
 * .RealDictionaryIntegrationTest] fixture) plus enough general vocabulary
 * to be a meaningful smoke test, not just an echo chamber for one sentence.
 */
object DebugSeedDictionary {
    private const val SOURCE_ID = "debug_seed"

    fun ensureSeeded(context: Context) {
        val helper = DictionaryDatabase(context, DictionaryDatabase.fileNameFor(SOURCE_ID))
        val db = helper.writableDatabase
        val alreadySeeded = db.rawQuery(
            "SELECT COUNT(*) FROM ${DictionaryDatabase.TABLE}",
            null
        ).use { cursor ->
            cursor.moveToFirst()
            cursor.getInt(0) > 0
        }
        if (alreadySeeded) return

        db.beginTransaction()
        try {
            for (entry in ENTRIES) {
                db.execSQL(
                    "INSERT INTO ${DictionaryDatabase.TABLE} (" +
                        "${DictionaryDatabase.COL_HEADWORD}, ${DictionaryDatabase.COL_READING}, " +
                        "${DictionaryDatabase.COL_POS}, ${DictionaryDatabase.COL_GLOSS}, " +
                        "${DictionaryDatabase.COL_POS_CLASS}) " +
                        "VALUES (?, ?, ?, ?, ?)",
                    arrayOf(
                        entry.headword, entry.reading, entry.pos, entry.gloss,
                        PartOfSpeech.fromJmdictCode(entry.pos).name
                    )
                )
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        // Same POS-minimalism rule the real importer applies (see
        // DictionaryDatabase.MARK_POS_AMBIGUOUS_SQL) -- keeps debug-seed
        // testing representative of real downloaded-dictionary behavior.
        db.execSQL(DictionaryDatabase.MARK_POS_AMBIGUOUS_SQL)
    }

    /**
     * [pos] is a raw JMdict-style code (see
     * [com.japanglify.app.domain.dictionary.PartOfSpeech.fromJmdictCode]),
     * not a display abbreviation.
     */
    private data class SeedEntry(val headword: String, val reading: String?, val pos: String, val gloss: String)

    private val ENTRIES = listOf(
        SeedEntry("日本語", "にほんご", "n", "Japanese"),
        SeedEntry("勉強", "べんきょう", "n", "study"),
        SeedEntry("する", "する", "vs-i", "to do"),
        SeedEntry("を", null, "prt", "object marker"),
        SeedEntry("は", null, "prt", "topic marker"),
        SeedEntry("が", null, "prt", "subject marker"),
        SeedEntry("の", null, "prt", "possessive / nominalizing particle"),
        SeedEntry("に", null, "prt", "target / location particle"),
        SeedEntry("出来る", "できる", "v1", "to be able to; can"),
        SeedEntry("行く", "いく", "v5k-s", "to go"),
        SeedEntry("思う", "おもう", "v5u", "to think"),
        SeedEntry("多分", "たぶん", "adv", "probably; perhaps"),
        SeedEntry("最後", "さいご", "n", "last; final"),
        SeedEntry("章", "しょう", "n", "chapter"),
        SeedEntry("一番", "いちばん", "adv", "most; number one"),
        SeedEntry("福音", "ふくいん", "n", "gospel"),
        SeedEntry("被造物", "ひぞうぶつ", "n", "creature; created being"),
        SeedEntry("語る", "かたる", "v5r", "to tell; to narrate"),
        SeedEntry("紙", "かみ", "n", "paper"),
        SeedEntry("水", "みず", "n", "water"),
        SeedEntry("食べる", "たべる", "v1", "to eat"),
        SeedEntry("大きい", "おおきい", "adj-i", "big; large")
    )
}
