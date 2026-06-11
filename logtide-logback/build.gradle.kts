plugins {
    id("logtide.convention")
}

dependencies {
    implementation(project(":logtide-core"))
    compileOnly(frameworks.logback.classic)

    testImplementation(frameworks.logback.classic)
}
