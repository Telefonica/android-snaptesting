plugins {
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.detekt)
}

allprojects {
    group = "com.telefonica.androidsnaptesting"
    version = System.getProperty("LIBRARY_VERSION") ?: "undefined"

    apply(plugin = rootProject.libs.plugins.detekt.get().pluginId)
    detekt {
        source.from(files(projectDir))
        config.from(files("${rootProject.projectDir}/../build-tools/detekt/detekt.yml"))
        buildUponDefaultConfig = true
    }
}

apply("${rootProject.projectDir}/../publish_maven_central.gradle")