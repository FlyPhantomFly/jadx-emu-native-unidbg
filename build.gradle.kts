import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    kotlin("jvm") version "2.3.10"
    id("com.gradleup.shadow") version "9.4.2"
    application
}

version = System.getenv("VERSION") ?: "0.1.1"

// Built against io.github.flyphantomfly:jadx-emu, resolved from ~/.m2 (publish it with the engine's own build).
val emuVersion = System.getenv("EMU_VERSION") ?: "0.3.0"

// unidbg 0.9.8 falls back to its built-in Unicorn CPU. 0.9.9 needs the unicorn2 module as well:
// build with UNIDBG_VERSION=0.9.9 UNIDBG_UNICORN2=true.
val unidbgVersion = System.getenv("UNIDBG_VERSION") ?: "0.9.8"

repositories {
    mavenLocal()
    mavenCentral()
    google()
}

dependencies {
    // provided by jadx and jadx-emu when the extension runs
    compileOnly("io.github.flyphantomfly:jadx-emu:$emuVersion")
    compileOnly("io.github.skylot:jadx-core:1.5.6")
    compileOnly("org.slf4j:slf4j-api:2.0.17")

    // bundled into the extension jar: jadx does not have unidbg on its classpath
    implementation("com.github.zhkl0228:unidbg-android:$unidbgVersion")
    if (System.getenv("UNIDBG_UNICORN2") == "true") {
        implementation("com.github.zhkl0228:unidbg-unicorn2:$unidbgVersion")
    }

    testImplementation(platform("org.junit:junit-bom:5.11.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("io.github.flyphantomfly:jadx-emu:$emuVersion")
    testImplementation("io.github.skylot:jadx-core:1.5.6")
    testImplementation("io.github.skylot:jadx-dex-input:1.5.6")
    testImplementation("com.android.tools.smali:smali:3.0.9")
    testImplementation("org.slf4j:slf4j-api:2.0.17")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testRuntimeOnly("org.slf4j:slf4j-simple:2.0.17")
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_21
    }
}

application {
    mainClass = "jadx.emu.ext.unidbg.StandaloneKt"
}

tasks.test {
    useJUnitPlatform()
}

tasks.jar {
    archiveClassifier = "thin"
}

// the extension jar: our classes plus unidbg and its dependencies
tasks.shadowJar {
    archiveBaseName = "jadx-emu-native-unidbg"
    archiveClassifier = ""
    mergeServiceFiles()
}

tasks.build {
    dependsOn(tasks.shadowJar)
}
