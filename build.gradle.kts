import com.vanniktech.maven.publish.MavenPublishBaseExtension
import com.vanniktech.maven.publish.SonatypeHost
import helpers.configureMavenCentralMetadata
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import kotlin.io.encoding.ExperimentalEncodingApi

val kotlinJvmTarget: String by project
val projectGroup: String by project
val projectVersion: String by project

plugins {
    alias(libs.plugins.kotlin.jvm)
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

fun MavenPublishBaseExtension.signIfKeyPresent(project: Project) {
    val keyId = System.getenv("SIGNING_KEY_ID")
    val signingKey = System.getenv("SIGNING_KEY")
    val signingKeyPassphrase = System.getenv("SIGNING_KEY_PASSPHRASE")

    if (!signingKey.isNullOrBlank()) {
        project.logger.info("Signing artifacts with in-memory PGP key for ${project.path}")
        project.extensions.configure<SigningExtension>("signing") {
            useInMemoryPgpKeys(keyId, preprocessPrivateGpgKey(signingKey), signingKeyPassphrase)
            signAllPublications()
        }
    } else {
        project.logger.warn("Skipping signing of artifacts: PGP key or password not found in environment variables for ${project.path}")
    }
}

private fun preprocessPrivateGpgKey(key: String): String {
    val prefix = "-----BEGIN PGP PRIVATE KEY BLOCK-----"
    val suffix = "-----END PGP PRIVATE KEY BLOCK-----"
    val delimiter = "\r\n"
    return prefix + delimiter + key
        .replace(prefix, "")
        .replace(suffix, "")
        .replace(" ", "\r\n") + delimiter + suffix
}

tasks.register("printVersion") {
    doLast {
        println(projectVersion)
    }
}