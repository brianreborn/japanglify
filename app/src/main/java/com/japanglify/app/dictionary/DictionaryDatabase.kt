package com.japanglify.app.dictionary

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

/**
 * Read-side [SQLiteOpenHelper] for a downloaded dictionary. [name] is a
 * plain filename (e.g. "dictionary_jmdict_en.db") — `SQLiteOpenHelper`
 * places it under the app's private, persistent `databases/` directory
 * automatically, never `cacheDir`, so a downloaded dictionary can't be
 * silently reclaimed by the OS under storage pressure.
 *
 * Raw `SQLiteOpenHelper`, not Room: one indexed `SELECT` is all this needs,
 * and the platform primitive ships with zero new Gradle dependency —
 * matching this codebase's demonstrated preference (raw `HttpURLConnection`
 * over OkHttp, `Handler`/`Runnable` over coroutines) for the platform's own
 * tools over adding a library for something this small.
 *
 * The download/import pipeline builds a *separate* file (e.g.
 * `<name>.building`) with raw `SQLiteDatabase` calls and atomically renames
 * it over this class's live file only after the whole import transaction
 * commits — this class is purely the read side. Its [onCreate] schema only
 * actually runs for a debug-seeded database (see the `debug` source set's
 * `DictionaryBootstrap`/`DebugSeedDictionary`), since a downloaded DB
 * already has its schema baked in by the importer before the atomic rename.
 */
class DictionaryDatabase(
    context: Context,
    name: String
) : SQLiteOpenHelper(context, name, null, DB_VERSION) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(CREATE_TABLE_SQL)
        db.execSQL(CREATE_INDEX_SQL)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // The DB is a disposable, rebuildable cache of a downloaded source
        // (source of truth is the hosted release asset) — drop and
        // recreate rather than migrate incrementally.
        db.execSQL("DROP TABLE IF EXISTS $TABLE")
        onCreate(db)
    }

    companion object {
        const val DB_VERSION = 1
        const val TABLE = "entries"
        const val COL_HEADWORD = "headword"
        const val COL_READING = "reading"
        const val COL_POS = "pos"
        const val COL_GLOSS = "gloss"

        const val CREATE_TABLE_SQL = """
            CREATE TABLE $TABLE (
                $COL_HEADWORD TEXT NOT NULL,
                $COL_READING TEXT,
                $COL_POS TEXT,
                $COL_GLOSS TEXT NOT NULL
            )
        """
        const val CREATE_INDEX_SQL =
            "CREATE INDEX idx_$COL_HEADWORD ON $TABLE($COL_HEADWORD)"

        /** Standard filename for a given dictionary source, e.g. "dictionary_jmdict_en.db". */
        fun fileNameFor(sourceId: String): String = "dictionary_$sourceId.db"
    }
}
