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
        // v3: pos_class replaced comparing raw pos (see MARK_POS_AMBIGUOUS_SQL)
        // -- する has both "vs-i" and "v5r" rows, which differ as raw JMdict
        // codes but both mean VERB once mapped, so raw-code comparison alone
        // flagged it "ambiguous" and showed a "v." that added nothing (all
        // its senses are verbs; the real ambiguity is *which* verb sense,
        // which v1 doesn't disambiguate at all -- see the plan's "sense
        // disambiguation" backlog note).
        // v4: added sense_rank/gloss_count/dated so a headword can carry up
        // to a few candidate senses instead of always JMdict's first one --
        // see [com.japanglify.app.domain.dictionary.SenseSelector] for why
        // "always sense 0" was a documented v1 limitation, not a design
        // choice, and [SqliteDictionaryProvider] for how these get scored at
        // query time. The DB is a disposable, rebuildable cache (onUpgrade
        // just drops + recreates it empty), which is fine pre-release with
        // no shipped downloads to preserve; a real schema change post-
        // release would need this to also reset the persisted download
        // status back to NOT_DOWNLOADED so the app doesn't think a
        // just-emptied table is still ready.
        const val DB_VERSION = 4
        const val TABLE = "entries"
        const val COL_HEADWORD = "headword"
        const val COL_READING = "reading"
        const val COL_POS = "pos"
        const val COL_GLOSS = "gloss"
        const val COL_POS_AMBIGUOUS = "pos_ambiguous"
        /** Position in JMdict's own sense ordering for this row's sense (0 = first). */
        const val COL_SENSE_RANK = "sense_rank"
        /** How many English gloss synonyms this sense lists — [SenseSelector]'s "richness" signal. */
        const val COL_GLOSS_COUNT = "gloss_count"
        /** 1 when JMdict tags this sense arch/obs/obsc/dated, else 0. */
        const val COL_DATED = "dated"
        /**
         * The *mapped* [com.japanglify.app.domain.dictionary.PartOfSpeech]
         * enum name (e.g. "VERB"), not a raw JMdict code -- computed once at
         * insert time by calling the real
         * [com.japanglify.app.domain.dictionary.PartOfSpeech.fromJmdictCode]
         * function (never duplicated as a parallel SQL heuristic, which
         * would just be a second place for the mapping to drift out of
         * sync). Exists purely to drive [MARK_POS_AMBIGUOUS_SQL]'s
         * aggregate; [SqliteDictionaryProvider] doesn't read it back.
         */
        const val COL_POS_CLASS = "pos_class"

        const val CREATE_TABLE_SQL = """
            CREATE TABLE $TABLE (
                $COL_HEADWORD TEXT NOT NULL,
                $COL_READING TEXT,
                $COL_POS TEXT,
                $COL_GLOSS TEXT NOT NULL,
                $COL_POS_AMBIGUOUS INTEGER NOT NULL DEFAULT 0,
                $COL_POS_CLASS TEXT,
                $COL_SENSE_RANK INTEGER NOT NULL DEFAULT 0,
                $COL_GLOSS_COUNT INTEGER NOT NULL DEFAULT 1,
                $COL_DATED INTEGER NOT NULL DEFAULT 0
            )
        """
        const val CREATE_INDEX_SQL =
            "CREATE INDEX idx_$COL_HEADWORD ON $TABLE($COL_HEADWORD)"

        /**
         * Part of speech is only worth showing when it actually disambiguates
         * something *as displayed* -- a headword whose rows all map to the
         * same [com.japanglify.app.domain.dictionary.PartOfSpeech] gains
         * nothing from a "v."/"n." tag stapled onto it, even if their raw
         * JMdict codes differ. Run once, after bulk insert *and* after the
         * headword index exists (this aggregate scans by headword, so it
         * benefits from that index same as any lookup would).
         * [SqliteDictionaryProvider] reads this to decide whether to surface
         * a part of speech at all -- null POS already means "don't show it"
         * in the existing `GlossAnnotator.format()` plumbing, so no
         * rendering code changes.
         */
        const val MARK_POS_AMBIGUOUS_SQL = """
            UPDATE $TABLE SET $COL_POS_AMBIGUOUS = 1 WHERE $COL_HEADWORD IN (
                SELECT $COL_HEADWORD FROM $TABLE GROUP BY $COL_HEADWORD HAVING COUNT(DISTINCT $COL_POS_CLASS) > 1
            )
        """

        /** Standard filename for a given dictionary source, e.g. "dictionary_jmdict_en.db". */
        fun fileNameFor(sourceId: String): String = "dictionary_$sourceId.db"
    }
}
