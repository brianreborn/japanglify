package com.japanglify.app.ui

import android.content.Context
import android.text.Editable
import android.text.TextWatcher
import android.util.AttributeSet
import android.widget.EditText
import android.widget.TextView
import androidx.preference.Preference
import androidx.preference.PreferenceViewHolder
import com.google.android.material.button.MaterialButton
import com.japanglify.app.R

/**
 * "Try it here" content (input field, live output, convert buttons) as a
 * real Preference item — see [StatusCardPreference] for why.
 *
 * Debouncing text-change events is the caller's job (it owns the timing
 * policy); this class only forwards the raw text as it changes.
 */
class TryItCardPreference(context: Context, attrs: AttributeSet?) : Preference(context, attrs) {

    var onTextChanged: ((String) -> Unit)? = null
    var onConvertSelectionOrAll: (() -> Unit)? = null
    var onConvertClipboard: (() -> Unit)? = null

    init {
        layoutResource = R.layout.preference_try_it_card
        isSelectable = false
    }

    private var inputView: EditText? = null
    private var outputView: TextView? = null
    private var pendingText: String? = null

    private val watcher = object : TextWatcher {
        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
        override fun afterTextChanged(s: Editable?) {
            onTextChanged?.invoke(s?.toString().orEmpty())
        }
    }

    override fun onBindViewHolder(holder: PreferenceViewHolder) {
        super.onBindViewHolder(holder)
        inputView?.removeTextChangedListener(watcher)

        val input = holder.findViewById(R.id.try_it_text) as? EditText
        inputView = input
        outputView = holder.findViewById(R.id.try_it_output) as? TextView

        pendingText?.let {
            input?.setText(it)
            pendingText = null
        }
        input?.addTextChangedListener(watcher)

        (holder.findViewById(R.id.btn_japanglify_here) as? MaterialButton)?.setOnClickListener {
            onConvertSelectionOrAll?.invoke()
        }
        (holder.findViewById(R.id.btn_japanglify_clipboard) as? MaterialButton)?.setOnClickListener {
            onConvertClipboard?.invoke()
        }
    }

    fun currentText(): String = inputView?.text?.toString().orEmpty()
    fun selectionStart(): Int = inputView?.selectionStart?.coerceAtLeast(0) ?: 0
    fun selectionEnd(): Int = inputView?.selectionEnd?.coerceAtLeast(0) ?: 0
    fun substring(start: Int, end: Int): String = inputView?.text?.substring(start, end).orEmpty()

    fun replaceRange(start: Int, end: Int, text: String) {
        inputView?.text?.replace(start, end, text)
    }

    /** Safe even before the view is bound — applied as soon as it is. */
    fun setText(text: String) {
        val input = inputView
        if (input != null) input.setText(text) else pendingText = text
    }

    fun setSelectionEnd(pos: Int) {
        inputView?.setSelection(pos)
    }

    fun setOutput(text: CharSequence) {
        outputView?.text = text
    }
}
