plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "dev.glorioustr.mtzstudio"
    compileSdk = 36

    defaultConfig {
        applicationId = "dev.glorioustr.mtzstudio"
        minSdk = 26
        targetSdk = 36
        versionCode = 3
        versionName = "0.3.0-diagnostics"
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
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("debug")
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
    debugImplementation("androidx.compose.ui:ui-tooling")
}
