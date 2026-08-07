package com.japanglify.app.dictionary

import android.content.Context
import com.japanglify.app.domain.EmojiPrecisionTier
import com.japanglify.app.domain.dictionary.DictionarySources
import com.japanglify.app.domain.emoji.EmojiAnnotator

/**
 * [EmojiAnnotator.EmojiProvider] backed by an [EmojiDatabase] (exact CLDR
 * `tts` matches) plus, for MEDIUM, a [WordNetDatabase] synonym expansion:
 * a word with no exact match is looked up in WordNet, and if exactly one of
 * its real synonyms (e.g. "car" for "automobile") has an exact CLDR match,
 * that's used -- multiple differing candidates are ambiguous and dropped,
 * same "no symbol reused for a different word" rule as STRICT. If the
 * WordNet source hasn't been downloaded, [WordNetDatabase] just opens an
 * empty table (`SQLiteOpenHelper` auto-creates it), so MEDIUM harmlessly
 * behaves like STRICT until it has been. LOOSE isn't built yet (see
 * [EmojiPrecisionTier]) so it behaves like STRICT for now.
 */
class SqliteEmojiProvider(
    context: Context,
    sourceId: String
) : EmojiAnnotator.EmojiProvider {

    private val emojiDbHelper = EmojiDatabase(context, EmojiDatabase.fileNameFor(sourceId))
    private val wordNetDbHelper = WordNetDatabase(
        context,
        WordNetDatabase.fileNameFor(DictionarySources.WORDNET_SYNONYMS.id)
    )

    override fun lookup(englishWord: String, tier: EmojiPrecisionTier): String? {
        queryStrict(englishWord)?.let { return it }
        if (tier != EmojiPrecisionTier.MEDIUM) return null
        val candidates = synonymsOf(englishWord).mapNotNull { queryStrict(it) }.distinct()
        return candidates.singleOrNull()
    }

    private fun queryStrict(englishWord: String): String? {
        val db = emojiDbHelper.readableDatabase
        db.query(
            EmojiDatabase.TABLE,
            arrayOf(EmojiDatabase.COL_EMOJI),
            "${EmojiDatabase.COL_WORD} = ? AND ${EmojiDatabase.COL_TIER} = ?",
            arrayOf(englishWord, EmojiDatabase.TIER_STRICT),
            null,
            null,
            null,
            "1"
        ).use { cursor ->
            if (!cursor.moveToFirst()) return null
            return cursor.getString(0)
        }
    }

    private fun synonymsOf(englishWord: String): List<String> {
        val db = wordNetDbHelper.readableDatabase
        db.query(
            WordNetDatabase.TABLE,
            arrayOf(WordNetDatabase.COL_SYNONYM),
            "${WordNetDatabase.COL_WORD} = ?",
            arrayOf(englishWord),
            null,
            null,
            null
        ).use { cursor ->
            val result = ArrayList<String>()
            while (cursor.moveToNext()) result += cursor.getString(0)
            return result
        }
    }
}
