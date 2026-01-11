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

rootProject.name = "logtide-sdk-kotlin"