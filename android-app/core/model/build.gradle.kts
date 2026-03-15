plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
}

kotlin { jvmToolchain(21) }

dependencies {
    implementation(project(":core:common"))
    api(libs.kotlinx.datetime)
    api(libs.kotlinx.serialization.json)
}
