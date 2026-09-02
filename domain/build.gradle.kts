// :domain — kotlin("jvm"). No Android, no Compose. LLM.md §3.3.
// The dependency direction is one-way: :domain may depend on :core:common and nothing else.
// It must never see :data, :core:ui, or any :feature (LLM.md §2).
plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
}

java {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11)
        optIn.add("kotlin.time.ExperimentalTime")
    }
}

dependencies {
    api(project(":core:common"))

    // `api`, not `implementation`: every one of these appears in a PUBLIC signature this module
    // exports — Flow on the repository ports, ImmutableList/ImmutableSet on every model a repository
    // hands out (LLM.md §8), @Serializable on CleanupSummary, LocalDate on DailySavings. With
    // `implementation` they are absent from a consumer's compile classpath, and :data and the
    // thirteen :feature modules cannot name the types they were given.
    api(libs.kotlinx.coroutines.android)
    api(libs.kotlinx.collections.immutable)
    api(libs.kotlinx.serialization.json)
    api(libs.kotlinx.datetime)

    // Koin, for `domainModule` (LLM.md §6.4 — every use case is a factory, declared here).
    //
    // The coordinate is written out because the catalogue has no `koin-core` alias and
    // gradle/libs.versions.toml is not this module's to edit. The VERSION still comes from the
    // catalogue, so §10.3's rule — "the single place a version is written" — holds. The `koin`
    // bundle cannot be used: it is koin-android, an AAR, and this is a kotlin("jvm") module, where
    // it fails to resolve (verified: "io.insert-koin:koin-android:4.2.2 FAILED").
    // TODO Add `koin-core = { group = "io.insert-koin", name = "koin-core", version.ref = "koin" }`
    //      to the catalogue and switch this line to `libs.koin.core`.
    implementation("io.insert-koin:koin-core:${libs.versions.koin.get()}")

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
