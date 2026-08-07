package com.japanglify.app.ui

import android.content.Context
import android.graphics.Typeface
import android.util.AttributeSet
import android.widget.TextView
import androidx.preference.Preference
import androidx.preference.PreferenceViewHolder
import com.japanglify.app.JapanglifyApp
import com.japanglify.app.R
import com.japanglify.app.domain.OutputFormat

/**
 * Renders every [OutputFormat] option's output for the same short sample,
 * side by side, so people can see what they're choosing between instead of
 * picking a format by name alone — the output-format `ListPreference` above
 * this in Settings has no other way to preview a format before selecting it.
 *
 * Read-only: this never writes the `output_format` preference itself, only
 * displays it. [refresh] is driven externally by [SettingsFragment], both on
 * resume and immediately after the `output_format` `ListPreference` changes.
 */
class OutputFormatPreviewPreference(context: Context, attrs: AttributeSet?) : Preference(context, attrs) {

    init {
        layoutResource = R.layout.preference_output_format_preview
        isSelectable = false
    }

    private var boundHolder: PreferenceViewHolder? = null

    override fun onBindViewHolder(holder: PreferenceViewHolder) {
        super.onBindViewHolder(holder)
        boundHolder = holder
        refresh()
    }

    /**
     * [selectedOverride], when given, is used for the bold-highlighting only
     * (the row for that format) — needed right after the `ListPreference`'s
     * `onPreferenceChangeListener` fires, since it reports the new value
     * *before* it's actually persisted to SharedPreferences. Every row's
     * own rendered sample is unaffected either way, since each is rendered
     * with its own format explicitly, not the "current" one.
     */
    fun refresh(selectedOverride: OutputFormat? = null) {
        val holder = boundHolder ?: return
        val app = context.applicationContext as? JapanglifyApp ?: return
        val baseSettings = app.preferences.load()
        val selected = selectedOverride ?: baseSettings.outputFormat
        val sample = context.getString(R.string.output_format_preview_sample)

        for (fmt in OutputFormat.entries) {
            val labelView = holder.findViewById(labelId(fmt)) as? TextView ?: continue
            val sampleView = holder.findViewById(sampleId(fmt)) as? TextView ?: continue
            val isSelected = fmt == selected
            labelView.text = if (isSelected) {
                context.getString(R.string.output_format_preview_selected_label, fmt.displayName)
            } else {
                fmt.displayName
            }
            labelView.setTypeface(null, if (isSelected) Typeface.BOLD else Typeface.NORMAL)
            sampleView.text = runCatching {
                app.engine.expand(sample, baseSettings.copy(outputFormat = fmt))
            }.getOrDefault("")
        }
    }

    private fun labelId(fmt: OutputFormat): Int = when (fmt) {
        OutputFormat.FURIGANA_INLINE -> R.id.output_format_label_furigana_inline
        OutputFormat.PARENTHETICAL -> R.id.output_format_label_parenthetical
        OutputFormat.INTERLINEAR -> R.id.output_format_label_interlinear
        OutputFormat.HTML_RUBY -> R.id.output_format_label_html_ruby
        OutputFormat.COMPACT -> R.id.output_format_label_compact
    }

    private fun sampleId(fmt: OutputFormat): Int = when (fmt) {
        OutputFormat.FURIGANA_INLINE -> R.id.output_format_sample_furigana_inline
        OutputFormat.PARENTHETICAL -> R.id.output_format_sample_parenthetical
        OutputFormat.INTERLINEAR -> R.id.output_format_sample_interlinear
        OutputFormat.HTML_RUBY -> R.id.output_format_sample_html_ruby
        OutputFormat.COMPACT -> R.id.output_format_sample_compact
    }
}
