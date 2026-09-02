pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "PhoneCleanerDemo"

// Module graph — LLM.md §3.1. Nineteen Gradle projects.
// The edges are enforced by each module's own dependency block; §2 lists the four edges that must never exist.
include(":app")

// core — no feature may be depended upon by these
include(":core:common")   // kotlin("jvm"), no Android
include(":core:mvi")      // android-library + Compose
include(":core:ui")       // android-library + Compose
include(":domain")        // kotlin("jvm"), no Android, no Compose
include(":data")          // android-library, no Compose

// feature — thirteen clusters, one module each (LLM.md §3.8)
include(":feature:onboarding")
include(":feature:home")
include(":feature:junk")
include(":feature:photo")
include(":feature:files")
include(":feature:cleanresult")
include(":feature:antivirus")
include(":feature:applock")
include(":feature:vault")
include(":feature:notification")
include(":feature:device")
include(":feature:network")
include(":feature:settings")
 