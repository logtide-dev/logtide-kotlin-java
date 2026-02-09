package plugins

import libsVersionCatalog
import org.gradle.api.Plugin
import org.gradle.api.Project

class LogtideConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            val libs = libsVersionCatalog
            pluginManager.apply(libs.findPlugin("kotlin-jvm").get().get().pluginId)

            dependencies.apply {
                add("implementation", libs.findBundle("kotlin").get())
                add("compileOnly", libs.findBundle("slf4j").get())

                add("testImplementation", libs.findBundle("test-slf4j").get())
                add("testImplementation", libs.findBundle("test-kotlin").get())
            }
        }
    }
}