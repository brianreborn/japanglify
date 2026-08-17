package com.japanglify.app

import android.content.ComponentName
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.os.bundleOf
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import com.japanglify.app.ui.SettingsFragment

/**
 * Launcher / options screen. Context-menu actions never open this for options;
 * PROCESS_TEXT is handled by [ProcessTextActivity], Share (ACTION_SEND) by
 * [ShareTargetActivity]. The in-app convert / clipboard buttons on the
 * Try-It card are the fallback when a host never exposes either.
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

        // targetSdk 35 enforces edge-to-edge, so content (including the
        // toolbar's title) draws behind the status/navigation bars unless we
        // pad for them ourselves — without this the title overlaps the
        // status bar icons and the last list row hides under the nav bar.
        val root = findViewById<View>(R.id.settings_root)
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.updatePadding(top = bars.top, bottom = bars.bottom)
            insets
        }

        // Edge-to-edge enforcement (Android 15 / API 35+ devices specifically
        // -- checked against the device's actual OS version, not just this
        // app's targetSdk) makes the OS ignore this theme's
        // android:statusBarColor entirely, so our own light window
        // background shows through the status bar instead of the dark red
        // that color was written for. The theme's windowLightStatusBar=false
        // (light icons, meant to contrast against that dark red) is then
        // wrong -- light-on-light is invisible except the battery indicator,
        // which always draws its own colored pill regardless of icon tint
        // (confirmed live: exactly the symptom reported). Below API 35,
        // statusBarColor is still honored normally and the theme's default
        // is already correct, so this only overrides it where it would
        // otherwise be wrong.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
            WindowCompat.getInsetsController(window, root).isAppearanceLightStatusBars = true
        }

        ensureProcessTextEnabled()

        if (savedInstanceState == null) {
            // Set only by ShareTargetActivity's URL path (see its doc
            // comment) -- extracted page text lands here for the user to
            // trim/edit before converting, rather than being converted
            // sight-unseen the way a plain-text share is.
            val prefillText = intent?.getStringExtra(EXTRA_PREFILL_TEXT)
            val fragment = SettingsFragment().apply {
                arguments = prefillText?.let { bundleOf(SettingsFragment.ARG_PREFILL_TEXT to it) }
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

    companion object {
        const val EXTRA_PREFILL_TEXT = "prefill_text"
    }
}
