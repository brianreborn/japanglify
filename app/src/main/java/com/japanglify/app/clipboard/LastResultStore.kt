package com.japanglify.app.clipboard

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.SystemClock
import com.japanglify.app.R

/**
 * Holds the most recent Japanglify output for “tap notification → copy”,
 * and guards against re-processing our own clipboard writes (notify → copy loop).
 */
object LastResultStore {
    private const val PREFS = "japanglify_clipboard_assist"
    private const val KEY_RESULT = "last_result"
    private const val KEY_SOURCE = "last_source"

    /** ClipData label we stamp on every outbound write — ignored on the way back in. */
    const val CLIP_LABEL = "Japanglify"

    /** Ignore all clipboard-change handling while this is in the future (elapsedRealtime). */
    @Volatile
    private var suppressUntilElapsed: Long = 0L

    private const val SUPPRESS_MS = 2_500L

    @Volatile
    var lastResult: String? = null
        private set

    @Volatile
    var lastSource: String? = null
        private set

    /** Package that triggered the Copy this result came from, when known. */
    @Volatile
    var lastHostPackage: String? = null
        private set

    /** On-screen width (px) of the host's input field/window at Copy time, when known. */
    @Volatile
    var lastHostFieldWidthPx: Int? = null
        private set

    /** Whether the focused node at Copy time was an editable text field we can write into. */
    @Volatile
    var lastHostFieldEditable: Boolean = false
        private set

    fun rememberHost(packageName: String?, fieldWidthPx: Int?, fieldEditable: Boolean = false) {
        lastHostPackage = packageName
        lastHostFieldWidthPx = fieldWidthPx
        lastHostFieldEditable = fieldEditable
    }

    /** Hosts known to widen every glyph on a line (even plain Latin) once it contains CJK. */
    private val IMAGE_PREFERRED_HOSTS = setOf(
        "com.discord",
        "com.twitter.android",
        "com.instagram.android"
    )

    fun hostPrefersImage(): Boolean = lastHostPackage in IMAGE_PREFERRED_HOSTS

    /** Exact texts we placed on the clipboard (ring buffer). */
    private val selfWrittenClips = ArrayDeque<String>()

    fun save(context: Context, source: String, result: String) {
        lastSource = source
        lastResult = result
        // Result is “ours” even before the user taps Copy — if anything
        // puts it on the clipboard, do not re-Japanglify it.
        rememberSelfWrite(result)
        context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_SOURCE, source)
            .putString(KEY_RESULT, result)
            .apply()
    }

    fun load(context: Context): String? {
        lastResult?.let { return it }
        val prefs = context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val fromPrefs = prefs.getString(KEY_RESULT, null)
        lastResult = fromPrefs
        lastSource = prefs.getString(KEY_SOURCE, null)
        if (fromPrefs != null) rememberSelfWrite(fromPrefs)
        return fromPrefs
    }

    fun rememberSelfWrite(text: String) {
        if (text.isEmpty()) return
        synchronized(selfWrittenClips) {
            // Move to front if already present
            selfWrittenClips.remove(text)
            selfWrittenClips.addFirst(text)
            while (selfWrittenClips.size > 24) selfWrittenClips.removeLast()
        }
    }

    /** Call immediately before [ClipboardManager.setPrimaryClip] for our own data. */
    fun beginOutgoingWrite(text: String) {
        rememberSelfWrite(text)
        suppressUntilElapsed = SystemClock.elapsedRealtime() + SUPPRESS_MS
    }

    fun isSuppressing(): Boolean =
        SystemClock.elapsedRealtime() < suppressUntilElapsed

    fun isSelfWrite(text: String): Boolean {
        if (text.isEmpty()) return false
        if (text == lastResult) return true
        synchronized(selfWrittenClips) {
            if (selfWrittenClips.any { it == text }) return true
        }
        return false
    }

    /**
     * Whether this clipboard snapshot should be ignored entirely (our write,
     * suppress window, or our clip label).
     */
    fun shouldIgnoreClipboard(context: Context, raw: String?, clipLabel: CharSequence?): Boolean {
        if (isSuppressing()) return true
        val label = clipLabel?.toString()
        if (label == CLIP_LABEL || label == context.getString(R.string.clipboard_label)) {
            return true
        }
        if (raw != null && isSelfWrite(raw.trim())) return true
        return false
    }

    /**
     * Write [text] to the clipboard with our label and anti-recursion guards.
     * Always use this instead of raw [ClipboardManager.setPrimaryClip].
     */
    fun writeToClipboard(context: Context, text: String) {
        beginOutgoingWrite(text)
        val cm = context.getSystemService(ClipboardManager::class.java) ?: return
        cm.setPrimaryClip(ClipData.newPlainText(CLIP_LABEL, text))
    }
}
