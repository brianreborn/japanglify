package com.japanglify.app.domain.polyglos.voice

/**
 * SDK gates for later Android ASR / assistant APIs.
 *
 * API presence is not OEM presence: GrapheneOS Pixel 8 is API 35 and still
 * has no Google RecognitionService on a stock profile.
 */
data class VoicePlatformProfile(
    val sdk: Int,
    val onDeviceSpeechRecognizerApi: Boolean,
    val recognitionSupportApi: Boolean,
    val segmentedSessionApi: Boolean,
    val microphoneForegroundServiceType: Boolean,
    val restartListeningAfterFinal: Boolean,
    val roleAssistantApi: Boolean
) {
    companion object {
        const val NOTE9_SDK = 29
        const val ANDROID15_SDK = 35

        fun forSdk(sdk: Int): VoicePlatformProfile {
            require(sdk > 0) { "sdk must be positive" }
            return VoicePlatformProfile(
                sdk = sdk,
                onDeviceSpeechRecognizerApi = sdk >= 31,
                recognitionSupportApi = sdk >= 33,
                segmentedSessionApi = sdk >= 33,
                microphoneForegroundServiceType = sdk >= 31,
                restartListeningAfterFinal = sdk < 33,
                roleAssistantApi = sdk >= 29
            )
        }

        fun note9(): VoicePlatformProfile = forSdk(NOTE9_SDK)

        /** 9185W and GrapheneOS Pixel 8 UAT pool: Android 15. */
        fun android15(): VoicePlatformProfile = forSdk(ANDROID15_SDK)
    }
}
