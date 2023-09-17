import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm") version "1.7.10"
    application
}

group = "org.example"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()

    maven {
        setUrl("https://jitpack.io")
    }
}

dependencies {
    testImplementation(kotlin("test"))
    implementation("com.discord4j:discord4j-core:3.2.5")
    implementation("dev.arbjerg:lavaplayer:2.0.1")
    implementation("org.slf4j:slf4j-api:2.0.5")
    implementation("ch.qos.logback:logback-classic:1.4.7")
    implementation("com.google.code.gson:gson:2.8.8")
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
            "Main-Class" to "org.example.MainKt"
        )
    }

    duplicatesStrategy = DuplicatesStrategy.EXCLUDE

    from(configurations.runtimeClasspath.get().filter { it.isDirectory || it.name.endsWith("jar") }.flatMap { if (it.isDirectory) listOf(it) else listOf(zipTree(it)) })
}



