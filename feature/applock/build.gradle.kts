// :feature:applock — android-library + Compose. LLM.md §3.7.
// Screens: applock · pin · lockscreen · settings
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
    namespace = "com.pion.phonecleaner.feature.applock"
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
    // App-icon rows only. `AppIconLoader` (`coreUiModule`) owns the ImageLoader and the fetcher;
    // this module needs the `AsyncImage` composable to hand it a request (`:core:ui` keeps Coil
    // `implementation`, so it is not on this module's compile classpath through the project edge).
    implementation(libs.coil.compose)
    debugImplementation(libs.androidx.compose.ui.tooling)
    testImplementation(libs.bundles.test.unit)
}
