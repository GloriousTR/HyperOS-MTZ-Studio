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
        versionCode = 2
        versionName = "0.2.0-spike"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
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
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")
}
