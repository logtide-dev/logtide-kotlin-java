package helpers

import org.gradle.api.Project
import org.gradle.api.provider.Property
import org.gradle.api.publish.maven.MavenPom

private infix fun <T> Property<T>.by(value: T) = set(value)

@Suppress("Unused")
fun MavenPom.configureMavenCentralMetadata(project: Project) {
    name by project.name
    description by "Official Kotlin SDK for LogTide - Self-hosted log management with batching, retry logic, circuit breaker, and query API"
    url by "https://github.com/logtide-dev/logtide-sdk-kotlin"

    licenses {
        license {
            name.set("MIT License")
            url.set("https://opensource.org/licenses/MIT")
        }
    }

    developers {
        developer {
            id by "polliog"
            name by "Polliog"
            email by "giuseppe@solture.it"
        }
        developer {
            id by "emanueleiannuzzi"
            name by "Emanuele Iannuzzi"
            email by "hello@emanueleiannuzzi.me"
        }
    }

    scm {
        connection by "scm:git:git://github.com/logtide-dev/logtide-sdk-kotlin.git"
        developerConnection by "scm:git:ssh://github.com/logtide-dev/logtide-sdk-kotlin.git"
        url by "https://github.com/logtide-dev/logtide-sdk-kotlin"
    }
}