package com.japanglify.app

import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.os.bundleOf
import com.japanglify.app.ui.SettingsFragment

/**
 * Launcher / options screen. Context-menu actions never open this for options;
 * PROCESS_TEXT is handled by [ProcessTextActivity]. Share (ACTION_SEND) and
 * the in-app convert / clipboard buttons are fallbacks when a host never
 * exposes PROCESS_TEXT (no overflow menu at all).
 *
 * This activity is just a shell around [SettingsFragment] — the status card,
 * try-it card, and every preference all live in one Preference RecyclerView
 * (see [com.japanglify.app.ui.StatusCardPreference] /
 * [com.japanglify.app.ui.TryItCardPreference]) so the whole screen scrolls
 * as a single list instead of nesting a second scroll container.
 */
class SettingsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        setSupportActionBar(findViewById(R.id.toolbar))
        supportActionBar?.setTitle(R.string.settings_title)

        ensureProcessTextEnabled()

        if (savedInstanceState == null) {
            val sharedText = if (Intent.ACTION_SEND == intent?.action &&
                intent.type?.startsWith("text/") == true
            ) {
                intent.getStringExtra(Intent.EXTRA_TEXT)?.takeIf { it.isNotBlank() }
            } else {
                null
            }
            val fragment = SettingsFragment().apply {
                arguments = sharedText?.let { bundleOf(SettingsFragment.ARG_SHARED_TEXT to it) }
            }
            supportFragmentManager
                .beginTransaction()
                .replace(R.id.settings_container, fragment)
                .commit()
        }
    }

    private fun ensureProcessTextEnabled() {
        val cn = ComponentName(this, ProcessTextActivity::class.java)
        packageManager.setComponentEnabledSetting(
            cn,
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
            PackageManager.DONT_KILL_APP
        )
    }
}
