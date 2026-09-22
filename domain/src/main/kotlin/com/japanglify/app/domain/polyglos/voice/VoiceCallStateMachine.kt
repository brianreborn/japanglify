package com.japanglify.app.domain.polyglos.voice

data class VoiceCallStep(
    val phase: VoiceCallPhase,
    val accepted: Boolean,
    val previewText: String? = null,
    val ingress: IngressEvent? = null,
    val failureReason: String? = null
)

/**
 * Pure JVM call beats: start / partials / final / hang up.
 * Partials never produce [ingress]. Hang-up never presents.
 */
class VoiceCallStateMachine {
    var phase: VoiceCallPhase = VoiceCallPhase.IDLE
        private set

    fun apply(event: VoiceCallEvent): VoiceCallStep {
        val step = transition(event)
        if (step.accepted) {
            phase = step.phase
        }
        return step
    }

    fun reset() {
        phase = VoiceCallPhase.IDLE
    }

    private fun transition(event: VoiceCallEvent): VoiceCallStep {
        return when (event) {
            VoiceCallEvent.Started -> when (phase) {
                VoiceCallPhase.IDLE,
                VoiceCallPhase.HUNG_UP,
                VoiceCallPhase.FAILED -> accept(VoiceCallPhase.LIVE)
                VoiceCallPhase.LIVE -> reject()
            }
            is VoiceCallEvent.Partial -> if (phase == VoiceCallPhase.LIVE) {
                accept(VoiceCallPhase.LIVE, previewText = event.text)
            } else {
                reject()
            }
            is VoiceCallEvent.Final -> if (phase == VoiceCallPhase.LIVE) {
                accept(VoiceCallPhase.LIVE, ingress = VoiceCall.toIngressEvent(event))
            } else {
                reject()
            }
            VoiceCallEvent.HungUp -> when (phase) {
                VoiceCallPhase.LIVE,
                VoiceCallPhase.IDLE,
                VoiceCallPhase.FAILED -> accept(VoiceCallPhase.HUNG_UP)
                VoiceCallPhase.HUNG_UP -> reject()
            }
            is VoiceCallEvent.Failed -> if (phase == VoiceCallPhase.LIVE) {
                accept(VoiceCallPhase.FAILED, failureReason = event.reason)
            } else {
                reject()
            }
        }
    }

    private fun accept(
        next: VoiceCallPhase,
        previewText: String? = null,
        ingress: IngressEvent? = null,
        failureReason: String? = null
    ) = VoiceCallStep(
        phase = next,
        accepted = true,
        previewText = previewText,
        ingress = ingress,
        failureReason = failureReason
    )

    private fun reject() = VoiceCallStep(phase = phase, accepted = false)
}
