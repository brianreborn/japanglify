package com.japanglify.app.ui

import android.content.Context
import android.graphics.Paint
import android.text.Editable
import android.text.TextWatcher
import android.util.AttributeSet
import android.view.View
import android.widget.EditText
import android.widget.SeekBar
import android.widget.TextView
import androidx.preference.Preference
import androidx.preference.PreferenceViewHolder
import com.japanglify.app.JapanglifyApp
import com.japanglify.app.R
import com.japanglify.app.data.PreferencesRepository
import com.japanglify.app.domain.JapanglifySettings
import com.japanglify.app.domain.OutputFormat
import com.japanglify.app.domain.TripleScriptRenderer
import kotlin.math.roundToInt

/**
 * Max line width as a slider + number box instead of a fixed-choice dropdown
 * — the useful values form a continuous range (any host might need a
 * narrower or wider wrap than whatever handful of presets a list offers), so
 * picking from a short list was always an artificial constraint.
 *
 * Stored as a String under the same key the old ListPreference used
 * ("max_line_width_fullwidth"), so no migration is needed — only how the
 * value is chosen changes, not how it's persisted or read
 * ([PreferencesRepository.parseMaxLineWidth]).
 */
class MaxLineWidthPreference(context: Context, attrs: AttributeSet?) : Preference(context, attrs) {

    init {
        layoutResource = R.layout.preference_max_line_width
    }

    private var seekBar: SeekBar? = null
    private var valueField: EditText? = null
    private var previewSmall: TextView? = null
    private var previewDefault: TextView? = null
    private var previewMonospace: TextView? = null

    /** Guards against the SeekBar and EditText re-triggering each other while one is updating. */
    private var syncing = false

    private val textWatcher = object : TextWatcher {
        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
        override fun afterTextChanged(s: Editable?) {
            if (syncing) return
            val typed = s?.toString()?.toIntOrNull() ?: return
            applyValue(clamp(typed), fromField = true)
        }
    }

    private val seekListener = object : SeekBar.OnSeekBarChangeListener {
        override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
            if (!fromUser) return
            applyValue(progress, fromField = false)
        }
        override fun onStartTrackingTouch(seekBar: SeekBar) = Unit
        override fun onStopTrackingTouch(seekBar: SeekBar) = Unit
    }

    override fun onBindViewHolder(holder: PreferenceViewHolder) {
        super.onBindViewHolder(holder)
        seekBar?.setOnSeekBarChangeListener(null)
        valueField?.removeTextChangedListener(textWatcher)

        val sb = holder.findViewById(R.id.max_line_width_seekbar) as? SeekBar
        val field = holder.findViewById(R.id.max_line_width_value) as? EditText
        seekBar = sb
        valueField = field
        previewSmall = holder.findViewById(R.id.max_line_width_preview_small) as? TextView
        previewDefault = holder.findViewById(R.id.max_line_width_preview_default) as? TextView
        previewMonospace = holder.findViewById(R.id.max_line_width_preview_monospace) as? TextView

        val current = clamp(
            PreferencesRepository.parseMaxLineWidth(getPersistedString(DEFAULT.toString()))
        )
        setFieldsSilently(current)
        updatePreview(current)

        sb?.setOnSeekBarChangeListener(seekListener)
        field?.addTextChangedListener(textWatcher)
        field?.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) {
                // Re-normalize on blur (e.g. field left blank or out of range).
                val normalized = clamp(field.text?.toString()?.toIntOrNull() ?: current)
                setFieldsSilently(normalized)
                updatePreview(normalized)
            }
        }
    }

    private fun applyValue(value: Int, fromField: Boolean) {
        setFieldsSilently(value, skipField = fromField)
        updatePreview(value)
        persistString(value.toString())
    }

    /**
     * Renders the same sample phrase used by the "Try it" card at this
     * candidate width, so the effect of dragging the slider is visible
     * immediately — under a few representative fonts/sizes, since the actual
     * wrap point in any given host depends on a font/size we don't control.
     *
     * Each swatch is rendered separately with its own [calibratedCjkWidth],
     * rather than sharing one string across all three: the column padding
     * ([TripleScriptRenderer.PAD] repeats) is only ever as aligned as the
     * assumed CJK-glyph-to-pad-glyph width ratio it was built with, and that
     * ratio is a font/size property, not a universal constant. Measuring it
     * per swatch, in the exact [Paint] each one will actually draw with,
     * can't produce guaranteed alignment in a proportional font (glyph
     * widths still vary letter to letter) but removes the dominant
     * systematic drift, which is a real, visible improvement.
     */
    private fun updatePreview(value: Int) {
        val app = context.applicationContext as? JapanglifyApp ?: return
        val baseSettings = app.preferences.load()
        val sample = context.getString(R.string.try_it_sample)
        for (view in listOfNotNull(previewSmall, previewDefault, previewMonospace)) {
            val settings = baseSettings.copy(
                outputFormat = OutputFormat.INTERLINEAR,
                maxLineWidthFullwidth = value,
                cjkDisplayWidthUnits = calibratedCjkWidth(view.paint)
            )
            view.text = runCatching { app.engine.expand(sample, settings) }.getOrNull().orEmpty()
        }
    }

    /**
     * Ratio of a CJK glyph's rendered width to [TripleScriptRenderer.PAD]'s,
     * in [paint] specifically — Android resolves font fallback (e.g. for
     * ideographs a Latin-only typeface doesn't cover) per glyph during
     * [Paint.measureText] itself, so this reflects whatever will actually
     * be drawn, not an assumption about the named typeface alone.
     */
    private fun calibratedCjkWidth(paint: Paint): Int {
        val padPx = paint.measureText(TripleScriptRenderer.PAD)
        if (padPx <= 0f) return JapanglifySettings.DEFAULT_CJK_DISPLAY_WIDTH_UNITS
        val cjkPx = paint.measureText(SAMPLE_CJK_CHAR)
        return (cjkPx / padPx).roundToInt().coerceIn(1, 4)
    }

    /** Updates both widgets without re-firing each other's listeners. */
    private fun setFieldsSilently(value: Int, skipField: Boolean = false) {
        syncing = true
        seekBar?.progress = value
        if (!skipField) {
            valueField?.let {
                if (it.text?.toString() != value.toString()) {
                    it.setText(value.toString())
                    it.setSelection(it.text?.length ?: 0)
                }
            }
        }
        syncing = false
    }

    private fun clamp(value: Int): Int = value.coerceIn(0, MAX_SLIDER)

    override fun onGetDefaultValue(a: android.content.res.TypedArray, index: Int): Any? =
        a.getString(index)

    override fun onSetInitialValue(defaultValue: Any?) {
        val initial = clamp(
            PreferencesRepository.parseMaxLineWidth(
                getPersistedString(defaultValue as? String ?: DEFAULT.toString())
            )
        )
        setFieldsSilently(initial)
    }

    companion object {
        private const val MAX_SLIDER = 32
        private const val DEFAULT = 14

        /** One full-width kana glyph — representative CJK width for [calibratedCjkWidth]. */
        private const val SAMPLE_CJK_CHAR = "あ"
    }
}
