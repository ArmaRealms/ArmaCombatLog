plugins {
    id("distribution")
}

val coreJar = configurations.create("coreJar") {
    isCanBeConsumed = false
    isTransitive = false
    attributes {
        attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage.JAVA_RUNTIME))
    }
}
val pluginJar = configurations.create("pluginJar")
val expansion = configurations.create("expansion")

dependencies {
    // BlueSlimeCore
    coreJar("com.github.sirblobman.api:core:2.9-SNAPSHOT")

    // CombatLogX
    pluginJar(project(path = ":plugin", configuration = "shadow"))

    // Expansions selected for ArmaRealms Config-Plugins
    expansion(project(":expansion:action-bar"))
    expansion(project(path = ":expansion:cheat-prevention", configuration = "shadow"))
    expansion(project(path = ":expansion:end-crystal", configuration = "shadow"))
    expansion(project(":expansion:newbie-helper"))
    expansion(project(":expansion:compatibility:EssentialsX"))
    expansion(project(":expansion:compatibility:MarriageMaster"))
}

distributions {
    main {
        contents {
            into("/") {
                // README.TXT
                from(sourceSets.getByName("main").allSource)

                from(pluginJar)
                from(coreJar)
            }

            into("/CombatLogX/expansions/") {
                from(expansion)
            }
        }
    }
}

tasks {
    named<Jar>("jar") {
        enabled = false
    }

    named<Tar>("distTar") {
        enabled = false
    }

    named<Zip>("distZip") {
        isPreserveFileTimestamps = true
        archiveBaseName.set("CombatLogX-${rootProject.version}")
    }
}
