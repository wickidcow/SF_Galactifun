plugins {
    `java-library`
    id("com.gradleup.shadow") version "8.3.6"
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
version = "1.0.1"
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

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
    options.release.set(21)
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
    archiveFileName.set("SF_Galactifun1.0.1.jar")
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
