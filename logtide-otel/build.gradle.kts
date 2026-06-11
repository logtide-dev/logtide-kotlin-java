plugins {
    id("logtide.convention")
}

dependencies {
    implementation(project(":logtide-core"))
    api(frameworks.opentelemetry.sdk)
    implementation(frameworks.opentelemetry.exporter.otlp)
}
