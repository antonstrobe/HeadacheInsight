plugins {
    alias(libs.plugins.kotlin.jvm)
}

kotlin { jvmToolchain(21) }

tasks.withType<Test>().configureEach {
    useJUnit()
    val kotlinTestClasses = layout.buildDirectory.dir("classes/kotlin/test")
    val javaTestClasses = layout.buildDirectory.dir("classes/java/test")
    val testResources = layout.buildDirectory.dir("resources/test")
    testClassesDirs = files(kotlinTestClasses, javaTestClasses)
    classpath = files(testClassesDirs, testResources) + classpath
}

dependencies {
    implementation(project(":core:model"))
    implementation(project(":core:common"))
    implementation(libs.javax.inject)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.datetime)
    implementation(libs.kotlinx.serialization.json)
    testImplementation(project(":core:testing"))
    testImplementation(libs.junit4)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.truth)
    testImplementation(libs.turbine)
}
