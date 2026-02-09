import com.vanniktech.maven.publish.MavenPublishBaseExtension
import com.vanniktech.maven.publish.SonatypeHost
import helpers.configureMavenCentralMetadata
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

val kotlinJvmTarget: String by project
val projectGroup: String by project
val projectVersion: String by project

plugins {
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.maven.publish) apply false
}

subprojects {
    if (projectGroup.isBlank() || projectVersion.isBlank()) {
        throw GradleException("Project group and version must be defined in gradle.properties")
    }

    group = projectGroup
    version = projectVersion

    apply(plugin = "org.jetbrains.kotlin.jvm")
    apply(plugin = "com.vanniktech.maven.publish")
    apply(plugin = "signing")

    pluginManager.withPlugin("com.vanniktech.maven.publish") {
        pluginManager.withPlugin("signing") {
            extensions.configure<MavenPublishBaseExtension> {
                publishToMavenCentral(
                    SonatypeHost.CENTRAL_PORTAL,
                    automaticRelease = true
                )
                signIfKeyPresent(this@subprojects)

                pom {
                    configureMavenCentralMetadata(this@subprojects)
                }
            }
        }
    }

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