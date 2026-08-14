import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// Release signing — local, git-ignored keystore.properties (see
// keystore.properties.example for the format / how to generate one).
// Absent on a machine ⇒ :app:assembleRelease still succeeds, just produces
// an unsigned APK — same graceful-degradation style as the FreeBSD /
// includeApp toggles used elsewhere in this build.
val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties().apply {
    if (keystorePropertiesFile.exists()) {
        keystorePropertiesFile.inputStream().use { load(it) }
    }
}

android {
    namespace = "com.japanglify.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.japanglify.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // Two distributions of the same app, differing only in how the
    // optional dictionaries (see com.japanglify.app.dictionary) get onto
    // the device:
    //  - "downloadable" (default): current behavior, fetched over the
    //    network into DictionaryDownloadService on demand.
    //  - "bundled": the same source files (JMdict zip, CLDR XML, WordNet
    //    text) ship as APK assets under src/bundled/assets/dictionaries/,
    //    so DictionaryDownloadManager/EmojiDownloadManager/
    //    WordNetDownloadManager copy from assets instead of opening a
    //    socket -- everything downstream of that (parsing, SQLite import,
    //    atomic swap) is exactly the same code path either way, so this is
    //    a small branch at the top of each acquisition step, not a
    //    parallel implementation. Adds ~21 MB to the APK (11 MB JMdict +
    //    0.3 MB CLDR + 9.6 MB WordNet, all verified-live real source files,
    //    same ones "downloadable" fetches from the same upstream URLs) in
    //    exchange for those dictionaries working with zero network
    //    dependency and no "hangs forever" failure mode at all.
    flavorDimensions += "distribution"
    productFlavors {
        create("downloadable") {
            dimension = "distribution"
            buildConfigField("boolean", "DICTIONARIES_BUNDLED", "false")
        }
        create("bundled") {
            dimension = "distribution"
            applicationIdSuffix = ".bundled"
            versionNameSuffix = "-bundled"
            buildConfigField("boolean", "DICTIONARIES_BUNDLED", "true")
        }
    }

    signingConfigs {
        if (keystorePropertiesFile.exists()) {
            create("release") {
                storeFile = rootProject.file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            if (keystorePropertiesFile.exists()) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
        debug {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        viewBinding = false
        buildConfig = true
    }

    // The bundled flavor's dictionary assets are already LZMA2 (.xz)
    // compressed (see BundledDictionaryAssets) -- letting aapt2
    // additionally deflate-compress an already-high-entropy .xz stream
    // wastes build time for no size benefit (and occasionally grows it
    // slightly from container overhead), so it's excluded the same way
    // apps normally exclude already-compressed media (.mp3/.jpg/etc, which
    // AGP excludes by default -- .xz just isn't in that default list).
    androidResources {
        noCompress += "xz"
    }

    packaging {
        resources {
            // kuromoji-core and kuromoji-ipadic both ship duplicate META-INF docs
            excludes += setOf(
                "META-INF/CONTRIBUTORS.md",
                "META-INF/LICENSE",
                "META-INF/LICENSE.md",
                "META-INF/LICENSE.txt",
                "META-INF/NOTICE",
                "META-INF/NOTICE.md",
                "META-INF/NOTICE.txt",
                "META-INF/DEPENDENCIES",
                "META-INF/CHANGES.md",
                "META-INF/README.md",
                "META-INF/*.SF",
                "META-INF/*.DSA",
                "META-INF/*.RSA"
            )
        }
    }
}

// FreeBSD: brand Linux SDK/Gradle natives before any AGP task that may exec them.
val realOs = System.getProperty("japanglify.real.os")
    ?: System.getProperty("os.name").orEmpty()
if (realOs.equals("FreeBSD", ignoreCase = true)) {
    tasks.configureEach {
        if (name == "preBuild" || name.startsWith("process") && name.endsWith("Resources")) {
            dependsOn(rootProject.tasks.named("brandLinuxElfs"))
        }
    }
}

dependencies {
    implementation(project(":domain"))

    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.constraintlayout:constraintlayout:2.2.0")
    implementation("androidx.preference:preference-ktx:1.2.1")
    implementation("androidx.activity:activity-ktx:1.9.3")

    // Offline morphological analysis for kanji readings (furigana source)
    implementation("com.atilika.kuromoji:kuromoji-ipadic:0.9.0")

    // Decompresses the "bundled" flavor's pre-compressed dictionary assets
    // (see BundledDictionaryAssets) -- needed by both flavors' code paths
    // since DictionaryDownloadManager/EmojiDownloadManager/
    // WordNetDownloadManager are flavor-agnostic classes that branch on
    // BuildConfig.DICTIONARIES_BUNDLED at runtime, not separate per-flavor
    // source sets.
    //
    // Pure-Java LZMA2 (XZ format), not com.github.luben:zstd-jni -- tried
    // zstd-jni first and rejected it after inspecting the actual built APK
    // live: its published artifact only bundles desktop natives
    // (darwin/*.dylib, win/*.dll, linux/*.so meant for JVM-desktop temp-dir
    // extraction), not Android's `lib/<abi>/*.so` jniLibs convention -- it
    // would have shipped completely non-functional native libs and crashed
    // on first use on a real device. org.tukaani:xz has none of that risk
    // (plain JAR, no JNI), and empirically beat zstd anyway once LZMA2's
    // literal-context params were tuned for this data (see the asset-prep
    // note in BundledDictionaryAssets / NOTES.md): 7.47 MB vs zstd -19's
    // 7.79 MB for JMdict's JSON.
    implementation("org.tukaani:xz:1.12")
}

val staticAnalysis by tasks.registering {
    group = "verification"
    description = "Runs static code analysis and lint hygiene checks on the Android app module."
    doLast {
        var warningCount = 0
        fileTree("src").matching { include("**/*.kt") }.forEach { file ->
            val lines = file.readLines()
            lines.forEachIndexed { index, line ->
                val lineNo = index + 1
                if (line.contains("import ") && line.contains(".*")) {
                    logger.warn("[STATIC ANALYSIS WARNING] Wildcard import in ${file.name}:$lineNo")
                    warningCount++
                }
                if (line.contains("printStackTrace()")) {
                    logger.warn("[STATIC ANALYSIS WARNING] printStackTrace call in ${file.name}:$lineNo")
                    warningCount++
                }
            }
        }
        println("==> :app staticAnalysis complete ($warningCount static warnings).")
    }
}

// adb lookup mirrors scripts/acceptance-smoke-test.sh's own fallback chain,
// so "is a device connected?" agrees between Gradle's configuration-time
// check (below) and the script's runtime check.
fun findAdbForGradle(): String? {
    val candidates = listOfNotNull(
        System.getenv("ANDROID_SDK_ROOT")?.let { "$it/platform-tools/adb" },
        System.getenv("ANDROID_HOME")?.let { "$it/platform-tools/adb" },
        "$rootDir/sdk/platform-tools/adb"
    )
    candidates.firstOrNull { file(it).canExecute() }?.let { return it }
    return runCatching {
        val proc = ProcessBuilder("which", "adb").start()
        proc.waitFor()
        proc.inputStream.bufferedReader().readText().trim().takeIf { it.isNotEmpty() }
    }.getOrNull()
}

// Drives scripts/acceptance-smoke-test.sh: runs the domain suite and (if a
// device is connected right now) installs the debug build and exercises the
// real rendering pipeline via the debug-only AcceptanceTestActivity,
// producing a Markdown report with embedded screenshots at
// build/reports/acceptance/. installDebug is only pulled in as a dependency
// when a device is actually present — same graceful-degradation style as
// the keystoreProperties handling above — so this also runs headlessly
// (domain summary + "no device" note) ahead of the eventual locally-spun-up
// Android VM target, without installDebug hard-failing the whole task.
val acceptanceSmokeTest by tasks.registering(Exec::class) {
    group = "verification"
    description = "Runs the acceptance smoke test and writes a Markdown report with screenshots."
    dependsOn(":domain:test")

    val outDir = layout.buildDirectory.dir("reports/acceptance")
    val serial = (project.findProperty("deviceSerial") as String?) ?: ""
    val adbPath = findAdbForGradle()
    val deviceConnected = adbPath != null && runCatching {
        val args = mutableListOf(adbPath)
        if (serial.isNotEmpty()) {
            args += "-s"
            args += serial
        }
        args += "get-state"
        ProcessBuilder(args).redirectErrorStream(true).start().waitFor() == 0
    }.getOrDefault(false)

    if (deviceConnected) {
        // The "downloadable" flavor specifically -- its applicationId
        // matches PKG="com.japanglify.app" below unmodified, unlike
        // "bundled" (applicationIdSuffix ".bundled"), which this script's
        // hardcoded package name wouldn't find.
        dependsOn("installDownloadableDebug")
    }

    doFirst {
        outDir.get().asFile.mkdirs()
    }
    commandLine("bash", "$rootDir/scripts/acceptance-smoke-test.sh", outDir.get().asFile.path, serial)
}

// Publishes both APKs as assets on a GitHub Release via the `gh` CLI.
// `gh` must already be installed and authenticated (`gh auth login`) on the
// machine running this -- this task is a thin wrapper over it, not a
// credentials manager, same posture as keystore.properties/adb elsewhere in
// this build: absence degrades to a clear failure message, not a silent
// no-op, since "publish" has no sensible degraded behavior the way an
// unsigned release build does.
//
// Defaults the release tag to "v<versionName>" (e.g. "v1.0.0"); override
// with -PreleaseTag=v1.0.0-beta to update an existing tagged release (like
// this project's actual first public release, tagged "v1.0.0-beta" by
// hand) instead of creating a new one every run.
val publishApks by tasks.registering {
    group = "distribution"
    description = "Builds and publishes all flavor/build-type APKs (downloadable + bundled, debug + release) as assets on a GitHub Release."
    dependsOn(
        "assembleDownloadableDebug", "assembleDownloadableRelease",
        "assembleBundledDebug", "assembleBundledRelease"
    )

    doLast {
        val tag = (project.findProperty("releaseTag") as String?)
            ?: "v${android.defaultConfig.versionName}"
        val apks = listOf(
            "outputs/apk/downloadable/debug/app-downloadable-debug.apk",
            "outputs/apk/downloadable/release/app-downloadable-release.apk",
            "outputs/apk/bundled/debug/app-bundled-debug.apk",
            "outputs/apk/bundled/release/app-bundled-release.apk"
        ).map { layout.buildDirectory.file(it).get().asFile }
        apks.forEach {
            require(it.exists()) { "Expected APK not found: ${it.path}" }
        }

        fun run(vararg args: String): Int {
            val proc = ProcessBuilder(*args).redirectErrorStream(true).start()
            proc.inputStream.bufferedReader().forEachLine { println(it) }
            return proc.waitFor()
        }

        val ghAvailable = runCatching {
            ProcessBuilder("which", "gh").start().waitFor() == 0
        }.getOrDefault(false)
        require(ghAvailable) {
            "gh CLI not found on PATH -- install it and run `gh auth login` first."
        }

        val releaseExists = run("gh", "release", "view", tag) == 0
        if (!releaseExists) {
            val created = run(
                "gh", "release", "create", tag,
                "--title", "Japanglify $tag",
                "--notes", "Published via :app:publishApks."
            )
            require(created == 0) { "gh release create failed for tag $tag" }
        }

        val uploaded = run(
            "gh", "release", "upload", tag,
            *apks.map { it.path }.toTypedArray(),
            "--clobber"
        )
        require(uploaded == 0) { "gh release upload failed for tag $tag" }
        println("==> Published downloadable + bundled (debug + release) APKs to GitHub Release $tag")
    }
}
