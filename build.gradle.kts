import com.vanniktech.maven.publish.MavenPublishBaseExtension
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

plugins {
    kotlin("jvm") version "1.9.21"
    kotlin("plugin.serialization") version "1.9.21"
    id("org.jetbrains.dokka") version "1.9.10"
    id("com.vanniktech.maven.publish") version "0.29.0"
}

group = "io.github.logtide-dev"
version = "0.4.0"

repositories {
    mavenCentral()
}

dependencies {
    // Kotlin
    implementation(kotlin("stdlib"))

    // HTTP Client
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:okhttp-sse:4.12.0")

    // JSON Serialization
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.0")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")

    // Logging
    compileOnly("org.slf4j:slf4j-api:2.0.9")

    // Framework integrations
    compileOnly("org.springframework.boot:spring-boot-starter-web:3.2.0")
    compileOnly("io.ktor:ktor-server-core:2.3.7")
    compileOnly("jakarta.servlet:jakarta.servlet-api:6.0.0")

    // Testing
    testImplementation(kotlin("test"))
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.7.3")
    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
    testImplementation("io.mockk:mockk:1.13.8")

    // Logging for tests (required since slf4j-api is compileOnly)
    testImplementation("org.slf4j:slf4j-api:2.0.9")
    testImplementation("org.slf4j:slf4j-simple:2.0.9")

    // Framework testing dependencies
    testImplementation("io.ktor:ktor-server-test-host:2.3.7")
    testImplementation("io.ktor:ktor-server-content-negotiation:2.3.7")
    testImplementation("io.ktor:ktor-serialization-kotlinx-json:2.3.7")
    testImplementation("org.springframework:spring-test:6.1.1")
    testImplementation("org.springframework:spring-webmvc:6.1.1")
    testImplementation("org.springframework.boot:spring-boot-test:3.2.0")
    testImplementation("jakarta.servlet:jakarta.servlet-api:6.0.0")
}

tasks.withType<KotlinCompile> {
    kotlinOptions {
        freeCompilerArgs = listOf("-Xjsr305=strict")
        jvmTarget = "17"
    }
}

tasks.test {
    useJUnitPlatform()
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

mavenPublishing {
    publishToMavenCentral(com.vanniktech.maven.publish.SonatypeHost.CENTRAL_PORTAL, automaticRelease = true)
    signIfKeyPresent(project)

    coordinates("io.github.logtide-dev", "logtide-sdk-kotlin", version.toString())

    pom {
        name.set("LogTide Kotlin SDK")
        description.set("Official Kotlin SDK for LogTide - Self-hosted log management with batching, retry logic, circuit breaker, and query API")
        url.set("https://github.com/logtide-dev/logtide-sdk-kotlin")

        licenses {
            license {
                name.set("MIT License")
                url.set("https://opensource.org/licenses/MIT")
            }
        }

        developers {
            developer {
                id.set("polliog")
                name.set("Polliog")
                email.set("giuseppe@solture.it")
            }
            developer {
                id.set("emanueleiannuzzi")
                name.set("Emanuele Iannuzzi")
                email.set("hello@emanueleiannuzzi.me")
            }
        }

        scm {
            connection.set("scm:git:git://github.com/logtide-dev/logtide-sdk-kotlin.git")
            developerConnection.set("scm:git:ssh://github.com/logtide-dev/logtide-sdk-kotlin.git")
            url.set("https://github.com/logtide-dev/logtide-sdk-kotlin")
        }
    }
}

gradle.taskGraph.whenReady {
    val tag = System.getenv("GITHUB_REF_NAME")
        ?.removePrefix("v")
        ?: return@whenReady

    val versionString = project.version.toString()

    if (versionString != tag) {
        throw GradleException(
            "Version mismatch: project.version=$versionString, tag=$tag"
        )
    }
}

tasks.register("printVersion") {
    doLast {
        println(project.version.toString())
    }
}

@OptIn(ExperimentalEncodingApi::class)
fun MavenPublishBaseExtension.signIfKeyPresent(project: Project) {
    val keyId = System.getenv("KEY_ID")
    val keyBytes = Base64.decode(System.getenv("SECRING").toByteArray()).decodeToString()
    val keyPassword = System.getenv("PASSWORD")

    if (keyPassword != null) {
        project.extensions.configure<SigningExtension>("signing") {
            // For binary .gpg keys
            if (keyId == null) {
                useInMemoryPgpKeys(keyBytes, keyPassword)
            } else {
                useInMemoryPgpKeys(keyId, keyBytes, keyPassword)
            }
            signAllPublications()
        }
    } else {
        println("Signing skipped: PASSWORD environment variable not set.")
    }
}