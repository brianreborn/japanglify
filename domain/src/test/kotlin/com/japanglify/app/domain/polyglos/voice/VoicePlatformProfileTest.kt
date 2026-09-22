package com.japanglify.app.domain.polyglos.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VoicePlatformProfileTest {
    @Test fun note9Api29() {
        val p = VoicePlatformProfile.note9()
        assertEquals(29, p.sdk)
        assertFalse(p.onDeviceSpeechRecognizerApi)
        assertFalse(p.recognitionSupportApi)
        assertFalse(p.segmentedSessionApi)
        assertFalse(p.microphoneForegroundServiceType)
        assertTrue(p.restartListeningAfterFinal)
        assertTrue(p.roleAssistantApi)
    }

    @Test fun android15Matches9185WAndGrapheneosPixel8Gates() {
        val w = VoicePlatformProfile.forSdk(35)
        val graphene = VoicePlatformProfile.android15()
        assertEquals(w, graphene)
        assertTrue(w.onDeviceSpeechRecognizerApi)
        assertTrue(w.recognitionSupportApi)
        assertTrue(w.segmentedSessionApi)
        assertTrue(w.microphoneForegroundServiceType)
        assertFalse(w.restartListeningAfterFinal)
        assertTrue(w.roleAssistantApi)
    }

    @Test fun api31GainsOnDeviceRecognizerNotSegmented() {
        val p = VoicePlatformProfile.forSdk(31)
        assertTrue(p.onDeviceSpeechRecognizerApi)
        assertTrue(p.microphoneForegroundServiceType)
        assertFalse(p.segmentedSessionApi)
        assertTrue(p.restartListeningAfterFinal)
    }
}
