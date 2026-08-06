package com.japanglify.app.clipboard

import android.content.ClipboardManager
import android.content.Context
import com.japanglify.app.JapanglifyApp
import com.japanglify.app.data.PreferencesRepository

/**
 * Shared “read clipboard → Japanglify → result notification” pipeline.
 *
 * Assist is active when the Accessibility service is connected **or** the
 * in-app “Process on Copy” switch is on. (Users often enable only the system
 * Accessibility toggle and never flip the in-app switch — that used to do
 * nothing except show the a11y icon.)
 */
object ClipboardProcessor {

    const val MAX_CHARS = 8_000

    private var lastHandledRaw: String? = null

    fun isAssistWanted(context: Context): Boolean {
        // Accessibility service connected ⇒ user turned us on in system settings
        if (JapanglifyAccessibilityService.isRunning()) return true
        return androidx.preference.PreferenceManager
            .getDefaultSharedPreferences(context)
            .getBoolean(PreferencesRepository.KEY_CLIPBOARD_ASSIST, false)
    }

    /**
     * Quick per-notification pause, independent of the full Settings switch.
     * Governs both Copy (shows result notification) and Cut (auto-replaces
     * in place) — one switch for both, toggled from the status notification.
     */
    fun isCopyHookPaused(context: Context): Boolean =
        androidx.preference.PreferenceManager
            .getDefaultSharedPreferences(context)
            .getBoolean(PreferencesRepository.KEY_COPY_HOOK_PAUSED, false)

    data class ClipSnapshot(
        val text: String?,
        val label: CharSequence?
    )

    fun readClipboardSnapshot(context: Context): ClipSnapshot {
        return try {
            val cm = context.getSystemService(ClipboardManager::class.java)
                ?: return ClipSnapshot(null, null)
            val clip = cm.primaryClip
            val label = cm.primaryClipDescription?.label
            if (clip == null || clip.itemCount <= 0) {
                return ClipSnapshot(null, label)
            }
            val text = clip.getItemAt(0).coerceToText(context)?.toString()
            ClipSnapshot(text, label)
        } catch (_: Exception) {
            ClipSnapshot(null, null)
        }
    }

    fun readClipboard(context: Context): String? = readClipboardSnapshot(context).text

    fun processClipboardIfNew(context: Context, force: Boolean = false): ProcessOutcome {
        if (!isAssistWanted(context)) return ProcessOutcome.DISABLED

        if (!force && LastResultStore.isSuppressing()) return ProcessOutcome.SELF_WRITE

        val snap = readClipboardSnapshot(context)
        if (!force && LastResultStore.shouldIgnoreClipboard(context, snap.text, snap.label)) {
            return ProcessOutcome.SELF_WRITE
        }

        val raw = snap.text?.trim()
        if (raw.isNullOrEmpty()) return ProcessOutcome.EMPTY_OR_UNREADABLE
        if (raw.length > MAX_CHARS) return ProcessOutcome.TOO_LONG
        if (!force && raw == lastHandledRaw) return ProcessOutcome.DUPLICATE

        return processText(context, raw, force = force)
    }

    fun processText(context: Context, source: String, force: Boolean = false): ProcessOutcome {
        if (!isAssistWanted(context)) return ProcessOutcome.DISABLED
        val text = source.trim()
        if (text.isEmpty()) return ProcessOutcome.EMPTY_OR_UNREADABLE
        if (text.length > MAX_CHARS) return ProcessOutcome.TOO_LONG
        if (!force && LastResultStore.isSelfWrite(text)) return ProcessOutcome.SELF_WRITE
        if (!force && text == lastHandledRaw) return ProcessOutcome.DUPLICATE

        val app = context.applicationContext as? JapanglifyApp
            ?: return ProcessOutcome.ERROR
        val result = runCatching {
            app.engine.expand(text, app.preferences.load())
        }.getOrElse {
            return ProcessOutcome.ERROR
        }

        lastHandledRaw = text
        LastResultStore.save(context, text, result)
        ClipboardNotifications.cancelTapToProcess(context)
        ClipboardNotifications.showResult(context, result)
        return ProcessOutcome.SUCCESS
    }

    enum class ProcessOutcome {
        SUCCESS,
        DISABLED,
        EMPTY_OR_UNREADABLE,
        TOO_LONG,
        SELF_WRITE,
        DUPLICATE,
        ERROR
    }
}
