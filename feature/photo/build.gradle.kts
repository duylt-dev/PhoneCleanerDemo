// :feature:photo — android-library + Compose. LLM.md §3.7.
// Screens: similar · preview · compressor · compressrun · privacy · albums · albumdetail
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
    namespace = "com.pion.phonecleaner.feature.photo"
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
    // Coil is `implementation` in :core:ui, so it is NOT on this module's compile classpath
    // through that edge. Every screen in this cluster draws a MediaStore thumbnail
    // (docs/screens/13-photo-and-media.md §1.3), so the dependency is declared here.
    implementation(libs.coil.compose)
    debugImplementation(libs.androidx.compose.ui.tooling)
    testImplementation(libs.bundles.test.unit)
}
