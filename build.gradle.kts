plugins {
    `java-library`
    id("com.gradleup.shadow") version "9.3.2"
}

group = "com.wickidcow.galactifun"
version = "1.0.0"
description = "Galactifun Legacy"

val slimefunCoreJarPath = providers.gradleProperty("slimefunCoreJar")
    .orElse(providers.environmentVariable("SLIMEFUN_CORE_JAR"))
    .orElse(providers.gradleProperty("slimefunLegacyJar"))
    .orElse(providers.environmentVariable("SLIMEFUN_LEGACY_JAR"))
    .orElse(providers.environmentVariable("SLIMEFUN_COMPATIBILITY_JAR"))
    .orElse(layout.projectDirectory.file("lib/Slimefun-Legacy.jar").asFile.absolutePath)
val slimefunCoreJar = file(slimefunCoreJarPath.get())

if (!slimefunCoreJar.isFile) {
    throw GradleException(
        "Slimefun Legacy JAR not found at '${slimefunCoreJar.absolutePath}'. " +
            "Pass -PslimefunCoreJar=/path/to/Slimefun-Legacy.jar or set SLIMEFUN_CORE_JAR."
    )
}

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://jitpack.io")
    maven("https://repo.codemc.org/repository/maven-public")
}

configurations.configureEach {
    exclude(group = "com.github.SlimefunGuguProject", module = "Slimefun4")
    exclude(group = "com.github.slimefun", module = "Slimefun4")
    exclude(group = "io.github.thebusybiscuit", module = "Slimefun4")
}

dependencies {
    implementation("org.apache.commons:commons-lang3:3.18.0")
    implementation("commons-codec:commons-codec:1.19.0")

    // Paper 26.2 / Minecraft 1.21.11 is the primary runtime target.
    compileOnly("io.papermc.paper:paper-api:26.2.build.+")

    // Compile against the exact Slimefun Legacy JAR supplied by CI or the developer.
    compileOnly(files(slimefunCoreJar))
    compileOnly("com.google.code.findbugs:jsr305:3.0.2")
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(25))
    }
    sourceCompatibility = JavaVersion.VERSION_25
    targetCompatibility = JavaVersion.VERSION_25
    withSourcesJar()
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release.set(25)
    options.compilerArgs.add("-Xlint:-removal")
}

tasks.processResources {
    val props = mapOf("version" to project.version)
    inputs.properties(props)
    filesMatching("plugin.yml") {
        expand(props)
    }
}

tasks.shadowJar {
    archiveClassifier.set("")
    archiveFileName.set("SF_Galactifun1.0.0.jar")
    relocate("io.github.mooy1.infinitylib", "io.github.addoncommunity.galactifun.infinitylib")
    relocate("org.apache.commons.lang3", "io.github.addoncommunity.galactifun.commons.lang3")
    relocate("org.apache.commons.codec", "io.github.addoncommunity.galactifun.commons.codec")
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}

tasks.jar {
    enabled = false
}

tasks.build {
    dependsOn(tasks.shadowJar)
}

defaultTasks("clean", "build")
