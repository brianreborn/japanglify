package com.japanglify.app.debug

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import com.japanglify.app.JapanglifyApp
import com.japanglify.app.clipboard.ClipboardImageRenderer
import com.japanglify.app.domain.OutputFormat
import java.io.File
import java.io.FileOutputStream

/**
 * Debug-build-only seam for `scripts/acceptance-smoke-test.sh`
 * (`./gradlew acceptanceSmokeTest`). Exercises the real rendering pipeline —
 * [JapanglifyApp.engine], real Kuromoji, real Android `Canvas`/`Paint` via
 * [ClipboardImageRenderer] — directly, with no clipboard, no notification,
 * no accessibility service, and no third-party host app. That makes this
 * reliably scriptable against a freshly-booted, unattended emulator (the
 * eventual target), unlike the Copy-hook path this session's live
 * verification needed a real Discord Copy to reach.
 *
 * Never present in a release build: this class and its manifest entry live
 * under `app/src/debug/`, which Android Gradle Plugin only merges into the
 * debug variant.
 */
class AcceptanceTestActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val text = intent?.getStringExtra(EXTRA_TEXT) ?: DEFAULT_TEXT
        val app = application as JapanglifyApp
        val outDir = getExternalFilesDir(null)

        val result = runCatching {
            requireNotNull(outDir) { "getExternalFilesDir(null) returned null" }
            val settings = app.preferences.load().copy(outputFormat = OutputFormat.INTERLINEAR)
            val rows = app.engine.buildInterlinearRows(text, settings)
            val bitmap: Bitmap = ClipboardImageRenderer.renderInterlinearToBitmap(this, rows, settings)
            val pngFile = File(outDir, RESULT_PNG_NAME)
            FileOutputStream(pngFile).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            pngFile.absolutePath
        }

        val statusFile = outDir?.let { File(it, STATUS_FILE_NAME) }
        val statusText = result.fold(
            onSuccess = { path -> "OK $path" },
            onFailure = { err -> "ERROR ${err::class.simpleName}: ${err.message}" }
        )
        runCatching { statusFile?.writeText(statusText) }

        finish()
    }

    companion object {
        const val EXTRA_TEXT = "text"
        const val RESULT_PNG_NAME = "acceptance_result.png"
        const val STATUS_FILE_NAME = "acceptance_status.txt"

        // Matches RealDictionaryIntegrationTest's fixture — one source of
        // truth for "the proof sentence" between the JVM test suite and
        // this on-device acceptance check.
        private const val DEFAULT_TEXT = "すべての被造物に福音を語れは、多分マルコの一番最後の章だったと思う。"
    }
}
