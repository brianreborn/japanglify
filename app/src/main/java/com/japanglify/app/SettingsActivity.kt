package com.japanglify.app

import android.content.ClipboardManager
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.japanglify.app.ui.SettingsFragment

/**
 * Launcher / options screen. Context-menu actions never open this for options;
 * PROCESS_TEXT is handled by [ProcessTextActivity]. Share (ACTION_SEND) and
 * the in-app convert / clipboard buttons are fallbacks when a host never
 * exposes PROCESS_TEXT (no overflow menu at all).
 */
class SettingsActivity : AppCompatActivity() {

    private val liveHandler = Handler(Looper.getMainLooper())
    private var liveRunnable: Runnable? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        setSupportActionBar(findViewById(R.id.toolbar))
        supportActionBar?.setTitle(R.string.settings_title)

        ensureProcessTextEnabled()

        if (savedInstanceState == null) {
            supportFragmentManager
                .beginTransaction()
                .replace(R.id.settings_container, SettingsFragment())
                .commit()
        }

        val input = findViewById<EditText>(R.id.try_it_text)
        findViewById<MaterialButton>(R.id.btn_japanglify_here).setOnClickListener {
            japanglifyTryItField()
        }
        findViewById<MaterialButton>(R.id.btn_japanglify_clipboard).setOnClickListener {
            japanglifyClipboard()
        }

        // Live preview — debounced so typing stays smooth
        input.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                scheduleLivePreview()
            }
        })
        scheduleLivePreview()

        if (Intent.ACTION_SEND == intent?.action && intent.type?.startsWith("text/") == true) {
            val shared = intent.getStringExtra(Intent.EXTRA_TEXT)
            if (!shared.isNullOrBlank()) {
                input.setText(shared)
                Toast.makeText(this, R.string.shared_text_loaded, Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        refreshRegistrationStatus()
        scheduleLivePreview()
    }

    override fun onDestroy() {
        liveRunnable?.let { liveHandler.removeCallbacks(it) }
        super.onDestroy()
    }

    private fun scheduleLivePreview() {
        liveRunnable?.let { liveHandler.removeCallbacks(it) }
        val r = Runnable { updateLiveOutput() }
        liveRunnable = r
        liveHandler.postDelayed(r, 180L)
    }

    private fun updateLiveOutput() {
        val input = findViewById<EditText>(R.id.try_it_text) ?: return
        val output = findViewById<TextView>(R.id.try_it_output) ?: return
        val source = input.text?.toString().orEmpty()
        if (source.isBlank()) {
            output.text = ""
            return
        }
        val app = application as JapanglifyApp
        val expanded = runCatching {
            app.engine.expand(source, app.preferences.load())
        }.getOrElse { err ->
            output.text = getString(R.string.error_processing, err.message ?: "unknown")
            return
        }
        output.text = expanded
    }

    private fun ensureProcessTextEnabled() {
        val cn = ComponentName(this, ProcessTextActivity::class.java)
        packageManager.setComponentEnabledSetting(
            cn,
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
            PackageManager.DONT_KILL_APP
        )
    }

    private fun refreshRegistrationStatus() {
        val statusView = findViewById<TextView>(R.id.status_registration) ?: return
        val handlersView = findViewById<TextView>(R.id.status_handlers) ?: return
        val hookLog = findViewById<TextView>(R.id.status_copy_hook_log)

        val intent = Intent(Intent.ACTION_PROCESS_TEXT).setType("text/plain")
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PackageManager.MATCH_ALL or PackageManager.MATCH_DEFAULT_ONLY
        } else {
            0
        }
        @Suppress("DEPRECATION")
        val resolved = packageManager.queryIntentActivities(intent, flags)

        val selfEntries = resolved.filter {
            it.activityInfo.packageName == packageName
        }
        val labels = resolved.map { info ->
            val label = info.loadLabel(packageManager)
            val name = info.activityInfo.name.substringAfterLast('.')
            "• $label ($name)"
        }

        val cn = ComponentName(this, ProcessTextActivity::class.java)
        val enabled = try {
            val state = packageManager.getComponentEnabledSetting(cn)
            state == PackageManager.COMPONENT_ENABLED_STATE_ENABLED ||
                state == PackageManager.COMPONENT_ENABLED_STATE_DEFAULT
        } catch (_: Exception) {
            true
        }

        val a11y = com.japanglify.app.clipboard.JapanglifyAccessibilityService.isRunning()
        val registered = selfEntries.isNotEmpty()
        statusView.text = buildString {
            append(if (registered) getString(R.string.status_registered_ok) else getString(R.string.status_registered_missing))
            append('\n')
            append(getString(R.string.status_component_enabled, if (enabled) "yes" else "NO"))
            append('\n')
            append(getString(R.string.status_handlers_count, resolved.size, selfEntries.size))
            append('\n')
            append(
                if (a11y) "Copy hook (Accessibility): RUNNING"
                else "Copy hook (Accessibility): OFF — enable Japanglify Copy assist in system settings"
            )
            append("\n\n")
            append(getString(R.string.status_why_only_here))
        }
        statusView.setTextColor(
            getColor(if (registered) R.color.jp_ink else R.color.jp_red)
        )

        handlersView.text = buildString {
            append(getString(R.string.status_menu_not_a_bug))
            append("\n\n")
            if (labels.isEmpty()) {
                append(getString(R.string.status_handlers_empty))
            } else {
                append(getString(R.string.status_handlers_list, labels.joinToString("\n")))
            }
        }

        hookLog?.text = com.japanglify.app.clipboard.CopyHookDiagnostics.snapshot(this)
    }

    private fun japanglifyTryItField() {
        val field = findViewById<EditText>(R.id.try_it_text)
        val start = field.selectionStart.coerceAtLeast(0)
        val end = field.selectionEnd.coerceAtLeast(0)
        val hasSelection = start != end
        val source = if (hasSelection) {
            field.text.substring(minOf(start, end), maxOf(start, end))
        } else {
            field.text?.toString().orEmpty()
        }
        if (source.isBlank()) {
            Toast.makeText(this, R.string.try_it_empty, Toast.LENGTH_SHORT).show()
            return
        }

        val expanded = expandOrToast(source) ?: return

        if (hasSelection) {
            field.text.replace(minOf(start, end), maxOf(start, end), expanded)
        } else {
            field.setText(expanded)
            field.setSelection(expanded.length)
        }
        updateLiveOutput()
        Toast.makeText(this, R.string.try_it_done, Toast.LENGTH_SHORT).show()
    }

    private fun japanglifyClipboard() {
        val clipboard = getSystemService(ClipboardManager::class.java)
        val text = clipboard.primaryClip
            ?.takeIf { it.itemCount > 0 }
            ?.getItemAt(0)
            ?.coerceToText(this)
            ?.toString()
            ?.takeIf { it.isNotBlank() }

        if (text == null) {
            Toast.makeText(this, R.string.clipboard_empty, Toast.LENGTH_LONG).show()
            return
        }

        val expanded = expandOrToast(text) ?: return
        findViewById<EditText>(R.id.try_it_text).setText(expanded)
        com.japanglify.app.clipboard.LastResultStore.writeToClipboard(this, expanded)
        updateLiveOutput()
        Toast.makeText(this, R.string.clipboard_done, Toast.LENGTH_SHORT).show()
    }

    private fun expandOrToast(source: String): String? {
        val app = application as JapanglifyApp
        return runCatching {
            app.engine.expand(source, app.preferences.load())
        }.getOrElse { err ->
            Toast.makeText(
                this,
                getString(R.string.error_processing, err.message ?: "unknown"),
                Toast.LENGTH_SHORT
            ).show()
            null
        }
    }
}
