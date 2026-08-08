package com.japanglify.app.debug

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.widget.Toast

/**
 * Debug-build-only seam for live UAT: writes [EXTRA_TEXT] to the system
 * clipboard via [ClipboardManager] directly, then finishes. Exists because
 * `adb shell input text` cannot type non-Latin-1 text (throws inside
 * `InputShellCommand.sendText` for Japanese) -- this sidesteps typing
 * entirely by handing text straight to the clipboard, the same way a real
 * user's IME-based copy would, so it can be pasted into any app (Discord,
 * etc.) via a normal long-press "Paste."
 *
 * Never present in a release build: lives under `app/src/debug/`.
 */
class SetClipboardTestActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val text = intent?.getStringExtra(EXTRA_TEXT).orEmpty()
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("japanglify_uat", text))
        Toast.makeText(this, "Clipboard set (${text.length} chars)", Toast.LENGTH_SHORT).show()
        finish()
    }

    companion object {
        const val EXTRA_TEXT = "text"
    }
}
