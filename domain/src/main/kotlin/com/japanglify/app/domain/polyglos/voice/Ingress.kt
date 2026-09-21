package com.japanglify.app.domain.polyglos.voice

/**
 * How text arrived at expand. Linguistics ignore this; presenters MAY log it.
 *
 * Clipboard input is not an ingress — do not add [CLIPBOARD].
 */
enum class IngressSource {
    PROCESS_TEXT,
    SHARE,
    VOICE
}

data class IngressEvent(
    val source: IngressSource,
    val text: String,
    val localeHint: String? = null
)
