import org.gradle.api.attributes.java.TargetJvmVersion

plugins {
    `java-library`
    id("com.gradleup.shadow") version "9.6.1"
    id("xyz.jpenilla.run-paper") version "3.0.2"
}

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://api.modrinth.com/maven")
    maven("https://jitpack.io")
    maven("https://repo.codemc.org/repository/maven-public")
}

group = "io.github.addoncommunity.galactifun"
version = "1.0.3"
description = "Galactifun Legacy - space exploration and planetary gameplay for Slimefun Legacy"

val slimefunCoreJar = providers.gradleProperty("slimefunCoreJar").orNull

dependencies {
    implementation("org.apache.commons:commons-lang3:3.17.0")
    implementation("commons-codec:commons-codec:1.17.1")

    compileOnly("io.papermc.paper:paper-api:26.2.build.+")
    if (slimefunCoreJar != null) {
        compileOnly(files(slimefunCoreJar))
    } else {
        // Developer fallback. CI and release builds pass the exact Slimefun Legacy JAR.
        compileOnly("maven.modrinth:slimefuncore:PEuZoZh4")
    }
    compileOnly("com.google.code.findbugs:jsr305:3.0.2")
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(25))
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

// Paper 26.2 publishes Java 25 API classes. The build JVM can consume those classes while
// Galactifun itself remains deliberately compiled to Java 21 bytecode for Legacy compatibility.
configurations.configureEach {
    if (isCanBeResolved) {
        attributes.attribute(TargetJvmVersion.TARGET_JVM_VERSION_ATTRIBUTE, 25)
    }
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
    options.release.set(21)
    // Keep the Legacy compatibility boundaries explicit and prevent deprecated APIs from
    // silently creeping back into normal Galactifun code.
    options.compilerArgs.addAll(listOf(
        "-Xlint:deprecation",
        "-Xlint:removal",
        "-Xlint:unchecked",
        "-Werror"
    ))
}

tasks.processResources {
    val props = mapOf(
        "version" to project.version,
        "project" to mapOf("version" to project.version)
    )
    inputs.properties(props)
    filesMatching("plugin.yml") {
        expand(props)
    }
}

tasks.shadowJar {
    archiveClassifier.set("")
    archiveFileName.set("SF_Glactifun1.0.3.jar")
    relocate("io.github.mooy1.infinitylib", "io.github.addoncommunity.galactifun.infinitylib")
    relocate("org.apache.commons.lang3", "io.github.addoncommunity.galactifun.commons.lang3")
    relocate("org.apache.commons.codec", "io.github.addoncommunity.galactifun.commons.codec")
}

tasks.build {
    dependsOn(tasks.shadowJar)
}

tasks.runServer {
    minecraftVersion("26.2")
    pluginJars(tasks.shadowJar.flatMap { it.archiveFile })
}
