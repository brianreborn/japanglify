package com.japanglify.app.translate

import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URI
import java.net.URLEncoder
import javax.net.ssl.HttpsURLConnection

/**
 * Free, unofficial Google Translate web endpoint (client=gtx) — no API key,
 * no setup, matching the rest of the app's zero-configuration philosophy.
 * Undocumented and could be rate-limited or broken by Google without notice;
 * any failure here is silently swallowed (see [TranslationProvider]).
 */
class GoogleWebTranslator(
    private val sourceLang: String = "ja",
    private val targetLang: String = "en"
) : TranslationProvider {

    override fun translate(text: String): String? {
        if (text.isBlank()) return null
        return runCatching {
            val encoded = URLEncoder.encode(text, "UTF-8")
            val url = URI(
                "https://translate.googleapis.com/translate_a/single" +
                    "?client=gtx&sl=$sourceLang&tl=$targetLang&dt=t&q=$encoded"
            ).toURL()
            val connection = url.openConnection() as HttpsURLConnection
            connection.connectTimeout = TIMEOUT_MS
            connection.readTimeout = TIMEOUT_MS
            connection.requestMethod = "GET"
            try {
                if (connection.responseCode != HttpURLConnection.HTTP_OK) return null
                val body = connection.inputStream.bufferedReader().use { it.readText() }
                parseTranslation(body)
            } finally {
                connection.disconnect()
            }
        }.getOrNull()?.takeIf { it.isNotBlank() }
    }

    /**
     * Response shape: `[[[translated, original, null, null, 1], ...], null, "ja", ...]`
     * — one chunk per sentence the endpoint split the input into.
     */
    private fun parseTranslation(body: String): String? {
        val chunks = JSONArray(body).optJSONArray(0) ?: return null
        return buildString {
            for (i in 0 until chunks.length()) {
                val chunk = chunks.optJSONArray(i) ?: continue
                append(chunk.optString(0, ""))
            }
        }
    }

    companion object {
        private const val TIMEOUT_MS = 4_000
    }
}
