plugins {
    id("logtide.convention")
    alias(libs.plugins.kotlin.serialization)
}

dependencies {
    implementation(libs.bundles.kotlin.serialization)
    implementation(libs.bundles.okhttp)

    testImplementation(libs.bundles.test.okhttp)
}