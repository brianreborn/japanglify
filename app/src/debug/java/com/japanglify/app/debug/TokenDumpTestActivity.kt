package com.japanglify.app.debug

import android.app.Activity
import android.graphics.Color
import android.os.Bundle
import android.widget.TextView
import com.japanglify.app.data.KuromojiReadingProvider

/**
 * Debug-build-only seam: dumps Kuromoji's raw per-token tags (surface,
 * isBoundToPrevious, isParticle, baseForm) for [EXTRA_TEXT] as plain text,
 * bypassing JapaneseAnalyzer/rendering entirely -- for diagnosing
 * word-gap/wrap bugs that trace back to how a specific token got tagged,
 * without guessing from rendered output alone.
 *
 * Never present in a release build: lives under `app/src/debug/`.
 */
class TokenDumpTestActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val text = intent?.getStringExtra(EXTRA_TEXT) ?: ""
        val tokens = KuromojiReadingProvider().tokenize(text)
        val dump = tokens.joinToString("\n") {
            "[${it.surface}] bound=${it.isBoundToPrevious} particle=${it.isParticle} " +
                "reading=${it.reading} base=${it.baseForm}"
        }
        setContentView(
            TextView(this).apply {
                setText(dump)
                textSize = 14f
                setPadding(24, 24, 24, 24)
                setBackgroundColor(Color.WHITE)
                setTextColor(Color.BLACK)
            }
        )
    }

    companion object {
        const val EXTRA_TEXT = "text"
    }
}
