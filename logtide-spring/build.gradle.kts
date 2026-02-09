plugins {
    id("logtide.convention")
}

dependencies {
    implementation(project(":logtide-core"))
    compileOnly(frameworks.spring.boot.starter.web)

    testImplementation(frameworks.bundles.test.spring)
    testImplementation(frameworks.bundles.test.jakarta)
}