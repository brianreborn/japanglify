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
        dependsOn("installDebug")
    }

    doFirst {
        outDir.get().asFile.mkdirs()
    }
    commandLine("bash", "$rootDir/scripts/acceptance-smoke-test.sh", outDir.get().asFile.path, serial)
}
