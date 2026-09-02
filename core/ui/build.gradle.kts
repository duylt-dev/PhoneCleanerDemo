// :core:ui — android-library + Compose. LLM.md §3.5.
// Theme, tokens, and the components shared by two or more CLUSTERS. A composable shared by two
// screens of ONE cluster stays in that feature's `component/` package instead.
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.pion.phonecleaner.core.ui"
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
    api(project(":core:common"))
    api(project(":domain"))
    api(project(":core:mvi"))
    implementation(platform(libs.androidx.compose.bom))
    api(libs.bundles.compose)
    api(libs.androidx.compose.material.icons.extended)
    implementation(libs.bundles.koin)
    implementation(libs.coil.compose)
    debugImplementation(libs.androidx.compose.ui.tooling)
    testImplementation(libs.bundles.test.unit)
}
