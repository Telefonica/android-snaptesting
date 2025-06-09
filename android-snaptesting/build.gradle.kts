plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin)
}

android {
    namespace = "com.telefonica.androidsnaptesting"
    compileSdk = libs.versions.compile.sdk.get().toInt()


    defaultConfig {
        minSdk = libs.versions.min.sdk.get().toInt()

        consumerProguardFiles("consumer-rules.pro")
    }

    testOptions {
        targetSdk = libs.versions.target.sdk.get().toInt()
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}

kotlin {
    explicitApi()
}

dependencies {
    api(libs.differ)

    implementation(libs.core.ktx)
    implementation(libs.junit)
    implementation(libs.androidx.test.monitor)
    implementation(libs.androidx.test.runner)
    implementation(libs.androidx.ui.test.junit4.android)
}

apply("${rootProject.projectDir}/mavencentral.gradle")
