package com.japanglify.app.clipboard

import android.accessibilityservice.AccessibilityService
import android.content.ClipboardManager
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.japanglify.app.data.PreferencesRepository

/**
 * **Copy-first** assist for hosts without PROCESS_TEXT (Discord/X, …) and a
 * reliability layer even when PROCESS_TEXT is missing.
 *
 * Primary: detect Copy (clipboard change + “Copy” clicks + selection memory)
 * and post a result notification. Polls the clipboard while active so OEM
 * listener bugs cannot silence the hook.
 */
class JapanglifyAccessibilityService : AccessibilityService() {

    private val mainHandler = Handler(Looper.getMainLooper())
    private var clipboard: ClipboardManager? = null
    private var overlay: SelectionActionOverlay? = null

    @Volatile
    private var lastSelectedText: String? = null

    private var lastPolledClip: String? = null
    private var copyPipelineGeneration = 0
    private var pendingSelection: CapturedSelection? = null

    private val clipListener = ClipboardManager.OnPrimaryClipChangedListener {
        if (LastResultStore.isSuppressing()) return@OnPrimaryClipChangedListener
        CopyHookDiagnostics.log(this, "clipListener fired")
        scheduleCopyPipeline("clip_listener")
    }

    /** Poll clipboard even if OnPrimaryClipChangedListener is flaky. */
    private val pollRunnable = object : Runnable {
        override fun run() {
            try {
                if (!ClipboardProcessor.isAssistWanted(this@JapanglifyAccessibilityService)) {
                    return
                }
                if (LastResultStore.isSuppressing()) {
                    mainHandler.postDelayed(this, POLL_MS)
                    return
                }
                val snap = ClipboardProcessor.readClipboardSnapshot(this@JapanglifyAccessibilityService)
                val text = snap.text?.trim().orEmpty()
                if (text.isNotEmpty() &&
                    text != lastPolledClip &&
                    !LastResultStore.shouldIgnoreClipboard(
                        this@JapanglifyAccessibilityService,
                        text,
                        snap.label
                    )
                ) {
                    lastPolledClip = text
                    CopyHookDiagnostics.log(
                        this@JapanglifyAccessibilityService,
                        "poll saw new clip (${text.length} chars)"
                    )
                    scheduleCopyPipeline("poll")
                }
            } finally {
                mainHandler.postDelayed(this, POLL_MS)
            }
        }
    }

    private val hideOverlayRunnable = Runnable { overlay?.hide() }

    private val selectionDebounce = Runnable {
        val cap = pendingSelection
        pendingSelection = null
        if (cap == null || cap.text.isBlank()) return@Runnable
        if (LastResultStore.isSelfWrite(cap.text)) return@Runnable
        lastSelectedText = cap.text
        CopyHookDiagnostics.log(this, "selection remembered (${cap.text.length} chars)")
        // Optional chip — secondary to Copy
        if (ClipboardProcessor.isAssistWanted(this)) {
            mainHandler.removeCallbacks(hideOverlayRunnable)
            overlay?.show(cap.text, cap.bounds)
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        androidx.preference.PreferenceManager
            .getDefaultSharedPreferences(this)
            .edit()
            .putBoolean(PreferencesRepository.KEY_CLIPBOARD_ASSIST, true)
            .apply()
        ClipboardNotifications.ensureChannels(this)
        clipboard = getSystemService(ClipboardManager::class.java)
        clipboard?.addPrimaryClipChangedListener(clipListener)
        overlay = SelectionActionOverlay(this)
        mainHandler.post(pollRunnable)
        CopyHookDiagnostics.log(this, "service connected — Copy hook active")
        // Visible proof the service is alive (not just the system a11y icon)
        ClipboardNotifications.showHookArmed(this)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        if (!ClipboardProcessor.isAssistWanted(this)) {
            overlay?.hide()
            return
        }
        if (LastResultStore.isSuppressing()) return

        when (event.eventType) {
            AccessibilityEvent.TYPE_VIEW_TEXT_SELECTION_CHANGED,
            AccessibilityEvent.TYPE_VIEW_LONG_CLICKED -> {
                val captured = captureSelection(event)
                if (captured != null && captured.text.isNotBlank()) {
                    pendingSelection = captured
                    lastSelectedText = captured.text
                    mainHandler.removeCallbacks(selectionDebounce)
                    mainHandler.postDelayed(selectionDebounce, 80L)
                }
            }
            AccessibilityEvent.TYPE_VIEW_CLICKED -> {
                if (looksLikeCopyAction(event)) {
                    CopyHookDiagnostics.log(this, "Copy click detected")
                    scheduleCopyPipeline("copy_click")
                } else if (!looksLikeOurChip(event)) {
                    mainHandler.postDelayed(hideOverlayRunnable, 250L)
                }
            }
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> overlay?.hide()
            else -> Unit
        }
    }

    private fun scheduleCopyPipeline(reason: String) {
        if (!ClipboardProcessor.isAssistWanted(this)) return
        if (LastResultStore.isSuppressing()) return

        val gen = ++copyPipelineGeneration
        overlay?.hide()
        CopyHookDiagnostics.log(this, "pipeline start ($reason)")

        // 1) Selection memory — works when OS hides clipboard after Copy
        val selected = lastSelectedText?.trim().orEmpty()
        if (selected.isNotEmpty() && !LastResultStore.isSelfWrite(selected)) {
            when (ClipboardProcessor.processText(this, selected)) {
                ClipboardProcessor.ProcessOutcome.SUCCESS -> {
                    CopyHookDiagnostics.log(this, "processed selection OK")
                    lastPolledClip = selected
                    scheduleClipboardRetries(gen, alreadySucceeded = true)
                    return
                }
                else -> Unit
            }
        }

        scheduleClipboardRetries(gen, alreadySucceeded = false)
    }

    private fun scheduleClipboardRetries(gen: Int, alreadySucceeded: Boolean) {
        val delays = longArrayOf(0L, 30L, 80L, 180L, 350L, 600L, 1000L)
        for (delay in delays) {
            mainHandler.postDelayed({
                if (gen != copyPipelineGeneration) return@postDelayed
                if (LastResultStore.isSuppressing()) return@postDelayed
                if (!ClipboardProcessor.isAssistWanted(this)) return@postDelayed

                when (ClipboardProcessor.processClipboardIfNew(this)) {
                    ClipboardProcessor.ProcessOutcome.SUCCESS -> {
                        CopyHookDiagnostics.log(this, "processed clipboard OK @${delay}ms")
                        lastPolledClip = ClipboardProcessor.readClipboard(this)?.trim()
                    }
                    ClipboardProcessor.ProcessOutcome.EMPTY_OR_UNREADABLE -> {
                        if (delay == delays.last() && !alreadySucceeded) {
                            val sel = lastSelectedText?.trim().orEmpty()
                            if (sel.isNotEmpty()) {
                                val o = ClipboardProcessor.processText(this, sel)
                                CopyHookDiagnostics.log(this, "fallback selection → $o")
                            } else {
                                CopyHookDiagnostics.log(this, "no clip/selection — tap-to-process")
                                ClipboardNotifications.showTapToProcess(this)
                            }
                        }
                    }
                    ClipboardProcessor.ProcessOutcome.DUPLICATE,
                    ClipboardProcessor.ProcessOutcome.SELF_WRITE -> Unit
                    else -> {
                        if (delay == delays.last()) {
                            CopyHookDiagnostics.log(this, "clipboard outcome non-success")
                        }
                    }
                }
            }, delay)
        }
    }

    private data class CapturedSelection(val text: String, val bounds: Rect?)

    private fun captureSelection(event: AccessibilityEvent): CapturedSelection? {
        val fromEventList = event.text
            ?.mapNotNull { it?.toString() }
            ?.joinToString("")
            ?.trim()
            .orEmpty()
        val source = event.source
        try {
            val bounds = Rect()
            source?.getBoundsInScreen(bounds)
            val nodeText = source?.text?.toString()
            val start = source?.textSelectionStart ?: -1
            val end = source?.textSelectionEnd ?: -1
            val selected = when {
                nodeText != null && start >= 0 && end > start && end <= nodeText.length ->
                    nodeText.substring(start, end)
                fromEventList.isNotEmpty() && start >= 0 && end > start &&
                    end <= fromEventList.length ->
                    fromEventList.substring(start, end)
                fromEventList.isNotEmpty() -> fromEventList
                else -> null
            }?.trim().orEmpty()
            if (selected.isBlank()) return null
            return CapturedSelection(selected, if (bounds.isEmpty) null else Rect(bounds))
        } finally {
            if (source != null) recycleSafely(source)
        }
    }

    override fun onInterrupt() {
        overlay?.hide()
    }

    override fun onDestroy() {
        mainHandler.removeCallbacksAndMessages(null)
        clipboard?.removePrimaryClipChangedListener(clipListener)
        clipboard = null
        overlay?.destroy()
        overlay = null
        CopyHookDiagnostics.log(this, "service destroyed")
        if (instance === this) instance = null
        super.onDestroy()
    }

    private fun looksLikeOurChip(event: AccessibilityEvent): Boolean {
        val t = eventLabel(event)
        return t.contains("japanglify")
    }

    private fun looksLikeCopyAction(event: AccessibilityEvent): Boolean {
        val text = eventLabel(event)
        if (text.contains("japanglify")) return false
        val copyHints = listOf(
            "copy", "コピー", "複製", "copy link", "copy text",
            "リンクをコピー", "テキストをコピー", "内容をコピー", "copy all"
        )
        if (copyHints.any { text.contains(it) }) return true
        val source = event.source ?: return false
        return try {
            val label = buildString {
                source.text?.let { append(it) }
                source.contentDescription?.let { append(it) }
                source.viewIdResourceName?.let { append(it) }
            }.lowercase()
            !label.contains("japanglify") && copyHints.any { label.contains(it) }
        } finally {
            recycleSafely(source)
        }
    }

    private fun eventLabel(event: AccessibilityEvent): String = buildString {
        event.text?.forEach { append(it) }
        event.contentDescription?.let { append(it) }
    }.lowercase()

    private fun recycleSafely(node: AccessibilityNodeInfo) {
        try {
            @Suppress("DEPRECATION")
            node.recycle()
        } catch (_: Exception) {
        }
    }

    companion object {
        private const val POLL_MS = 400L

        @Volatile
        var instance: JapanglifyAccessibilityService? = null
            private set

        fun isRunning(): Boolean = instance != null
    }
}
