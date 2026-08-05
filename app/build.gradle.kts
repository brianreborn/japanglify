plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
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

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
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
