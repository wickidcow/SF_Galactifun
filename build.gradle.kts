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
version = providers.gradleProperty("projectVersion").orElse("1.0.7").get()
description = "Galactifun Legacy - space exploration and planetary gameplay for Slimefun Legacy"

val slimefunCoreJar = providers.gradleProperty("slimefunCoreJar").orNull
val paperApiVersion = providers.gradleProperty("paperVersion").orElse("1.21.11-R0.1-SNAPSHOT")

dependencies {
    implementation("org.apache.commons:commons-lang3:3.17.0")
    implementation("commons-codec:commons-codec:1.17.1")

    // Compile the release JAR against the oldest supported Paper API. CI overrides this
    // property for 26.2 and 26.3 compatibility compilation without raising the runtime floor.
    compileOnly("io.papermc.paper:paper-api:${paperApiVersion.get()}")
    if (slimefunCoreJar != null) {
        compileOnly(files(slimefunCoreJar))
    } else {
        // Developer fallback. CI and release builds pass the exact Slimefun Legacy JAR.
        compileOnly("maven.modrinth:slimefuncore:PEuZoZh4")
    }
    compileOnly("com.google.code.findbugs:jsr305:3.0.2")

    testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:1.10.2")
    testImplementation("io.papermc.paper:paper-api:${paperApiVersion.get()}")
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(25))
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
    options.release.set(21)
    options.compilerArgs.addAll(listOf(
        "-Xlint:deprecation",
        "-Xlint:removal",
        "-Xlint:unchecked",
        "-Werror"
    ))
}

tasks.test {
    useJUnitPlatform()
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
    archiveFileName.set("SF_Galactifun${project.version}.jar")
    relocate("io.github.mooy1.infinitylib", "io.github.addoncommunity.galactifun.infinitylib")
    relocate("org.apache.commons.lang3", "io.github.addoncommunity.galactifun.commons.lang3")
    relocate("org.apache.commons.codec", "io.github.addoncommunity.galactifun.commons.codec")
}

tasks.build {
    dependsOn(tasks.shadowJar)
}

tasks.runServer {
    // Paper 26.2 stays the production runtime baseline while 26.3 is alpha.
    minecraftVersion("26.2")
    pluginJars(tasks.shadowJar.flatMap { it.archiveFile })
}
