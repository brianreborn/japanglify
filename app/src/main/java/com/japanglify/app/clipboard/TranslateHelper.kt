package com.japanglify.app.clipboard

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import com.japanglify.app.R

/**
 * Utility for launching Google Translate without requiring root permissions.
 *
 * Tries the official Google Translate app via Intent.ACTION_SEND first,
 * falling back to translate.google.com in the default browser.
 */
object TranslateHelper {

    fun launchGoogleTranslate(context: Context, text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return

        val appIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, trimmed)
            setPackage("com.google.android.apps.translate")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        try {
            context.startActivity(appIntent)
        } catch (_: ActivityNotFoundException) {
            val webUri = Uri.parse(
                "https://translate.google.com/?sl=ja&tl=en&text=${Uri.encode(trimmed)}"
            )
            val webIntent = Intent(Intent.ACTION_VIEW, webUri).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            try {
                context.startActivity(webIntent)
            } catch (_: Exception) {
                Toast.makeText(
                    context,
                    R.string.error_processing_generic,
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }
}
