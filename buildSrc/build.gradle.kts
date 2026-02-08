plugins {
    `kotlin-dsl`
}

repositories {
    mavenLocal()
    mavenCentral()
    maven { url = uri("https://packages.confluent.io/maven/") }
}

dependencies {
    gradleApi()
}