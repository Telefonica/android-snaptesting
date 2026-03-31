plugins {
    alias(libs.plugins.kotlin.jvm)
    id("java-gradle-plugin")
    alias(libs.plugins.publish.plugin)
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
}

dependencies {
    compileOnly(gradleApi())
    implementation(libs.android.gradle)
    implementation(libs.android.builder.test.api)
    implementation(libs.android.ddmlib)
}

gradlePlugin {
    plugins {
        create("androidsnaptesting-plugin") {
            id = "com.telefonica.androidsnaptesting-plugin"
            displayName = "Android Snaptesting"
            description = "Logs and screenshots snapshot testing for Android Instrumentation tests"
            implementationClass = "com.telefonica.androidsnaptesting.AndroidSnaptestingPlugin"
            website = "https://github.com/Telefonica/android-snaptesting"
            vcsUrl = "https://github.com/Telefonica/android-snaptesting"
            tags = listOf("android", "instrumentation", "testing", "logs")
        }
    }
}
