plugins {
    kotlin("jvm")
    application
}

// Compile with whatever JDK runs Gradle (21+ recommended); emit JVM 17
// bytecode so the Android app (min desugar / AGP) can consume this module.
tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
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
