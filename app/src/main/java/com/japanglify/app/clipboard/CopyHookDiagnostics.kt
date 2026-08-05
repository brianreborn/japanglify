package com.japanglify.app.clipboard

import android.content.Context
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Lightweight ring of recent copy-hook events for the settings screen. */
object CopyHookDiagnostics {
    private const val PREFS = "japanglify_copy_hook_diag"
    private const val KEY_LOG = "log"
    private const val MAX_LINES = 12

    @Volatile
    private var memoryLog: String = ""

    fun log(context: Context, message: String) {
        val ts = SimpleDateFormat("HH:mm:ss", Locale.US).format(Date())
        val line = "$ts  $message"
        synchronized(this) {
            val prev = memoryLog.lines().filter { it.isNotBlank() }
            val next = (listOf(line) + prev).take(MAX_LINES).joinToString("\n")
            memoryLog = next
            context.applicationContext
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_LOG, next)
                .apply()
        }
    }

    fun snapshot(context: Context): String {
        synchronized(this) {
            if (memoryLog.isNotBlank()) return memoryLog
            return context.applicationContext
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getString(KEY_LOG, "")
                .orEmpty()
                .ifBlank { "(no copy-hook events yet — enable Accessibility, then Copy text)" }
        }
    }
}
