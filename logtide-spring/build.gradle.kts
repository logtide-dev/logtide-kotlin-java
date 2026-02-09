plugins {
    id("logtide.convention")
}

dependencies {
    implementation(project(":logtide-core"))
    compileOnly(frameworks.spring.boot.starter)
}