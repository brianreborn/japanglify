// Kotlin Gradle Plugin / AGP only enumerate Linux|macOS|Windows.
// FreeBSD is a supported *build host* via Linuxulator + Linux SDK bits;
// report os.name as Linux so AGP selects Linux aapt2 / build-tools.
val realOs = System.getProperty("os.name").orEmpty()
val isBsdHost = realOs.equals("FreeBSD", ignoreCase = true) ||
    realOs.equals("OpenBSD", ignoreCase = true) ||
    realOs.equals("NetBSD", ignoreCase = true)
if (isBsdHost) {
    System.setProperty("os.name", "Linux")
    System.setProperty("japanglify.real.os", realOs)
    // Also help some Google tooling that reads these:
    System.setProperty("japanglify.freebsd", "true")
}

pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "Japanglify"

include(":domain")

// Android :app — included by default on FreeBSD (Linuxulator path) as well as
// Linux/macOS/Windows. Opt out: -PincludeApp=false or INCLUDE_ANDROID_APP=0.
val hostOs = (System.getProperty("japanglify.real.os")
    ?: System.getProperty("os.name").orEmpty()).lowercase()
val prop = providers.gradleProperty("includeApp").orNull
val env = providers.environmentVariable("INCLUDE_ANDROID_APP").orNull
val includeApp = when {
    prop != null -> prop.equals("true", ignoreCase = true) || prop == "1"
    env != null -> !(env == "0" || env.equals("false", ignoreCase = true))
    // Open/NetBSD: no first-class support yet (FreeBSD only among BSDs)
    hostOs.contains("openbsd") || hostOs.contains("netbsd") -> false
    else -> true
}

if (includeApp) {
    include(":app")
    if (isBsdHost) {
        println("NOTE: FreeBSD host — AGP sees Linux; SDK natives need brandelf (auto via :brandLinuxElfs).")
    }
} else {
    println("NOTE: Skipping :app (Android). Domain-only build. Use -PincludeApp=true to force.")
}
