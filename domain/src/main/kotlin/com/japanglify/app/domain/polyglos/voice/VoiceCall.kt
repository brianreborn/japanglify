package com.japanglify.app.domain.polyglos.voice

object VoiceCall {
    fun toIngressEvent(event: VoiceCallEvent.Final): IngressEvent? {
        val text = event.text.trim()
        if (text.isEmpty()) return null
        return IngressEvent(
            source = IngressSource.VOICE,
            text = text,
            localeHint = event.localeHint
        )
    }
}
