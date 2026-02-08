import com.vanniktech.maven.publish.MavenPublishBaseExtension
import com.vanniktech.maven.publish.SonatypeHost
import helpers.configureMavenCentralMetadata
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

val kotlinJvmTarget: String by project

plugins {
    alias(libs.plugins.kotlin.jvm) apply true
    alias(libs.plugins.maven.publish)
}

allprojects {
    if (this == rootProject) return@allprojects
    pluginManager.withPlugin("maven-publish") {
        pluginManager.withPlugin("signing") {
            mavenPublishing {
                publishToMavenCentral(SonatypeHost.CENTRAL_PORTAL, automaticRelease = true)
                signIfKeyPresent(this@allprojects)

                pom {
                    configureMavenCentralMetadata(this@allprojects)
                }
            }
        }
    }
}

subprojects {
    plugins.withType<JavaPlugin>().configureEach {
        extensions.configure<JavaPluginExtension> {
            toolchain {
                languageVersion.set(JavaLanguageVersion.of(kotlinJvmTarget))
                vendor.set(JvmVendorSpec.AMAZON)
                sourceCompatibility = JavaVersion.VERSION_17
                targetCompatibility = JavaVersion.VERSION_17
            }
        }
    }

    plugins.withId("org.jetbrains.kotlin.jvm") {
        extensions.configure<KotlinJvmProjectExtension> {
            jvmToolchain(kotlinJvmTarget.toInt())
        }
    }

    tasks.withType<KotlinCompile> {
        kotlinOptions {
            freeCompilerArgs = listOf("-Xjsr305=strict")
            jvmTarget = kotlinJvmTarget
        }
    }

    tasks.withType<Test> {
        useJUnitPlatform()
    }
}

@OptIn(ExperimentalEncodingApi::class)
private fun MavenPublishBaseExtension.signIfKeyPresent(project: Project) {
    val keyId = System.getenv("KEY_ID")
    val keyBytes = runCatching {
        Base64.decode(System.getenv("SECRING").toByteArray()).decodeToString()
    }.getOrNull()
    val keyPassword = System.getenv("PASSWORD")

    if (keyBytes != null && keyPassword != null) {
        println("Signing artifacts with in-memory PGP key (.gpg)")
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
        println("Skipping signing of artifacts: PGP key or password not found in environment variables")
    }
}