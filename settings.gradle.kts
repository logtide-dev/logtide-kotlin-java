rootProject.name = "logtide-kotlin-java"

@Suppress("UnstableApiUsage")
dependencyResolutionManagement {
    versionCatalogs {
        create("frameworks") {
            from(files("gradle/frameworks.versions.toml"))
        }
    }
    repositories {
        mavenLocal()
        mavenCentral()
        maven { url = uri("https://packages.confluent.io/maven/") }
    }
}

pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
    resolutionStrategy {
        eachPlugin {
            if (requested.id.id == "org.sonatype.central") {
                useModule("org.sonatype.central:org.sonatype.central.gradle.plugin:1.2.0")
            }
        }
    }
}

include("logtide-core")
include("logtide-spring")
include("logtide-ktor")
include("logtide-jakarta")