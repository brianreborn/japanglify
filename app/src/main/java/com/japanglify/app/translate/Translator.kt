package com.japanglify.app.translate

import android.os.Handler
import android.os.Looper
import java.util.concurrent.Executors

/**
 * Async facade over a [TranslationProvider]. Deliberately built on a plain
 * background thread rather than coroutines — the codebase uses none,
 * only `Handler`/`Runnable` (e.g. [com.japanglify.app.clipboard.ClipboardAssistService],
 * the Settings live-preview debounce) — so this matches the existing idiom.
 *
 * Nothing here — including the background thread — exists until the first
 * call. See [com.japanglify.app.JapanglifyApp.translator]'s `by lazy`: that,
 * plus every call site's `isTranslationEnabled()` guard running first, is
 * what keeps this feature at zero cost for the users who leave it off.
 */
class Translator(
    private val provider: TranslationProvider = GoogleWebTranslator()
) {
    private val executor = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())

    /** [onResult] always runs on the main thread; null means "no 4th line," not an error. */
    fun translateAsync(text: String, onResult: (String?) -> Unit) {
        executor.execute {
            val result = runCatching { provider.translate(text) }.getOrNull()
            mainHandler.post { onResult(result) }
        }
    }
}
