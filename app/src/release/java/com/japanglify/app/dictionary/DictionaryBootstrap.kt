package com.japanglify.app.dictionary

import android.content.Context
import com.japanglify.app.domain.dictionary.GlossAnnotator

/**
 * Release-build implementation of the debug/release-split `DictionaryBootstrap`
 * (same package + file name, defined once per build type — never in `main` —
 * so Android Gradle Plugin's `main ∪ <buildType>` source-set merge picks
 * exactly one implementation per variant with no duplicate-class conflict).
 *
 * Constructs the live [GlossAnnotator] the app should use right now, or
 * null when no dictionary is ready to query. This is a placeholder until
 * the download pipeline and its persisted "ready" state exist — always
 * null for now, so `includeGlosses` has nothing to show yet in a release
 * build. The debug build's counterpart seeds a tiny fixed dictionary
 * instead, so this feature is live-device-verifiable before the real
 * download pipeline exists — see that file's doc comment.
 */
object DictionaryBootstrap {
    fun createGlossAnnotator(context: Context): GlossAnnotator? = null
}
