import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm") version "1.9.21"
    kotlin("plugin.serialization") version "1.9.21"
    id("org.jetbrains.dokka") version "1.9.10"
    id("com.vanniktech.maven.publish") version "0.29.0"
}

group = "io.github.logward-dev"
version = "0.2.0"

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
    publishToMavenCentral(com.vanniktech.maven.publish.SonatypeHost.CENTRAL_PORTAL)
    signAllPublications()

    coordinates("io.github.logward-dev", "logward-sdk-kotlin", version.toString())

    pom {
        name.set("LogWard Kotlin SDK")
        description.set("Official Kotlin SDK for LogWard - Self-hosted log management with batching, retry logic, circuit breaker, and query API")
        url.set("https://github.com/logward-dev/logward-sdk-kotlin")

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
        }

        scm {
            connection.set("scm:git:git://github.com/logward-dev/logward-sdk-kotlin.git")
            developerConnection.set("scm:git:ssh://github.com/logward-dev/logward-sdk-kotlin.git")
            url.set("https://github.com/logward-dev/logward-sdk-kotlin")
        }
    }
}