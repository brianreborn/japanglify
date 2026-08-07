package com.japanglify.app.ui

import android.Manifest
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.style.RelativeSizeSpan
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.preference.ListPreference
import androidx.preference.MultiSelectListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.SwitchPreferenceCompat
import com.japanglify.app.JapanglifyApp
import com.japanglify.app.ProcessTextActivity
import com.japanglify.app.R
import com.japanglify.app.clipboard.ClipboardAssistService
import com.japanglify.app.clipboard.ClipboardImageRenderer
import com.japanglify.app.clipboard.ClipboardNotifications
import com.japanglify.app.clipboard.ClipboardProcessor
import com.japanglify.app.clipboard.CopyHookDiagnostics
import com.japanglify.app.clipboard.JapanglifyAccessibilityService
import com.japanglify.app.clipboard.LastResultStore
import com.japanglify.app.data.PreferencesRepository
import com.japanglify.app.dictionary.DictionaryDatabase
import com.japanglify.app.dictionary.DictionaryDownloadService
import com.japanglify.app.dictionary.DictionaryDownloadStatus
import com.japanglify.app.dictionary.EmojiDatabase
import com.japanglify.app.dictionary.WordNetDatabase
import com.japanglify.app.domain.EmojiPrecisionTier
import com.japanglify.app.domain.JapanglifySettings
import com.japanglify.app.domain.OutputFormat
import com.japanglify.app.domain.TripleScriptRenderer
import com.japanglify.app.domain.dictionary.DictionarySource
import com.japanglify.app.domain.dictionary.DictionarySourceFormat
import com.japanglify.app.domain.dictionary.DictionarySources
import com.japanglify.app.domain.dictionary.PartOfSpeech
import java.io.File

class SettingsFragment : PreferenceFragmentCompat() {

    private val liveHandler = Handler(Looper.getMainLooper())
    private var liveRunnable: Runnable? = null

    private val dictHandler = Handler(Looper.getMainLooper())
    private var dictRunnable: Runnable? = null

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
        findPreference<ListPreference>(PreferencesRepository.KEY_OUTPUT_FORMAT)
            ?.setOnPreferenceChangeListener { _, newValue ->
                // ListPreference reports the new value before it's persisted, so
                // pass it straight through rather than re-reading SharedPreferences.
                val fmt = OutputFormat.fromId(newValue as? String)
                findPreference<OutputFormatPreviewPreference>(KEY_OUTPUT_FORMAT_PREVIEW)?.refresh(fmt)
                true
            }
        bindList(
            PreferencesRepository.KEY_WRITING_ORIENTATION,
            com.japanglify.app.domain.WritingOrientation.entries.map { it.id to it.displayName }
        )
        bindList(
            PreferencesRepository.KEY_FURIGANA_PUNCTUATION_STYLE,
            com.japanglify.app.domain.FuriganaPunctuationStyle.entries.map { it.id to it.displayName }
        )
        bindList(
            PreferencesRepository.KEY_DICTIONARY_SOURCE,
            DictionarySources.ALL.map { it.id to it.displayName }
        )
        findPreference<ListPreference>(PreferencesRepository.KEY_DICTIONARY_SOURCE)
            ?.setOnPreferenceChangeListener { _, newValue ->
                // Same "new value arrives before it's persisted" shape as
                // KEY_OUTPUT_FORMAT above -- refresh against it directly.
                val source = DictionarySources.byId(newValue as? String) ?: DictionarySources.JMDICT_ENGLISH
                refreshDictionaryStatus(source)
                true
            }
        findPreference<DictionaryStatusPreference>(KEY_DICTIONARY_STATUS)?.apply {
            onDownloadClicked = {
                DictionaryDownloadService.start(requireContext(), selectedDictionarySource())
                scheduleDictionaryPoll()
            }
            onDeleteClicked = { deleteDictionary(selectedDictionarySource()) }
        }

        findPreference<DictionaryStatusPreference>(KEY_EMOJI_STATUS)?.apply {
            onDownloadClicked = {
                DictionaryDownloadService.start(requireContext(), DictionarySources.CLDR_EMOJI)
                scheduleDictionaryPoll()
            }
            onDeleteClicked = { deleteDictionary(DictionarySources.CLDR_EMOJI) }
        }
        findPreference<DictionaryStatusPreference>(KEY_WORDNET_STATUS)?.apply {
            onDownloadClicked = {
                DictionaryDownloadService.start(requireContext(), DictionarySources.WORDNET_SYNONYMS)
                scheduleDictionaryPoll()
            }
            onDeleteClicked = { deleteDictionary(DictionarySources.WORDNET_SYNONYMS) }
        }
        bindList(
            PreferencesRepository.KEY_EMOJI_PRECISION_TIER,
            EmojiPrecisionTier.entries.map { it.id to it.displayName }
        )
        findPreference<MultiSelectListPreference>(PreferencesRepository.KEY_EMOJI_POS_SCOPE)?.apply {
            entryValues = PartOfSpeech.entries.map { it.name }.toTypedArray()
            entries = PartOfSpeech.entries.map { it.displayName }.toTypedArray()
        }

        findPreference<Preference>(KEY_ABOUT_CONTACT)?.setOnPreferenceClickListener {
            val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:$CONTACT_EMAIL"))
            runCatching { startActivity(intent) }
            true
        }

        findPreference<Preference>(KEY_ABOUT_PROFILE)?.setOnPreferenceClickListener {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(PROFILE_URL))
            runCatching { startActivity(intent) }
            true
        }

        findPreference<TryItCardPreference>(KEY_TRY_IT_CARD)?.apply {
            onTextChanged = { scheduleLivePreview() }
            onConvertSelectionOrAll = { japanglifyTryItField() }
            onConvertClipboard = { japanglifyClipboard() }
            arguments?.getString(ARG_SHARED_TEXT)?.let { shared ->
                setText(shared)
                Toast.makeText(requireContext(), R.string.shared_text_loaded, Toast.LENGTH_SHORT).show()
            }
        }
        // Consume the shared-text arg so it isn't reapplied on config change / reuse.
        arguments?.remove(ARG_SHARED_TEXT)

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
        refreshStatusCard()
        scheduleLivePreview()
        findPreference<OutputFormatPreviewPreference>(KEY_OUTPUT_FORMAT_PREVIEW)?.refresh()
        refreshDictionaryStatus(selectedDictionarySource())
        refreshDictionaryStatus(DictionarySources.CLDR_EMOJI)
        refreshDictionaryStatus(DictionarySources.WORDNET_SYNONYMS)
        // Covers a download already in flight from before this screen was
        // (re)opened -- e.g. rotated, or navigated away and back.
        scheduleDictionaryPoll()
    }

    override fun onDestroyView() {
        liveRunnable?.let { liveHandler.removeCallbacks(it) }
        dictRunnable?.let { dictHandler.removeCallbacks(it) }
        super.onDestroyView()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val paper = ContextCompat.getColor(requireContext(), R.color.jp_paper)
        view.setBackgroundColor(paper)
        listView.setBackgroundColor(paper)
    }

    // ── Try-it card ─────────────────────────────────────────────────

    private fun scheduleLivePreview() {
        liveRunnable?.let { liveHandler.removeCallbacks(it) }
        val r = Runnable { updateLiveOutput() }
        liveRunnable = r
        liveHandler.postDelayed(r, 180L)
    }

    private fun updateLiveOutput() {
        val tryItCard = findPreference<TryItCardPreference>(KEY_TRY_IT_CARD) ?: return
        val source = tryItCard.currentText()
        if (source.isBlank()) {
            tryItCard.setOutput("")
            return
        }
        val app = requireContext().applicationContext as JapanglifyApp
        val output = runCatching {
            buildPreviewOutput(app, source, app.preferences.load())
        }.getOrElse { err ->
            tryItCard.setOutput(getString(R.string.error_processing, err.message ?: "unknown"))
            return
        }
        tryItCard.setOutput(output)
    }

    /**
     * For INTERLINEAR, renders via the structured/role-tagged rows instead of
     * the plain-text renderer so the furigana row can be shown visibly
     * smaller — real furigana is a small reading annotation, not a same-size
     * third line, and losing that distinction was the whole complaint. Only
     * this in-app preview is richly styled; the plain-text output copied
     * elsewhere (clipboard/PROCESS_TEXT) is unchanged.
     */
    private fun buildPreviewOutput(
        app: JapanglifyApp,
        source: String,
        settings: JapanglifySettings
    ): CharSequence {
        if (settings.outputFormat != OutputFormat.INTERLINEAR) {
            return app.engine.expand(source, settings)
        }
        val rows = app.engine.buildInterlinearDisplayRows(source, settings)
        return SpannableStringBuilder().apply {
            rows.forEachIndexed { rowIndex, row ->
                if (rowIndex > 0) append("\n\n")
                row.lines.forEachIndexed { lineIndex, line ->
                    if (lineIndex > 0) append("\n")
                    val start = length
                    append(line.text)
                    if (line.role == TripleScriptRenderer.InterlinearLineRole.FURIGANA) {
                        setSpan(
                            RelativeSizeSpan(ClipboardImageRenderer.FURIGANA_RELATIVE_SIZE),
                            start,
                            length,
                            Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                        )
                    }
                }
            }
        }
    }

    private fun japanglifyTryItField() {
        val tryItCard = findPreference<TryItCardPreference>(KEY_TRY_IT_CARD) ?: return
        val start = tryItCard.selectionStart()
        val end = tryItCard.selectionEnd()
        val hasSelection = start != end
        val source = if (hasSelection) {
            tryItCard.substring(minOf(start, end), maxOf(start, end))
        } else {
            tryItCard.currentText()
        }
        if (source.isBlank()) {
            Toast.makeText(requireContext(), R.string.try_it_empty, Toast.LENGTH_SHORT).show()
            return
        }

        val expanded = expandOrToast(source) ?: return

        if (hasSelection) {
            tryItCard.replaceRange(minOf(start, end), maxOf(start, end), expanded)
            updateLiveOutput()
        } else {
            // Whole-field convert replaces the input with `expanded` itself —
            // the TextWatcher this triggers would otherwise re-run engine.expand
            // on that already-converted text 180ms later (scheduleLivePreview's
            // debounce), producing a garbled double-conversion. We already know
            // the correct output, so cancel that reschedule and set it directly.
            tryItCard.setText(expanded)
            tryItCard.setSelectionEnd(expanded.length)
            liveRunnable?.let { liveHandler.removeCallbacks(it) }
            val app = requireContext().applicationContext as JapanglifyApp
            tryItCard.setOutput(buildPreviewOutput(app, source, app.preferences.load()))
        }
        Toast.makeText(requireContext(), R.string.try_it_done, Toast.LENGTH_SHORT).show()
    }

    private fun japanglifyClipboard() {
        // Shared with ClipboardProcessor's other callers rather than reading
        // ClipboardManager directly here: readClipboardSnapshot() already
        // catches the exception a non-text ClipData item (e.g. a URI/image
        // clip with no read grant) can throw from coerceToText() — this call
        // site used to skip that and could crash instead of just no-op'ing.
        val text = ClipboardProcessor.readClipboardSnapshot(requireContext())
            .text
            ?.takeIf { it.isNotBlank() }

        if (text == null) {
            Toast.makeText(requireContext(), R.string.clipboard_empty, Toast.LENGTH_LONG).show()
            return
        }

        val expanded = expandOrToast(text) ?: return
        val app = requireContext().applicationContext as JapanglifyApp
        findPreference<TryItCardPreference>(KEY_TRY_IT_CARD)?.let { card ->
            card.setText(expanded)
            // Same double-conversion hazard as japanglifyTryItField() above.
            liveRunnable?.let { liveHandler.removeCallbacks(it) }
            card.setOutput(buildPreviewOutput(app, text, app.preferences.load()))
        }
        LastResultStore.writeToClipboard(requireContext(), expanded)
        Toast.makeText(requireContext(), R.string.clipboard_done, Toast.LENGTH_SHORT).show()
    }

    private fun expandOrToast(source: String): String? {
        val app = requireContext().applicationContext as JapanglifyApp
        return runCatching {
            app.engine.expand(source, app.preferences.load())
        }.getOrElse { err ->
            Toast.makeText(
                requireContext(),
                getString(R.string.error_processing, err.message ?: "unknown"),
                Toast.LENGTH_SHORT
            ).show()
            null
        }
    }

    // ── Dictionary ──────────────────────────────────────────────────

    private fun selectedDictionarySource(): DictionarySource {
        val app = requireContext().applicationContext as JapanglifyApp
        return DictionarySources.byId(app.preferences.selectedDictionarySourceId())
            ?: DictionarySources.JMDICT_ENGLISH
    }

    private fun refreshDictionaryStatus(source: DictionarySource) {
        val statusKey = when (source.format) {
            DictionarySourceFormat.CLDR_EMOJI_XML -> KEY_EMOJI_STATUS
            DictionarySourceFormat.WORDNET_PROLOG -> KEY_WORDNET_STATUS
            DictionarySourceFormat.JMDICT_JSON -> KEY_DICTIONARY_STATUS
        }
        val statusPref = findPreference<DictionaryStatusPreference>(statusKey) ?: return
        val app = requireContext().applicationContext as JapanglifyApp
        val status = app.preferences.dictionaryStatus(source.id)
        // wordsImported isn't persisted anywhere (only known transiently
        // during the download's own progress callback) -- when READY,
        // query the real row count directly rather than show a stale or
        // always-zero number once the user leaves and reopens Settings.
        val wordCount = if (status == DictionaryDownloadStatus.READY) countEntries(source) else 0
        statusPref.render(
            DictionaryStatusPreference.State(
                sourceName = source.displayName,
                status = status,
                percent = null,
                wordsImported = wordCount,
                errorMessage = app.preferences.dictionaryErrorMessage(source.id)
            )
        )
    }

    private fun countEntries(source: DictionarySource): Int = runCatching {
        when (source.format) {
            DictionarySourceFormat.JMDICT_JSON -> {
                val helper = DictionaryDatabase(requireContext(), DictionaryDatabase.fileNameFor(source.id))
                helper.readableDatabase.rawQuery(
                    "SELECT COUNT(*) FROM ${DictionaryDatabase.TABLE}",
                    null
                ).use { cursor -> cursor.moveToFirst(); cursor.getInt(0) }
            }
            DictionarySourceFormat.CLDR_EMOJI_XML -> {
                val helper = EmojiDatabase(requireContext(), EmojiDatabase.fileNameFor(source.id))
                helper.readableDatabase.rawQuery(
                    "SELECT COUNT(*) FROM ${EmojiDatabase.TABLE}",
                    null
                ).use { cursor -> cursor.moveToFirst(); cursor.getInt(0) }
            }
            DictionarySourceFormat.WORDNET_PROLOG -> {
                val helper = WordNetDatabase(requireContext(), WordNetDatabase.fileNameFor(source.id))
                helper.readableDatabase.rawQuery(
                    "SELECT COUNT(*) FROM ${WordNetDatabase.TABLE}",
                    null
                ).use { cursor -> cursor.moveToFirst(); cursor.getInt(0) }
            }
        }
    }.getOrDefault(0)

    /**
     * Same debounce/cancel-and-repost shape as [scheduleLivePreview], but
     * self-rescheduling rather than a one-shot: the actual downloads run in
     * [DictionaryDownloadService] (a separate component, protected against
     * this fragment/activity going away), so this fragment has no direct
     * callback into them and instead polls the same
     * [com.japanglify.app.data.PreferencesRepository] status the service
     * persists at each stage transition.
     *
     * One shared poll covers *both* possible sources (the selected word
     * dictionary and the fixed emoji source) per tick rather than two
     * independent Handler/Runnable pairs -- both would run on the same main
     * thread either way (no real thread-switching cost from having two),
     * but one scheduled callback checking two sources is simpler than two
     * near-identical ones, and it self-stops once *neither* is still
     * DOWNLOADING/PARSING instead of needing two independent stop
     * conditions.
     */
    private fun scheduleDictionaryPoll() {
        dictRunnable?.let { dictHandler.removeCallbacks(it) }
        // A source can reach READY on one tick while the other source is
        // still in progress on a later tick -- track which ones already
        // triggered rebuildEngine() so a still-running sibling source
        // doesn't cause it to be called again every tick.
        val alreadyRebuilt = mutableSetOf<String>()
        val r = object : Runnable {
            override fun run() {
                val app = requireContext().applicationContext as JapanglifyApp
                var anyInProgress = false
                for (source in listOf(
                    selectedDictionarySource(),
                    DictionarySources.CLDR_EMOJI,
                    DictionarySources.WORDNET_SYNONYMS
                )) {
                    refreshDictionaryStatus(source)
                    when (app.preferences.dictionaryStatus(source.id)) {
                        DictionaryDownloadStatus.DOWNLOADING, DictionaryDownloadStatus.PARSING ->
                            anyInProgress = true
                        DictionaryDownloadStatus.READY ->
                            // A download can finish while the app is already
                            // running (the service isn't tied to this
                            // screen) -- without this, it would silently sit
                            // unused until the app was force-killed and
                            // reopened.
                            if (alreadyRebuilt.add(source.id)) app.rebuildEngine()
                        else -> Unit
                    }
                }
                if (anyInProgress) dictHandler.postDelayed(this, DICTIONARY_POLL_INTERVAL_MS)
            }
        }
        dictRunnable = r
        dictHandler.post(r)
    }

    private fun deleteDictionary(source: DictionarySource) {
        val context = requireContext()
        val app = context.applicationContext as JapanglifyApp
        val fileName = when (source.format) {
            DictionarySourceFormat.JMDICT_JSON -> DictionaryDatabase.fileNameFor(source.id)
            DictionarySourceFormat.CLDR_EMOJI_XML -> EmojiDatabase.fileNameFor(source.id)
            DictionarySourceFormat.WORDNET_PROLOG -> WordNetDatabase.fileNameFor(source.id)
        }
        val dbFile = context.getDatabasePath(fileName)
        dbFile.delete()
        // SQLiteOpenHelper only creates -journal/-wal sidecars for a
        // connection that opened *this* file for writing; the read-only
        // Sqlite*Provider connections this app uses don't, but clean up
        // defensively rather than assume that always holds.
        File("${dbFile.path}-journal").delete()
        File("${dbFile.path}-wal").delete()
        File("${dbFile.path}-shm").delete()
        app.preferences.setDictionaryStatus(source.id, DictionaryDownloadStatus.NOT_DOWNLOADED)
        refreshDictionaryStatus(source)
        app.rebuildEngine()
        Toast.makeText(context, R.string.dictionary_action_delete, Toast.LENGTH_SHORT).show()
    }

    // ── Status card ─────────────────────────────────────────────────

    private fun refreshStatusCard() {
        val statusCard = findPreference<StatusCardPreference>(KEY_STATUS_CARD) ?: return
        val context = requireContext()
        val packageManager = context.packageManager

        val intent = Intent(Intent.ACTION_PROCESS_TEXT).setType("text/plain")
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PackageManager.MATCH_ALL or PackageManager.MATCH_DEFAULT_ONLY
        } else {
            0
        }
        @Suppress("DEPRECATION")
        val resolved = packageManager.queryIntentActivities(intent, flags)

        val selfEntries = resolved.filter { it.activityInfo.packageName == context.packageName }
        val labels = resolved.map { info ->
            val label = info.loadLabel(packageManager)
            val name = info.activityInfo.name.substringAfterLast('.')
            "• $label ($name)"
        }

        val cn = ComponentName(context, ProcessTextActivity::class.java)
        val enabled = try {
            val state = packageManager.getComponentEnabledSetting(cn)
            state == PackageManager.COMPONENT_ENABLED_STATE_ENABLED ||
                state == PackageManager.COMPONENT_ENABLED_STATE_DEFAULT
        } catch (_: Exception) {
            true
        }

        val a11y = JapanglifyAccessibilityService.isRunning()
        val registered = selfEntries.isNotEmpty()
        val registrationText = buildString {
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

        val handlersText = buildString {
            append(getString(R.string.status_menu_not_a_bug))
            append("\n\n")
            if (labels.isEmpty()) {
                append(getString(R.string.status_handlers_empty))
            } else {
                append(getString(R.string.status_handlers_list, labels.joinToString("\n")))
            }
        }

        statusCard.updateStatus(
            registrationText = registrationText,
            registrationColor = ContextCompat.getColor(
                context,
                if (registered) R.color.jp_ink else R.color.jp_red
            ),
            handlersText = handlersText,
            hookLogText = CopyHookDiagnostics.snapshot(context)
        )
    }

    // ── Accessibility / clipboard-assist prefs ─────────────────────

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

    companion object {
        const val ARG_SHARED_TEXT = "shared_text"
        private const val KEY_STATUS_CARD = "status_card"
        private const val KEY_TRY_IT_CARD = "try_it_card"
        private const val KEY_OUTPUT_FORMAT_PREVIEW = "output_format_preview"
        private const val KEY_ABOUT_CONTACT = "about_contact"
        private const val CONTACT_EMAIL = "brianfundakowskifeldman@gmail.com"
        private const val KEY_ABOUT_PROFILE = "about_profile"
        private const val PROFILE_URL = "https://x.com/born_brian85001"
        private const val KEY_DICTIONARY_STATUS = "dictionary_status"
        private const val KEY_EMOJI_STATUS = "emoji_status"
        private const val KEY_WORDNET_STATUS = "wordnet_status"
        private const val DICTIONARY_POLL_INTERVAL_MS = 1000L
    }
}
