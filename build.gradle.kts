val baseVersion = providers.gradleProperty("version.base").get()
val betaVersion = providers.gradleProperty("version.beta").get().toBooleanStrict()
val buildNumber = providers.environmentVariable("BUILD_NUMBER").orNull?.takeIf { it.isNotBlank() }
val releaseVersion = if (betaVersion) "$baseVersion.Beta" else baseVersion
val buildSeparator = if (betaVersion) "-" else "."
rootProject.version = buildNumber?.let { "$releaseVersion$buildSeparator$it" } ?: releaseVersion

plugins {
    id("java")
}

val paperVersion = providers.gradleProperty("version.paper").get()
val paperProjects = setOf(":plugin", ":expansion:scoreboard", ":expansion:cheat-prevention:paper")

subprojects {
    val usesPaper = path in paperProjects
    apply {
        plugin("java")
    }

    java {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
        if (usesPaper) {
            disableAutoTargetJvm()
        }
        toolchain.languageVersion.set(JavaLanguageVersion.of(25))
    }

    repositories {
        mavenCentral()
        if (usesPaper) {
            maven("https://repo.papermc.io/repository/maven-public/")
        }
        maven("https://repo.aikar.co/nexus/content/groups/aikar/") // Aikar Repository
        maven("https://nexus.sirblobman.xyz/public/") // SirBlobman Public Repository
    }

    dependencies {
        compileOnly("org.jetbrains:annotations:26.1.0") // JetBrains Annotations
        compileOnly("com.github.sirblobman.api:core:2.9-SNAPSHOT") {
            isTransitive = false
        }

        if (usesPaper) {
            compileOnly("io.papermc.paper:paper-api:$paperVersion")
        } else {
            compileOnly("org.spigotmc:spigot-api")
            constraints {
                compileOnly("org.spigotmc:spigot-api") {
                    version {
                        prefer("1.19.4-R0.1-SNAPSHOT")
                    }
                }
            }
        }
    }

    tasks.withType<JavaCompile> {
        options.encoding = "UTF-8"
        options.compilerArgs.add("-Xlint:deprecation")
    }

    tasks.withType<AbstractArchiveTask>().configureEach {
        isReproducibleFileOrder = true
        isPreserveFileTimestamps = false
    }
}
