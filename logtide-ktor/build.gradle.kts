plugins {
    id("logtide.convention")
    alias(libs.plugins.kotlin.serialization)
}

dependencies {
    implementation(project(":logtide-core"))
    compileOnly(frameworks.ktor.server.core)

    testImplementation(frameworks.bundles.test.ktor)
}