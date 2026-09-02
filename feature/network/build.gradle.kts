// :feature:network — android-library + Compose. LLM.md §3.7.
// Screens: traffic · speedtest · speedtestresult
//
// Depends on :core:common, :core:mvi, :core:ui and :domain — and on NO other feature.
// Navigation between clusters is an Effect that :app wires; a direct feature-to-feature edge
// would make the graph a cycle waiting to happen (LLM.md §2).
// It must not depend on :data either: a screen sees repository interfaces, never implementations.
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.pion.phonecleaner.feature.network"
    compileSdk { version = release(37) }
    defaultConfig { minSdk = 28 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures { compose = true }
}

composeCompiler {
    reportsDestination.set(layout.buildDirectory.dir("compose-reports"))
    metricsDestination.set(layout.buildDirectory.dir("compose-reports"))
    stabilityConfigurationFiles.add(rootProject.layout.projectDirectory.file("compose-stability.conf"))
}

dependencies {
    implementation(project(":core:common"))
    implementation(project(":core:mvi"))
    implementation(project(":core:ui"))
    implementation(project(":domain"))
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.bundles.compose)
    implementation(libs.bundles.koin)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.collections.immutable)
    // App icons on the traffic rows: Coil through :core:ui's AppIconLoader, which exposes its own
    // ImageLoader. :core:ui keeps coil as `implementation`, so the composable needs its own.
    implementation(libs.coil.compose)
    debugImplementation(libs.androidx.compose.ui.tooling)
    testImplementation(libs.bundles.test.unit)
}
