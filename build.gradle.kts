import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import org.gradle.jvm.toolchain.JavaLanguageVersion
import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

plugins {
    kotlin("jvm") version "1.9.22"
    kotlin("kapt") version "1.9.22"
    application
    idea
    id("com.github.johnrengelman.shadow") version "7.1.2"
}

group = "org.example"
version = "1.0-SNAPSHOT"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
}

repositories {
    mavenCentral()
    maven("https://maven.lavalink.dev/snapshots")
    maven("https://maven.lavalink.dev/releases")
    maven("https://jitpack.io")
}

dependencies {
    val slf4jVersion = "2.0.5"
    val logbackVersion = "1.4.7"
    val daggerVersion = "2.48"

    implementation(kotlin("stdlib"))
    implementation("com.discord4j:discord4j-core:3.2.8")
    implementation("dev.arbjerg:lavaplayer:2.2.3")
    implementation("dev.lavalink.youtube:v2:1.13.2")

    implementation("org.slf4j:slf4j-api:$slf4jVersion")
    implementation("ch.qos.logback:logback-classic:$logbackVersion")

    implementation("com.google.code.gson:gson:2.10.1")

    implementation("com.google.dagger:dagger:$daggerVersion")
    kapt("com.google.dagger:dagger-compiler:$daggerVersion")

    implementation("org.xerial:sqlite-jdbc:3.40.1.0")

    testImplementation(kotlin("test"))
    testImplementation("io.mockk:mockk:1.13.8")
}

kapt {
    includeCompileClasspath = false
}

tasks.withType<KotlinCompile> {
    kotlinOptions {
        jvmTarget = "17"
        freeCompilerArgs = listOf(
            "-Xjsr305=strict",
            "-opt-in=kotlin.RequiresOptIn"
        )
    }
}

application {
    mainClass.set("MainKt")
}

tasks.named<com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar>("shadowJar") {
    mergeServiceFiles()
    archiveClassifier.set("")
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    manifest {
        attributes("Main-Class" to "MainKt")
    }
}

tasks.named("build") {
    dependsOn("shadowJar")
}

idea {
    module {
        sourceDirs.plusAssign(file("$buildDir/generated/source/kapt/main"))
        generatedSourceDirs.plusAssign(file("$buildDir/generated/source/kapt/main"))
    }
}

tasks.test {
    useJUnitPlatform()
}
