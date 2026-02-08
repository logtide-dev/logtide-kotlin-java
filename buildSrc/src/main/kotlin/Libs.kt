import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.kotlin.dsl.findByType

@PublishedApi
internal inline val Project.libsVersionCatalog
    get() = extensions.findByType<VersionCatalogsExtension>()?.named("libs")
        ?: error("Version catalog 'libs' not found")

@PublishedApi
internal inline val Project.frameworksVersionCatalog
    get() = extensions.findByType<VersionCatalogsExtension>()?.named("frameworks")
        ?: error("Version catalog 'frameworks' not found")
