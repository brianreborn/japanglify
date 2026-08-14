package com.japanglify.app.ui

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.widget.TextView
import androidx.preference.Preference
import androidx.preference.PreferenceViewHolder
import com.google.android.material.button.MaterialButton
import com.japanglify.app.R
import com.japanglify.app.dictionary.DictionaryDownloadStatus

/**
 * Download/progress/ready/delete status card for a [com.japanglify.app.domain.dictionary.DictionarySource]
 * -- same "real Preference item with a button" shape as [TryItCardPreference],
 * driven externally via [render] rather than owning its own state (matching
 * [OutputFormatPreviewPreference]'s external-refresh pattern), since the
 * fragment is the one polling [com.japanglify.app.data.PreferencesRepository]
 * for the authoritative status.
 */
class DictionaryStatusPreference(context: Context, attrs: AttributeSet?) : Preference(context, attrs) {

    var onDownloadClicked: (() -> Unit)? = null
    var onDeleteClicked: (() -> Unit)? = null
    var onCancelClicked: (() -> Unit)? = null

    init {
        layoutResource = R.layout.preference_dictionary_status
        isSelectable = false
    }

    private var statusView: TextView? = null
    private var downloadButton: MaterialButton? = null
    private var deleteButton: MaterialButton? = null
    private var cancelButton: MaterialButton? = null
    private var pendingState: State? = null

    data class State(
        val sourceName: String,
        val status: DictionaryDownloadStatus,
        val percent: Int?,
        val wordsImported: Int,
        val errorMessage: String?
    )

    override fun onBindViewHolder(holder: PreferenceViewHolder) {
        super.onBindViewHolder(holder)
        statusView = holder.findViewById(R.id.dictionary_status_text) as? TextView
        downloadButton = (holder.findViewById(R.id.btn_dictionary_download) as? MaterialButton)?.apply {
            setOnClickListener { onDownloadClicked?.invoke() }
        }
        deleteButton = (holder.findViewById(R.id.btn_dictionary_delete) as? MaterialButton)?.apply {
            setOnClickListener { onDeleteClicked?.invoke() }
        }
        cancelButton = (holder.findViewById(R.id.btn_dictionary_cancel) as? MaterialButton)?.apply {
            setOnClickListener { onCancelClicked?.invoke() }
        }
        pendingState?.let { applyState(it) }
    }

    /** Safe even before the view is bound -- applied as soon as it is. */
    fun render(state: State) {
        pendingState = state
        applyState(state)
    }

    private fun applyState(state: State) {
        val statusView = statusView ?: return
        statusView.text = when (state.status) {
            DictionaryDownloadStatus.NOT_DOWNLOADED ->
                context.getString(R.string.dictionary_status_not_downloaded, state.sourceName)
            DictionaryDownloadStatus.DOWNLOADING ->
                context.getString(R.string.dictionary_status_downloading, state.sourceName, state.percent ?: 0)
            DictionaryDownloadStatus.PARSING ->
                context.getString(R.string.dictionary_status_parsing, state.sourceName, state.wordsImported)
            DictionaryDownloadStatus.READY ->
                context.getString(R.string.dictionary_status_ready, state.sourceName, state.wordsImported)
            DictionaryDownloadStatus.FAILED ->
                context.getString(R.string.dictionary_status_failed, state.sourceName, state.errorMessage.orEmpty())
        }

        val busy = state.status == DictionaryDownloadStatus.DOWNLOADING ||
            state.status == DictionaryDownloadStatus.PARSING
        val downloadable = state.status == DictionaryDownloadStatus.NOT_DOWNLOADED ||
            state.status == DictionaryDownloadStatus.FAILED

        downloadButton?.visibility = if (downloadable) View.VISIBLE else View.GONE
        downloadButton?.setText(R.string.dictionary_action_download)
        cancelButton?.visibility = if (busy) View.VISIBLE else View.GONE
        deleteButton?.visibility = if (state.status == DictionaryDownloadStatus.READY) View.VISIBLE else View.GONE
    }
}
