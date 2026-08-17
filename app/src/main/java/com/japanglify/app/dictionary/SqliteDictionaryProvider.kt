package com.japanglify.app.dictionary

import android.content.Context
import com.japanglify.app.domain.dictionary.DictionaryEntry
import com.japanglify.app.domain.dictionary.GlossAnnotator
import com.japanglify.app.domain.dictionary.PartOfSpeech
import com.japanglify.app.domain.dictionary.SenseCandidate
import com.japanglify.app.domain.dictionary.SenseSelector
import com.japanglify.app.domain.dictionary.SenseWeights

/**
 * [GlossAnnotator.DictionaryProvider] backed by a [DictionaryDatabase].
 * One indexed `SELECT` per lookup, fetching every candidate sense row a
 * headword has (see [DictionaryDownloadManager.insertWord] — up to a few
 * per headword, one per JMdict sense) and handing them to [SenseSelector]
 * to pick the winner using the caller's [SenseWeights] — live, every call,
 * so changing the sense-selection setting takes effect immediately with no
 * dictionary re-import needed.
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

    override fun lookup(baseForm: String, weights: SenseWeights): DictionaryEntry? {
        val db = dbHelper.readableDatabase
        val candidates = ArrayList<SenseCandidate>()
        db.query(
            DictionaryDatabase.TABLE,
            arrayOf(
                DictionaryDatabase.COL_READING,
                DictionaryDatabase.COL_POS,
                DictionaryDatabase.COL_GLOSS,
                DictionaryDatabase.COL_GLOSS_COUNT,
                DictionaryDatabase.COL_DATED,
                DictionaryDatabase.COL_SENSE_RANK
            ),
            "${DictionaryDatabase.COL_HEADWORD} = ?",
            arrayOf(baseForm),
            null,
            null,
            null
        ).use { cursor ->
            while (cursor.moveToNext()) {
                val reading = cursor.getString(0)
                val posCode = cursor.getString(1)
                val candidate = SenseCandidate(
                    gloss = cursor.getString(2),
                    partOfSpeech = posCode?.let { PartOfSpeech.fromJmdictCode(it) },
                    glossCount = cursor.getInt(3),
                    isDated = cursor.getInt(4) != 0,
                    rank = cursor.getInt(5),
                    reading = reading
                )
                candidates += candidate
            }
        }
        val winner = SenseSelector.pickBest(candidates, weights) ?: return null
        return DictionaryEntry(
            headword = baseForm,
            reading = winner.reading,
            partOfSpeech = winner.partOfSpeech,
            gloss = winner.gloss
        )
    }
}
