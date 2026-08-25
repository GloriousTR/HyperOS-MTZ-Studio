plugins {
    id("org.jetbrains.kotlin.jvm")
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(project(":mtz-core"))
    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
}

