package com.japanglify.app.dictionary

import android.content.Context
import com.japanglify.app.domain.dictionary.DictionaryEntry
import com.japanglify.app.domain.dictionary.GlossAnnotator
import com.japanglify.app.domain.dictionary.PartOfSpeech

/**
 * [GlossAnnotator.DictionaryProvider] backed by a [DictionaryDatabase].
 * One indexed `SELECT` per lookup — v1 always takes the first matching row
 * (see the plan's "sense disambiguation" backlog note for why that's a
 * documented future target, not an oversight).
 *
 * The `pos` column holds the dictionary source's own raw code (e.g. JMdict's
 * "v5r", "n", "prt") rather than a pre-mapped [PartOfSpeech] — mapping
 * happens once, here, at read time via [PartOfSpeech.fromJmdictCode], so
 * both a real downloaded dictionary and the debug seed data (which also
 * stores raw JMdict-style codes) go through the identical code path.
 */
class SqliteDictionaryProvider(
    context: Context,
    sourceId: String
) : GlossAnnotator.DictionaryProvider {

    private val dbHelper = DictionaryDatabase(context, DictionaryDatabase.fileNameFor(sourceId))

    override fun lookup(baseForm: String): DictionaryEntry? {
        val db = dbHelper.readableDatabase
        db.query(
            DictionaryDatabase.TABLE,
            arrayOf(
                DictionaryDatabase.COL_HEADWORD,
                DictionaryDatabase.COL_READING,
                DictionaryDatabase.COL_POS,
                DictionaryDatabase.COL_GLOSS
            ),
            "${DictionaryDatabase.COL_HEADWORD} = ?",
            arrayOf(baseForm),
            null,
            null,
            null,
            "1"
        ).use { cursor ->
            if (!cursor.moveToFirst()) return null
            val posCode = cursor.getString(2)
            return DictionaryEntry(
                headword = cursor.getString(0),
                reading = cursor.getString(1),
                partOfSpeech = posCode?.let { PartOfSpeech.fromJmdictCode(it) },
                gloss = cursor.getString(3)
            )
        }
    }
}
