plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
}

fun runCommand(vararg command: String): String {
    return try {
        val process = ProcessBuilder(*command)
            .directory(rootDir.parentFile)
            .redirectErrorStream(true)
            .start()
        val output = process.inputStream.bufferedReader().use { it.readText() }.trim()
        process.waitFor()
        if (process.exitValue() == 0) {
            output
        } else {
            ""
        }
    } catch (_: Exception) {
        ""
    }
}

fun normalizeGithubUrl(remoteUrl: String): String = when {
    remoteUrl.startsWith("git@github.com:") -> {
        "https://github.com/" + remoteUrl.removePrefix("git@github.com:").removeSuffix(".git")
    }
    remoteUrl.startsWith("https://github.com/") -> remoteUrl.removeSuffix(".git")
    else -> "https://github.com/antonstrobe/HeadacheInsight"
}

fun String.asBuildConfigString(): String =
    "\"" + replace("\\", "\\\\").replace("\"", "\\\"") + "\""

val gitSha = runCommand("git", "rev-parse", "--short=12", "HEAD").ifBlank { "unknown" }
val repoUrl = normalizeGithubUrl(runCommand("git", "remote", "get-url", "origin"))
val releasesUrl = "$repoUrl/releases"

android {
    namespace = "com.neuron.headacheinsight"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.neuron.headacheinsight"
        minSdk = 28
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
        testInstrumentationRunner = "com.neuron.headacheinsight.app.HeadacheInsightTestRunner"
        vectorDrawables.useSupportLibrary = true
        buildConfigField("String", "APP_GIT_SHA", gitSha.asBuildConfigString())
        buildConfigField("String", "APP_REPO_URL", repoUrl.asBuildConfigString())
        buildConfigField("String", "APP_RELEASES_URL", releasesUrl.asBuildConfigString())
    }

    flavorDimensions += "environment"
    productFlavors {
        create("demo") {
            dimension = "environment"
            buildConfigField("String", "BACKEND_BASE_URL", "\"http://10.0.2.2:8000/\"")
            buildConfigField("boolean", "CLOUD_ENABLED_BY_DEFAULT", "false")
        }
        create("prod") {
            dimension = "environment"
            buildConfigField("String", "BACKEND_BASE_URL", "\"http://10.0.2.2:8000/\"")
            buildConfigField("boolean", "CLOUD_ENABLED_BY_DEFAULT", "true")
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    androidResources {
        generateLocaleConfig = true
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
    kotlin {
        jvmToolchain(21)
    }
}

dependencies {
    implementation(project(":core:common"))
    implementation(project(":core:model"))
    implementation(project(":core:designsystem"))
    implementation(project(":core:ui"))
    implementation(project(":data:local"))
    implementation(project(":data:remote"))
    implementation(project(":data:repository"))
    implementation(project(":domain"))
    implementation(project(":feature:onboarding"))
    implementation(project(":feature:home"))
    implementation(project(":feature:quicklog"))
    implementation(project(":feature:episode"))
    implementation(project(":feature:questionnaire"))
    implementation(project(":feature:profile"))
    implementation(project(":feature:attachments"))
    implementation(project(":feature:insights"))
    implementation(project(":feature:reports"))
    implementation(project(":feature:settings"))
    implementation(project(":feature:sync"))

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.hilt.navigation.compose)
    implementation(libs.androidx.hilt.work)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.preview)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.work.runtime)
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    ksp(libs.androidx.hilt.compiler)

    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.hilt.android.testing)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.espresso.core)
    kspAndroidTest(libs.hilt.compiler)
    testImplementation(libs.junit4)
}
