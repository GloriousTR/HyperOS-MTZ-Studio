plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

apply(from = "localization.gradle")

android {
    namespace = "dev.glorioustr.mtzstudio"
    compileSdk = 36

    defaultConfig {
        applicationId = "dev.glorioustr.mtzstudio"
        minSdk = 26
        targetSdk = 36
        versionCode = 20
        versionName = "2.4.0"
    }

    signingConfigs {
        create("releaseStable") {
            val releaseStoreFile = providers.environmentVariable("MTZ_RELEASE_STORE_FILE").orNull
            if (!releaseStoreFile.isNullOrBlank()) {
                storeFile = file(releaseStoreFile)
                storePassword = providers.environmentVariable("MTZ_RELEASE_STORE_PASSWORD").orNull
                keyAlias = providers.environmentVariable("MTZ_RELEASE_KEY_ALIAS").orNull
                keyPassword = providers.environmentVariable("MTZ_RELEASE_KEY_PASSWORD").orNull
            }
        }
    }

    buildFeatures {
        aidl = true
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildTypes {
        debug {
            // Keeps the installed release and its data untouched while validating new flows by ADB.
            applicationIdSuffix = ".test"
            versionNameSuffix = "-test"
        }
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("releaseStable")
        }
    }
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(project(":mtz-core"))
    implementation(project(":mtz-library"))
    implementation(project(":mtz-composer"))
    implementation(project(":tester-adapter"))

    val composeBom = platform("androidx.compose:compose-bom:2025.12.01")
    implementation(composeBom)
    implementation("androidx.activity:activity-compose:1.13.0")
    // 2.11+ requires compileSdk 37 / AGP 9.1; this spike is pinned to SDK 36 / AGP 8.13.
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.3")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("dev.rikka.shizuku:api:13.1.5")
    implementation("dev.rikka.shizuku:provider:13.1.5")
    compileOnly("io.github.libxposed:api:101.0.0")
    implementation("io.github.libxposed:service:101.0.0")
    implementation("org.luckypray:dexkit:2.2.0")
    // Text-only theme localization. Language models are downloaded by ML Kit only when the
    // user explicitly runs the Theme Language Tool; no theme content is uploaded by Studio.
    implementation("com.google.mlkit:translate:17.0.3")
    debugImplementation("androidx.compose.ui:ui-tooling")
}
