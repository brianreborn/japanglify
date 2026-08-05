plugins {
    kotlin("jvm")
    application
}

// Compile with JDK 17+ host; emit JVM 17 bytecode so Android min desugar / AGP can consume it.
tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        allWarningsAsErrors.set(true)
        freeCompilerArgs.addAll("-opt-in=kotlin.RequiresOptIn")
    }
}

java {
    targetCompatibility = JavaVersion.VERSION_17
    sourceCompatibility = JavaVersion.VERSION_17
}

dependencies {
    implementation("com.atilika.kuromoji:kuromoji-ipadic:0.9.0")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit:2.0.21")
}

tasks.test {
    useJUnit()
}

application {
    mainClass.set("com.japanglify.app.domain.DemoMain")
}

tasks.register<JavaExec>("runDemo") {
    group = "application"
    description = "Run a short triple-script demo (no Android SDK)."
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("com.japanglify.app.domain.DemoMain")
    if (project.hasProperty("demoText")) {
        args(project.property("demoText"))
    }
}

val staticAnalysis by tasks.registering {
    group = "verification"
    description = "Runs static code analysis and lint hygiene checks on the domain module."
    dependsOn("compileKotlin", "compileTestKotlin")
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
                if (line.trim().startsWith("// TODO") || line.trim().startsWith("// FIXME")) {
                    logger.info("[STATIC ANALYSIS NOTE] Pending item in ${file.name}:$lineNo: ${line.trim()}")
                }
                if (line.contains("println(") && !file.name.endsWith("DemoMain.kt")) {
                    logger.warn("[STATIC ANALYSIS WARNING] Direct console stdout call in ${file.name}:$lineNo")
                    warningCount++
                }
            }
        }
        println("==> :domain staticAnalysis complete ($warningCount static warnings).")
    }
}
