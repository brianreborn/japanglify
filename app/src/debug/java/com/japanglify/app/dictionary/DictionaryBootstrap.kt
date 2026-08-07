package com.japanglify.app.dictionary

import android.content.Context
import com.japanglify.app.domain.dictionary.GlossAnnotator

/**
 * Debug-build implementation of the debug/release-split `DictionaryBootstrap`
 * (same package + file name, defined once per build type — deliberately
 * *not* also in `main`, since Kotlin source files don't support the kind of
 * override that manifest/resource merging does; two same-named classes in
 * `main` and `debug` would just be a duplicate-class compile error for the
 * debug variant. Android Gradle Plugin instead merges `main ∪ <buildType>`
 * per variant, so defining this once in `debug/` and once in `release/`
 * — see that file's doc comment — gives each variant exactly one
 * implementation with zero conflict).
 *
 * Seeds a tiny (~20-entry) fixed dictionary into a dedicated debug-only
 * SQLite file on first use, so word/particle glosses are live-device-
 * testable (Try-It card, `include_glosses` on) before the real download +
 * ETL pipeline exists. Never present in a release build.
 */
object DictionaryBootstrap {
    private const val DEBUG_SOURCE_ID = "debug_seed"

    fun createGlossAnnotator(context: Context): GlossAnnotator {
        DebugSeedDictionary.ensureSeeded(context)
        return GlossAnnotator(SqliteDictionaryProvider(context, DEBUG_SOURCE_ID))
    }
}
