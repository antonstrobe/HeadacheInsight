pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "HeadacheInsight"

include(
    ":app",
    ":core:common",
    ":core:model",
    ":core:designsystem",
    ":core:ui",
    ":core:testing",
    ":data:local",
    ":data:remote",
    ":data:repository",
    ":domain",
    ":feature:onboarding",
    ":feature:home",
    ":feature:quicklog",
    ":feature:episode",
    ":feature:questionnaire",
    ":feature:profile",
    ":feature:attachments",
    ":feature:insights",
    ":feature:reports",
    ":feature:settings",
    ":feature:sync",
)
