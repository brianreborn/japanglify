package com.japanglify.app.domain.polyglos.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceCallStateMachineTest {
    private val machine = VoiceCallStateMachine()

    @Test fun startPartialFinalHangUp() {
        assertEquals(VoiceCallPhase.IDLE, machine.phase)
        assertTrue(machine.apply(VoiceCallEvent.Started).accepted)
        assertEquals(VoiceCallPhase.LIVE, machine.phase)

        val partial = machine.apply(VoiceCallEvent.Partial("λόγ"))
        assertTrue(partial.accepted)
        assertEquals("λόγ", partial.previewText)
        assertNull(partial.ingress)

        val final = machine.apply(VoiceCallEvent.Final("λόγος", localeHint = "el-GR"))
        assertTrue(final.accepted)
        assertNull(final.previewText)
        val ingress = final.ingress
        requireNotNull(ingress)
        assertEquals(IngressSource.VOICE, ingress.source)
        assertEquals("λόγος", ingress.text)
        assertEquals("el-GR", ingress.localeHint)
        assertEquals(VoiceCallPhase.LIVE, machine.phase)

        val hung = machine.apply(VoiceCallEvent.HungUp)
        assertTrue(hung.accepted)
        assertNull(hung.ingress)
        assertEquals(VoiceCallPhase.HUNG_UP, machine.phase)
    }

    @Test fun blankFinalDoesNotIngress() {
        machine.apply(VoiceCallEvent.Started)
        val step = machine.apply(VoiceCallEvent.Final("   "))
        assertTrue(step.accepted)
        assertNull(step.ingress)
        assertEquals(VoiceCallPhase.LIVE, machine.phase)
    }

    @Test fun partialOutsideLiveRejected() {
        val step = machine.apply(VoiceCallEvent.Partial("nope"))
        assertFalse(step.accepted)
        assertEquals(VoiceCallPhase.IDLE, machine.phase)
        assertNull(step.ingress)
    }

    @Test fun finalOutsideLiveRejected() {
        val step = machine.apply(VoiceCallEvent.Final("λόγος"))
        assertFalse(step.accepted)
        assertNull(step.ingress)
    }

    @Test fun nestedStartRejected() {
        machine.apply(VoiceCallEvent.Started)
        val nested = machine.apply(VoiceCallEvent.Started)
        assertFalse(nested.accepted)
        assertEquals(VoiceCallPhase.LIVE, machine.phase)
    }

    @Test fun failedFromLive() {
        machine.apply(VoiceCallEvent.Started)
        val step = machine.apply(VoiceCallEvent.Failed("offline unavailable"))
        assertTrue(step.accepted)
        assertEquals(VoiceCallPhase.FAILED, machine.phase)
        assertEquals("offline unavailable", step.failureReason)
        assertNull(step.ingress)
    }

    @Test fun restartAfterHangUp() {
        machine.apply(VoiceCallEvent.Started)
        machine.apply(VoiceCallEvent.HungUp)
        val again = machine.apply(VoiceCallEvent.Started)
        assertTrue(again.accepted)
        assertEquals(VoiceCallPhase.LIVE, machine.phase)
    }

    @Test fun ingressSourceHasNoClipboard() {
        val names = IngressSource.entries.map { it.name }
        assertEquals(listOf("PROCESS_TEXT", "SHARE", "VOICE"), names)
    }

    @Test fun toIngressEventTrims() {
        val event = VoiceCall.toIngressEvent(VoiceCallEvent.Final("  日本  "))
        requireNotNull(event)
        assertEquals("日本", event.text)
        assertEquals(IngressSource.VOICE, event.source)
    }
}
