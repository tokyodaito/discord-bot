import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm") version "1.7.10"
    kotlin("kapt") version "1.7.10"
    id("idea")
    application
}


group = "org.example"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
    maven("https://maven.lavalink.dev/snapshots")

    maven {
        setUrl("https://jitpack.io")
    }
}

idea {
    module {
        sourceDirs.plusAssign(file("$projectDir/build/generated/source/kapt/main"))
        generatedSourceDirs.plusAssign(file("$projectDir/build/generated/source/kapt/main"))
    }
}

sourceSets {
    main {
        java {
            srcDir("$buildDir/generated/source/kapt/main")
        }
    }
}

kapt {
    includeCompileClasspath = false
}


dependencies {
    testImplementation(kotlin("test"))
    implementation("com.discord4j:discord4j-core:3.2.5")
    implementation("dev.arbjerg:lavaplayer:0eaeee195f0315b2617587aa3537fa202df07ddc-SNAPSHOT")

    // Log's
    implementation("org.slf4j:slf4j-api:2.0.5")
    implementation("ch.qos.logback:logback-classic:1.4.7")

    // Gson
    implementation("com.google.code.gson:gson:2.10.1")

    // Dagger2
    implementation("com.google.dagger:dagger:2.48")
    kapt("com.google.dagger:dagger-compiler:2.48")

    // JDBC
    implementation("org.xerial:sqlite-jdbc:3.40.1.0")
}


tasks.test {
    useJUnitPlatform()
}

tasks.withType<KotlinCompile> {
    kotlinOptions.jvmTarget = "1.8"
}

application {
    mainClass.set("MainKt")
}

tasks.withType<Jar> {
    manifest {
        attributes(
            "Main-Class" to "MainKt"
        )
    }

    duplicatesStrategy = DuplicatesStrategy.EXCLUDE

    from(configurations.runtimeClasspath.get().filter { it.isDirectory || it.name.endsWith("jar") }
        .flatMap { if (it.isDirectory) listOf(it) else listOf(zipTree(it)) })
}



