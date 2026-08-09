package com.japanglify.app.dictionary

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

/**
 * Read-side [SQLiteOpenHelper] for a downloaded WordNet relation map (see
 * [WordNetDownloadManager]). Flattened `word -> related word` pairs, one row
 * per directed pair -- not the raw synset structure -- since every real
 * lookup only ever needs "what are this word's synonyms/hypernym-siblings,"
 * never synset ids. Two tables, not a `relation` column on one: synonyms
 * (same-synset, "is the same concept as") and hypernym-siblings
 * (one-hop-broader-synset, "is a kind of") are semantically different
 * strengths of relation -- see [com.japanglify.app.domain.EmojiPrecisionTier]
 * MEDIUM (synonyms only) vs LOOSE (synonyms, then hypernym-siblings).
 */
class WordNetDatabase(
    context: Context,
    name: String
) : SQLiteOpenHelper(context, name, null, DB_VERSION) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(CREATE_TABLE_SQL)
        db.execSQL(CREATE_INDEX_SQL)
        db.execSQL(CREATE_HYPERNYM_TABLE_SQL)
        db.execSQL(CREATE_HYPERNYM_INDEX_SQL)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS $TABLE")
        db.execSQL("DROP TABLE IF EXISTS $TABLE_HYPERNYMS")
        onCreate(db)
    }

    companion object {
        const val DB_VERSION = 2
        const val TABLE = "synonyms"
        const val COL_WORD = "word"
        const val COL_SYNONYM = "synonym"

        const val TABLE_HYPERNYMS = "hypernym_siblings"
        const val COL_HYPERNYM_SIBLING = "sibling"

        const val CREATE_TABLE_SQL = """
            CREATE TABLE $TABLE (
                $COL_WORD TEXT NOT NULL,
                $COL_SYNONYM TEXT NOT NULL
            )
        """
        const val CREATE_INDEX_SQL =
            "CREATE INDEX idx_$COL_WORD ON $TABLE($COL_WORD)"

        const val CREATE_HYPERNYM_TABLE_SQL = """
            CREATE TABLE $TABLE_HYPERNYMS (
                $COL_WORD TEXT NOT NULL,
                $COL_HYPERNYM_SIBLING TEXT NOT NULL
            )
        """
        const val CREATE_HYPERNYM_INDEX_SQL =
            "CREATE INDEX idx_hyp_$COL_WORD ON $TABLE_HYPERNYMS($COL_WORD)"

        /** Standard filename for a given source, e.g. "wordnet_wordnet_synonyms_en.db". */
        fun fileNameFor(sourceId: String): String = "wordnet_$sourceId.db"
    }
}
