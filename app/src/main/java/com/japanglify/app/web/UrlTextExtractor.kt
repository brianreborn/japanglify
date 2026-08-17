package com.japanglify.app.web

import org.jsoup.Jsoup

/**
 * Fetches a shared URL and extracts its visible body text, for
 * [com.japanglify.app.ShareTargetActivity]'s "share a URL from a browser"
 * path. Deliberately does not try to guess "the article" vs. nav/boilerplate
 * -- real per-site heuristics for that are a rabbit hole of their own and
 * every one of them is wrong somewhere. Instead this hands the *whole*
 * page's text to the user in an editable field (see
 * [com.japanglify.app.ui.SettingsFragment]'s Try-It card) and lets them
 * trim the leading/trailing parts they don't want themselves -- simpler,
 * and correct by construction instead of by heuristic.
 */
object UrlTextExtractor {
    private const val TIMEOUT_MS = 20_000
    /** Generous but bounded -- this is pasted into an editable field, not stored. */
    private const val MAX_CHARS = 40_000

    /**
     * Returns the page's extracted body text, or null on any failure (bad
     * URL, network error, empty page). Uses Jsoup's own connection
     * ([org.jsoup.Connection]) rather than a raw `HttpURLConnection` for two
     * reasons at once: the whole fetch-and-parse sequence is one call inside
     * a single try/catch, so a malformed [urlString] (e.g. a scheme-less
     * match [android.util.Patterns.WEB_URL] accepts but `java.net.URL`
     * itself rejects) can't throw uncaught partway through construction the
     * way a bare `URL(urlString).openConnection()` call could; and Jsoup
     * decodes the response with its *real* charset (the `Content-Type`
     * header, falling back to the page's own `<meta charset>`) instead of
     * assuming UTF-8, which silently produced mojibake on the
     * Shift_JIS/EUC-JP Japanese sites this app specifically cares about
     * getting right.
     */
    fun fetchAndExtractText(urlString: String): String? = try {
        val doc = Jsoup.connect(urlString)
            .timeout(TIMEOUT_MS)
            .userAgent("Mozilla/5.0 (Android) Japanglify/1.0")
            .followRedirects(true)
            .get()
        val text = doc.body()?.text()?.trim()
        text?.takeIf { it.isNotEmpty() }?.take(MAX_CHARS)
    } catch (_: Exception) {
        null
    }
}
