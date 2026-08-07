package com.japanglify.app.dictionary

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

/**
 * Read-side [SQLiteOpenHelper] for a downloaded WordNet synonym map (see
 * [WordNetDownloadManager]). Flattened `word -> synonym` pairs, one row per
 * directed pair -- not the raw synset structure -- since every real lookup
 * only ever needs "what are this word's synonyms," never synset ids.
 */
class WordNetDatabase(
    context: Context,
    name: String
) : SQLiteOpenHelper(context, name, null, DB_VERSION) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(CREATE_TABLE_SQL)
        db.execSQL(CREATE_INDEX_SQL)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS $TABLE")
        onCreate(db)
    }

    companion object {
        const val DB_VERSION = 1
        const val TABLE = "synonyms"
        const val COL_WORD = "word"
        const val COL_SYNONYM = "synonym"

        const val CREATE_TABLE_SQL = """
            CREATE TABLE $TABLE (
                $COL_WORD TEXT NOT NULL,
                $COL_SYNONYM TEXT NOT NULL
            )
        """
        const val CREATE_INDEX_SQL =
            "CREATE INDEX idx_$COL_WORD ON $TABLE($COL_WORD)"

        /** Standard filename for a given source, e.g. "wordnet_wordnet_synonyms_en.db". */
        fun fileNameFor(sourceId: String): String = "wordnet_$sourceId.db"
    }
}
