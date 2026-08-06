package com.japanglify.app.clipboard

import android.content.BroadcastReceiver
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.core.app.NotificationManagerCompat
import com.japanglify.app.JapanglifyApp
import com.japanglify.app.R
import com.japanglify.app.data.PreferencesRepository

/**
 * Handles notification actions for clipboard assist (stop / copy result).
 * Copy always goes through [LastResultStore.writeToClipboard] so listeners ignore it.
 */
class ClipboardAssistReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        when (intent?.action) {
            ACTION_STOP -> {
                androidx.preference.PreferenceManager
                    .getDefaultSharedPreferences(context)
                    .edit()
                    .putBoolean(PreferencesRepository.KEY_CLIPBOARD_ASSIST, false)
                    .apply()
                ClipboardAssistService.stop(context)
                NotificationManagerCompat.from(context)
                    .cancel(ClipboardNotifications.ID_LISTENING)
                Toast.makeText(
                    context,
                    R.string.clipboard_assist_stopped,
                    Toast.LENGTH_SHORT
                ).show()
            }

            ACTION_COPY_RESULT -> {
                val result = LastResultStore.load(context)
                if (result.isNullOrEmpty()) {
                    Toast.makeText(
                        context,
                        R.string.clipboard_assist_no_result,
                        Toast.LENGTH_SHORT
                    ).show()
                    return
                }
                // Suppress + label + ring buffer — must happen before setPrimaryClip
                LastResultStore.writeToClipboard(context, result)
                NotificationManagerCompat.from(context)
                    .cancel(ClipboardNotifications.ID_RESULT)
                Toast.makeText(
                    context,
                    R.string.notif_copied_ready_to_paste,
                    Toast.LENGTH_LONG
                ).show()
            }

            ACTION_COPY_IMAGE -> {
                // load(), not the raw property: the process may have been
                // recycled since the result was shown, which resets in-memory
                // state — load() falls back to SharedPreferences and also
                // repopulates lastSource as a side effect.
                LastResultStore.load(context)
                val source = LastResultStore.lastSource
                val app = context.applicationContext as? JapanglifyApp
                if (source.isNullOrEmpty() || app == null) {
                    Toast.makeText(
                        context,
                        R.string.clipboard_assist_no_result,
                        Toast.LENGTH_SHORT
                    ).show()
                    return
                }
                // Re-wrap at a column budget sized to the host's own input width when
                // we captured one, so the pasted image looks native there rather than
                // reusing whatever width the on-screen notification text happened to use.
                val baseSettings = app.preferences.load()
                val hostWidthPx = LastResultStore.lastHostFieldWidthPx
                val unitPx = ClipboardImageRenderer.fullwidthUnitPx(context)
                val settingsForImage = if (hostWidthPx != null && hostWidthPx > 0 && unitPx > 0f) {
                    val usablePx = hostWidthPx - ClipboardImageRenderer.paddingPx(context) * 2
                    val units = (usablePx / unitPx).toInt().coerceIn(6, 40)
                    baseSettings.copy(maxLineWidthFullwidth = units)
                } else {
                    baseSettings
                }
                val bitmap = if (settingsForImage.outputFormat == com.japanglify.app.domain.OutputFormat.INTERLINEAR) {
                    val rows = runCatching {
                        app.engine.buildInterlinearRows(source, settingsForImage)
                    }.getOrNull()
                    if (rows.isNullOrEmpty()) {
                        Toast.makeText(
                            context,
                            R.string.error_processing_generic,
                            Toast.LENGTH_SHORT
                        ).show()
                        return
                    }
                    ClipboardImageRenderer.renderInterlinearToBitmap(context, rows, settingsForImage)
                } else {
                    val rendered = runCatching { app.engine.expand(source, settingsForImage) }.getOrNull()
                    if (rendered.isNullOrEmpty()) {
                        Toast.makeText(
                            context,
                            R.string.error_processing_generic,
                            Toast.LENGTH_SHORT
                        ).show()
                        return
                    }
                    ClipboardImageRenderer.renderToBitmap(context, rendered)
                }
                val uri = ClipboardImageRenderer.saveAndGetUri(context, bitmap)
                // Suppress + ring-buffer the URI text form so our own clipboard write
                // is never mistaken for new user text by the Copy hook.
                LastResultStore.beginOutgoingWrite(uri.toString())
                val cm = context.getSystemService(ClipboardManager::class.java)
                cm?.setPrimaryClip(ClipData.newUri(context.contentResolver, LastResultStore.CLIP_LABEL, uri))
                NotificationManagerCompat.from(context)
                    .cancel(ClipboardNotifications.ID_RESULT)
                Toast.makeText(
                    context,
                    R.string.notif_copied_image_ready,
                    Toast.LENGTH_LONG
                ).show()
            }

            ACTION_REPLACE_FIELD -> {
                val result = LastResultStore.load(context)
                val service = JapanglifyAccessibilityService.instance
                if (result.isNullOrEmpty() || service == null) {
                    Toast.makeText(
                        context,
                        R.string.clipboard_assist_no_result,
                        Toast.LENGTH_SHORT
                    ).show()
                    return
                }
                // Re-query the live focused node rather than trusting anything
                // captured at Copy time — see replaceFocusedField's doc.
                val ok = service.replaceFocusedField(result)
                if (ok) {
                    NotificationManagerCompat.from(context)
                        .cancel(ClipboardNotifications.ID_RESULT)
                    Toast.makeText(context, R.string.notif_field_replaced, Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(
                        context,
                        R.string.notif_field_replace_failed,
                        Toast.LENGTH_LONG
                    ).show()
                }
            }

            ACTION_TOGGLE_COPY_PAUSE -> {
                val prefs = androidx.preference.PreferenceManager.getDefaultSharedPreferences(context)
                val paused = !prefs.getBoolean(PreferencesRepository.KEY_COPY_HOOK_PAUSED, false)
                prefs.edit().putBoolean(PreferencesRepository.KEY_COPY_HOOK_PAUSED, paused).apply()
                ClipboardNotifications.showHookArmed(context)
            }

            ACTION_TRANSLATE -> {
                val source = LastResultStore.lastSource?.trim().orEmpty()
                val result = LastResultStore.load(context)?.trim().orEmpty()
                val textToTranslate = source.ifEmpty { result }
                if (textToTranslate.isNotEmpty()) {
                    NotificationManagerCompat.from(context)
                        .cancel(ClipboardNotifications.ID_RESULT)
                    TranslateHelper.launchGoogleTranslate(context, textToTranslate)
                } else {
                    Toast.makeText(
                        context,
                        R.string.clipboard_assist_no_result,
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    companion object {
        const val ACTION_STOP = "com.japanglify.app.action.STOP_CLIPBOARD_ASSIST"
        const val ACTION_COPY_RESULT = "com.japanglify.app.action.COPY_JAPANGLIFY_RESULT"
        const val ACTION_COPY_IMAGE = "com.japanglify.app.action.COPY_JAPANGLIFY_IMAGE"
        const val ACTION_REPLACE_FIELD = "com.japanglify.app.action.REPLACE_FIELD"
        const val ACTION_TRANSLATE = "com.japanglify.app.action.TRANSLATE"
        const val ACTION_TOGGLE_COPY_PAUSE = "com.japanglify.app.action.TOGGLE_COPY_PAUSE"
    }
}
