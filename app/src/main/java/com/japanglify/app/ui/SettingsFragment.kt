package com.japanglify.app.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.SwitchPreferenceCompat
import com.japanglify.app.R
import com.japanglify.app.clipboard.ClipboardAssistService
import com.japanglify.app.clipboard.ClipboardNotifications
import com.japanglify.app.clipboard.JapanglifyAccessibilityService
import com.japanglify.app.data.PreferencesRepository

class SettingsFragment : PreferenceFragmentCompat() {

    private val requestNotifications = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        val pref = findPreference<SwitchPreferenceCompat>(
            PreferencesRepository.KEY_CLIPBOARD_ASSIST
        )
        if (granted) {
            pref?.isChecked = true
            onAssistEnabled()
        } else {
            pref?.isChecked = false
            Toast.makeText(
                requireContext(),
                R.string.clipboard_assist_need_notifications,
                Toast.LENGTH_LONG
            ).show()
        }
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.preferences, rootKey)
        bindList(
            PreferencesRepository.KEY_ROMANIZATION,
            com.japanglify.app.domain.RomanizationSystem.entries.map { it.id to it.displayName }
        )
        bindList(
            PreferencesRepository.KEY_ROMAJI_POSITION,
            com.japanglify.app.domain.RomajiPosition.entries.map { it.id to it.displayName }
        )
        bindList(
            PreferencesRepository.KEY_OUTPUT_FORMAT,
            com.japanglify.app.domain.OutputFormat.entries.map { it.id to it.displayName }
        )
        bindList(
            PreferencesRepository.KEY_WRITING_ORIENTATION,
            com.japanglify.app.domain.WritingOrientation.entries.map { it.id to it.displayName }
        )
        bindList(
            PreferencesRepository.KEY_MAX_LINE_WIDTH,
            listOf(
                "10" to "10 fullwidth (narrow)",
                "12" to "12 fullwidth",
                "14" to "14 fullwidth (default)",
                "16" to "16 fullwidth",
                "20" to "20 fullwidth",
                "24" to "24 fullwidth (wide)",
                "0" to "No wrap (unlimited)"
            )
        )

        findPreference<Preference>(PreferencesRepository.KEY_OPEN_ACCESSIBILITY)
            ?.setOnPreferenceClickListener {
                openAccessibilitySettings()
                true
            }

        findPreference<SwitchPreferenceCompat>(PreferencesRepository.KEY_CLIPBOARD_ASSIST)
            ?.setOnPreferenceChangeListener { _, newValue ->
                val enabled = newValue as Boolean
                if (enabled) {
                    if (!hasNotificationPermission()) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            requestNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
                            return@setOnPreferenceChangeListener false
                        }
                    }
                    onAssistEnabled()
                    true
                } else {
                    onAssistDisabled()
                    true
                }
            }

        findPreference<SwitchPreferenceCompat>(PreferencesRepository.KEY_CLIPBOARD_FGS_FALLBACK)
            ?.setOnPreferenceChangeListener { _, newValue ->
                val enabled = newValue as Boolean
                val assistOn = findPreference<SwitchPreferenceCompat>(
                    PreferencesRepository.KEY_CLIPBOARD_ASSIST
                )?.isChecked == true
                if (enabled && assistOn) {
                    ClipboardAssistService.start(requireContext())
                } else {
                    ClipboardAssistService.stop(requireContext())
                }
                true
            }
    }

    override fun onResume() {
        super.onResume()
        refreshA11yStatus()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val paper = ContextCompat.getColor(requireContext(), R.color.jp_paper)
        view.setBackgroundColor(paper)
        listView.setBackgroundColor(paper)
        // Parent NestedScrollView owns scrolling — expand the list to full content height.
        listView.isNestedScrollingEnabled = false
        listView.overScrollMode = View.OVER_SCROLL_NEVER
        listView.post { expandListToContentHeight() }
        listView.viewTreeObserver.addOnGlobalLayoutListener {
            expandListToContentHeight()
        }
        refreshA11yStatus()
    }

    /**
     * Expand the Preference [RecyclerView] to the height of all rows so the
     * parent [androidx.core.widget.NestedScrollView] scrolls status + options as one.
     */
    private fun expandListToContentHeight() {
        val rv = listView ?: return
        if (rv.width == 0) return
        val adapter = rv.adapter ?: return
        var total = rv.paddingTop + rv.paddingBottom
        val widthSpec = View.MeasureSpec.makeMeasureSpec(rv.width, View.MeasureSpec.EXACTLY)
        val heightSpec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        for (i in 0 until adapter.itemCount) {
            val type = adapter.getItemViewType(i)
            val holder = adapter.createViewHolder(rv, type)
            adapter.onBindViewHolder(holder, i)
            holder.itemView.measure(widthSpec, heightSpec)
            total += holder.itemView.measuredHeight
        }
        // Dividers / decorations approximate
        total += (adapter.itemCount - 1).coerceAtLeast(0) * 2
        val lp = rv.layoutParams
        if (lp.height != total) {
            lp.height = total
            rv.layoutParams = lp
        }
        view?.layoutParams?.let { rootLp ->
            if (rootLp.height != total) {
                rootLp.height = total
                view?.layoutParams = rootLp
            }
        }
    }

    private fun refreshA11yStatus() {
        val status = findPreference<Preference>(PreferencesRepository.KEY_A11Y_STATUS) ?: return
        val running = JapanglifyAccessibilityService.isRunning() || isA11yEnabledInSettings()
        status.summary = getString(
            if (running) R.string.a11y_status_on else R.string.a11y_status_off
        )
    }

    private fun isA11yEnabledInSettings(): Boolean {
        val expected = requireContext().packageName + "/" +
            JapanglifyAccessibilityService::class.java.canonicalName
        val enabled = Settings.Secure.getString(
            requireContext().contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        return enabled.split(':').any { it.equals(expected, ignoreCase = true) } ||
            enabled.contains(JapanglifyAccessibilityService::class.java.name)
    }

    private fun hasNotificationPermission(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return ContextCompat.checkSelfPermission(
            requireContext(),
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun onAssistEnabled() {
        ClipboardNotifications.ensureChannels(requireContext())
        Toast.makeText(
            requireContext(),
            R.string.clipboard_assist_started,
            Toast.LENGTH_LONG
        ).show()
        if (!JapanglifyAccessibilityService.isRunning() && !isA11yEnabledInSettings()) {
            Toast.makeText(
                requireContext(),
                R.string.clipboard_assist_need_a11y,
                Toast.LENGTH_LONG
            ).show()
            openAccessibilitySettings()
        }
        val fgs = findPreference<SwitchPreferenceCompat>(
            PreferencesRepository.KEY_CLIPBOARD_FGS_FALLBACK
        )
        if (fgs?.isChecked == true) {
            ClipboardAssistService.start(requireContext())
        }
        refreshA11yStatus()
    }

    private fun onAssistDisabled() {
        ClipboardAssistService.stop(requireContext())
        Toast.makeText(
            requireContext(),
            R.string.clipboard_assist_stopped,
            Toast.LENGTH_SHORT
        ).show()
        // Accessibility service may stay enabled in system settings but will
        // ignore copies while the preference is off.
    }

    private fun openAccessibilitySettings() {
        startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
    }

    private fun bindList(key: String, entries: List<Pair<String, String>>) {
        val pref = findPreference<ListPreference>(key) ?: return
        pref.entryValues = entries.map { it.first }.toTypedArray()
        pref.entries = entries.map { it.second }.toTypedArray()
        pref.setSummaryProvider(ListPreference.SimpleSummaryProvider.getInstance())
    }
}
