package com.japanglify.app.ui

import android.content.Context
import android.util.AttributeSet
import android.widget.TextView
import androidx.preference.Preference
import androidx.preference.PreferenceViewHolder
import com.japanglify.app.R

/**
 * Header content (PROCESS_TEXT registration status + copy-hook log) as a
 * real Preference item, so it lives inside the Preference [RecyclerView]
 * instead of a second, nested scroll container.
 */
class StatusCardPreference(context: Context, attrs: AttributeSet?) : Preference(context, attrs) {

    init {
        layoutResource = R.layout.preference_status_card
        isSelectable = false
    }

    private var registrationText: CharSequence? = null
    private var registrationColor: Int? = null
    private var handlersText: CharSequence? = null
    private var hookLogText: CharSequence? = null

    fun updateStatus(
        registrationText: CharSequence,
        registrationColor: Int,
        handlersText: CharSequence,
        hookLogText: CharSequence
    ) {
        this.registrationText = registrationText
        this.registrationColor = registrationColor
        this.handlersText = handlersText
        this.hookLogText = hookLogText
        notifyChanged()
    }

    override fun onBindViewHolder(holder: PreferenceViewHolder) {
        super.onBindViewHolder(holder)
        (holder.findViewById(R.id.status_registration) as? TextView)?.apply {
            registrationText?.let { text = it }
            registrationColor?.let { setTextColor(it) }
        }
        (holder.findViewById(R.id.status_handlers) as? TextView)?.apply {
            handlersText?.let { text = it }
        }
        (holder.findViewById(R.id.status_copy_hook_log) as? TextView)?.apply {
            hookLogText?.let { text = it }
        }
    }
}
