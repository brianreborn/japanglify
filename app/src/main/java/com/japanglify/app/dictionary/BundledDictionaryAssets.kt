package com.japanglify.app.dictionary

import android.content.Context
import org.tukaani.xz.XZInputStream
import java.io.File

/**
 * Reads a dictionary source file out of `assets/dictionaries/` (present
 * only in the "bundled" product flavor -- see `app/build.gradle.kts` --
 * where it's the same real upstream file "downloadable" fetches over the
 * network) into the same cache location the network path would have used.
 * Everything downstream (XML/text parsing, SQLite import, atomic rename)
 * is identical either way; this is the only thing that differs.
 *
 * Assets are stored LZMA2-compressed (`.xz`), not as the plain source file
 * and not re-zipped. Tried `com.github.luben:zstd-jni` first, but its
 * published artifact turned out to only bundle desktop natives
 * (darwin/win/linux paths meant for JVM-desktop temp-dir extraction, not
 * Android's `lib/<abi>/libfoo.so` convention) -- confirmed live by
 * inspecting the actual built APK, no `.so` under `lib/` at all.
 * `org.tukaani:xz` is pure Java (no native/JNI risk whatsoever) and, once
 * LZMA2's literal-context parameters were tuned for this data
 * (`lc=4,lp=0,pb=0` -- `pb=0` because none of JSON/XML/Prolog-text has
 * positional byte alignment to exploit, `lc=4` empirically beat the
 * default `lc=3` on real samples of this repo's actual mixed
 * ASCII/Japanese-UTF-8/emoji dictionary content; see NOTES.md for the
 * sweep), it actually beat zstd -19 on real data anyway: JMdict's 117 MB
 * raw JSON -> 7.47 MB vs zstd -19's 7.79 MB.
 */
object BundledDictionaryAssets {
    fun decompressToCache(context: Context, assetFileName: String, destFileName: String): File {
        val cacheDir = File(context.cacheDir, "dictionaries").apply { mkdirs() }
        val destFile = File(cacheDir, destFileName)
        context.assets.open("dictionaries/$assetFileName").use { raw ->
            XZInputStream(raw).use { decompressed ->
                destFile.outputStream().use { output -> decompressed.copyTo(output) }
            }
        }
        return destFile
    }
}
