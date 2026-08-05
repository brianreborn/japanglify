import java.io.File
import java.util.Properties

plugins {
    id("com.android.application") version "8.7.3" apply false
    id("org.jetbrains.kotlin.android") version "2.0.21" apply false
    id("org.jetbrains.kotlin.jvm") version "2.0.21" apply false
}

/**
 * FreeBSD build-host support: brand Linux ELF tools (aapt2, adb, …) so the
 * Linuxulator will run them. No-ops on non-FreeBSD hosts.
 */
val realOsForBrand: String =
    System.getProperty("japanglify.real.os")
        ?: System.getProperty("os.name").orEmpty()

val isFreeBsdHost: Boolean =
    realOsForBrand.equals("FreeBSD", ignoreCase = true)

fun resolveSdkRoot(rootDir: File): File? {
    val env = sequenceOf(
        System.getenv("ANDROID_SDK_ROOT"),
        System.getenv("ANDROID_HOME")
    ).firstOrNull { !it.isNullOrBlank() }
    if (env != null) return File(env)

    val localProps = File(rootDir, "local.properties")
    if (localProps.isFile) {
        val props = Properties()
        localProps.inputStream().use { props.load(it) }
        val dir = props.getProperty("sdk.dir")?.replace("\\", "/")
        if (!dir.isNullOrBlank()) return File(dir)
    }
    val bundled = File(rootDir, "sdk")
    return bundled.takeIf { it.isDirectory }
}

tasks.register<Exec>("brandLinuxElfs") {
    group = "build setup"
    description =
        "FreeBSD: brandelf -t Linux on Android SDK and Gradle-cached native tools"
    onlyIf { isFreeBsdHost }

    val script = rootDir.resolve("scripts/prepare-freebsd-build.sh")
    commandLine("bash", script.absolutePath)
    isIgnoreExitValue = false

    doFirst {
        if (!script.isFile) {
            throw GradleException("Missing $script")
        }
        script.setExecutable(true)
    }
}

// Wire FreeBSD branding ahead of the Android app build when :app is present.
gradle.projectsLoaded {
    val app = rootProject.findProject(":app") ?: return@projectsLoaded
    if (!isFreeBsdHost) return@projectsLoaded

    app.afterEvaluate {
        tasks.matching { it.name == "preBuild" }.configureEach {
            dependsOn(rootProject.tasks.named("brandLinuxElfs"))
        }
        tasks.matching {
            it.name in setOf(
                "processDebugResources",
                "processReleaseResources",
                "mergeDebugResources",
                "mergeReleaseResources"
            )
        }.configureEach {
            dependsOn(rootProject.tasks.named("brandLinuxElfs"))
        }
    }
}

tasks.register("staticAnalysis") {
    group = "verification"
    description = "Runs comprehensive static code analysis across all Japanglify modules."
    dependsOn(":domain:staticAnalysis", ":app:staticAnalysis")
    doLast {
        println("==================================================")
        println("  JAPANGLIFY BUILD TARGET: staticAnalysis PASSED  ")
        println("==================================================")
    }
}
