package com.japanglify.app.domain.polyglos.voice

/** Later Android `VoiceInteractionSession` / in-app Start implements this. */
interface VoiceCallHost {
    fun showListening()
    fun showPartial(text: String)
    fun showIdle()
    fun showFailed(reason: String)
}

interface VoiceCallController {
    fun start()
    fun hangUp()
    fun fail(reason: String)
    fun onAsrPartial(text: String)
    fun onAsrFinal(text: String, localeHint: String?)
}

interface VoiceExpandSink {
    fun submit(event: IngressEvent)
}

interface AsrListener {
    fun onPartial(text: String)
    fun onFinal(text: String, localeHint: String?)
    fun onFailed(reason: String)
}

interface AsrBackend {
    fun isAvailableOffline(locale: String): Boolean
    fun supportedLocales(): Set<String>
    fun start(localeHint: String?, listener: AsrListener)
    fun stop()
    fun cancel()
}
