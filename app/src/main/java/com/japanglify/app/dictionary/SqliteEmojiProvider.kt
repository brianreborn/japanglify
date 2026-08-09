package com.japanglify.app.dictionary

import android.content.Context
import com.japanglify.app.domain.EmojiPrecisionTier
import com.japanglify.app.domain.dictionary.DictionarySources
import com.japanglify.app.domain.emoji.EmojiAnnotator

/**
 * [EmojiAnnotator.EmojiProvider] backed by an [EmojiDatabase] (exact CLDR
 * `tts` matches, tier STRICT) plus two widening fallbacks, tried in order
 * of decreasing confidence once STRICT misses:
 *
 * 1. MEDIUM: a [WordNetDatabase] *synonym* expansion -- if exactly one of
 *    the word's real synonyms (e.g. "car" for "automobile") has an exact
 *    CLDR match, that's used.
 * 2. LOOSE (also includes MEDIUM's step first): if MEDIUM still misses,
 *    try [EmojiDatabase]'s own LOOSE tier (a word claimed by exactly one
 *    emoji's broader CLDR keyword list, not its `tts`), then a WordNet
 *    *hypernym-sibling* expansion (one hop broader than synonymy, e.g.
 *    "jacket" -> its category "coat" -> 🧥).
 *
 * Neither tier here checks whether the resulting emoji is "already used" by
 * some other word -- there is no such registry, deliberately. The only
 * thing that ever needs guarding against is a single word having multiple
 * *different*, competing candidates with no principled way to choose
 * between them (handled by `.distinct().singleOrNull()` below, and by
 * [EmojiDownloadManager]'s `claimUnique` on the word side) -- not whether
 * some unrelated word already resolved to the same emoji. Two words sharing
 * a symbol is fine, and expected, whenever they're close enough in meaning
 * (synonyms, or a specific word next to its own category).
 *
 * If the WordNet/CLDR sources haven't been downloaded yet, their tables are
 * just empty (`SQLiteOpenHelper` auto-creates them), so MEDIUM/LOOSE
 * harmlessly behave like STRICT until they have been.
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
        queryEmoji(englishWord, EmojiDatabase.TIER_STRICT)?.let { return it }
        if (tier == EmojiPrecisionTier.STRICT) return null

        synonymMatch(englishWord)?.let { return it }
        if (tier == EmojiPrecisionTier.MEDIUM) return null

        queryEmoji(englishWord, EmojiDatabase.TIER_LOOSE)?.let { return it }
        return hypernymMatch(englishWord)
    }

    private fun synonymMatch(englishWord: String): String? {
        val candidates = relatedWordsOf(WordNetDatabase.TABLE, WordNetDatabase.COL_SYNONYM, englishWord)
            .mapNotNull { queryEmoji(it, EmojiDatabase.TIER_STRICT) }
            .distinct()
        return candidates.singleOrNull()
    }

    private fun hypernymMatch(englishWord: String): String? {
        val candidates = relatedWordsOf(
            WordNetDatabase.TABLE_HYPERNYMS,
            WordNetDatabase.COL_HYPERNYM_SIBLING,
            englishWord
        )
            .mapNotNull { queryEmoji(it, EmojiDatabase.TIER_STRICT) }
            .distinct()
        return candidates.singleOrNull()
    }

    private fun queryEmoji(englishWord: String, dbTier: String): String? {
        val db = emojiDbHelper.readableDatabase
        db.query(
            EmojiDatabase.TABLE,
            arrayOf(EmojiDatabase.COL_EMOJI),
            "${EmojiDatabase.COL_WORD} = ? AND ${EmojiDatabase.COL_TIER} = ?",
            arrayOf(englishWord, dbTier),
            null,
            null,
            null,
            "1"
        ).use { cursor ->
            if (!cursor.moveToFirst()) return null
            return cursor.getString(0)
        }
    }

    private fun relatedWordsOf(table: String, relatedCol: String, englishWord: String): List<String> {
        val db = wordNetDbHelper.readableDatabase
        db.query(
            table,
            arrayOf(relatedCol),
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
