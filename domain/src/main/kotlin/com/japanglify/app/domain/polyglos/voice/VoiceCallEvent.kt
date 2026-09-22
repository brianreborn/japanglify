package com.japanglify.app.domain.polyglos.voice

sealed class VoiceCallEvent {
    data object Started : VoiceCallEvent()
    data class Partial(val text: String) : VoiceCallEvent()
    data class Final(val text: String, val localeHint: String? = null) : VoiceCallEvent()
    data object HungUp : VoiceCallEvent()
    data class Failed(val reason: String) : VoiceCallEvent()
}

enum class VoiceCallPhase {
    IDLE,
    LIVE,
    HUNG_UP,
    FAILED
}
