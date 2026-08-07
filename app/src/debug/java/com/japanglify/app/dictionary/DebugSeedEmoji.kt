package com.japanglify.app.dictionary

import android.content.Context

/**
 * Inserts a small, fixed word→emoji map the first time it's called, so
 * [DictionaryBootstrap] (debug builds only) has real data to look up
 * against before a real CLDR download exists -- mirrors
 * [DebugSeedDictionary]'s role exactly, for the emoji feature.
 *
 * "paper"/"water" deliberately match [DebugSeedDictionary]'s 紙/水 glosses
 * so the full elision pipeline (Japanese word -> gloss -> emoji -> English
 * word dropped) is live-demoable with zero setup, not just individually
 * testable; the rest are generic real CLDR short names for basic coverage.
 */
object DebugSeedEmoji {
    private const val SOURCE_ID = "debug_seed_emoji"

    fun ensureSeeded(context: Context) {
        val helper = EmojiDatabase(context, EmojiDatabase.fileNameFor(SOURCE_ID))
        val db = helper.writableDatabase
        val alreadySeeded = db.rawQuery(
            "SELECT COUNT(*) FROM ${EmojiDatabase.TABLE}",
            null
        ).use { cursor ->
            cursor.moveToFirst()
            cursor.getInt(0) > 0
        }
        if (alreadySeeded) return

        db.beginTransaction()
        try {
            for ((word, emoji) in ENTRIES) {
                db.execSQL(
                    "INSERT INTO ${EmojiDatabase.TABLE} " +
                        "(${EmojiDatabase.COL_WORD}, ${EmojiDatabase.COL_EMOJI}, ${EmojiDatabase.COL_TIER}) " +
                        "VALUES (?, ?, ?)",
                    arrayOf(word, emoji, EmojiDatabase.TIER_STRICT)
                )
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    private val ENTRIES = listOf(
        "paper" to "📄",
        "water" to "💧",
        "dog" to "🐕",
        "cat" to "🐈",
        "books" to "📚",
        "automobile" to "🚗"
    )
}
