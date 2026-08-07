package com.japanglify.app.dictionary

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

/**
 * Read-side [SQLiteOpenHelper] for a downloaded English→emoji map (CLDR
 * annotations). Same pattern as [DictionaryDatabase] -- persistent
 * `databases/` directory, `.building` file + atomic rename owned by the
 * importer ([EmojiDownloadManager]), this class purely the read side -- but
 * a genuinely different, much simpler schema (word -> emoji, not the
 * JMdict-shaped headword/reading/pos/gloss table), so it's its own class
 * rather than a reuse of [DictionaryDatabase].
 */
class EmojiDatabase(
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
        const val DB_VERSION = 2
        const val TABLE = "emoji"
        const val COL_WORD = "word"
        const val COL_EMOJI = "emoji"
        const val COL_TIER = "tier"

        /** Exact match against CLDR's `tts` short name -- see [EmojiDownloadManager]. */
        const val TIER_STRICT = "strict"
        /** Best single keyword picked from CLDR's broader annotation list -- see [EmojiDownloadManager]. */
        const val TIER_MEDIUM = "medium"

        const val CREATE_TABLE_SQL = """
            CREATE TABLE $TABLE (
                $COL_WORD TEXT NOT NULL,
                $COL_EMOJI TEXT NOT NULL,
                $COL_TIER TEXT NOT NULL
            )
        """
        const val CREATE_INDEX_SQL =
            "CREATE INDEX idx_$COL_WORD ON $TABLE($COL_WORD, $COL_TIER)"

        /** Standard filename for a given source, e.g. "emoji_cldr_emoji_en.db". */
        fun fileNameFor(sourceId: String): String = "emoji_$sourceId.db"
    }
}
