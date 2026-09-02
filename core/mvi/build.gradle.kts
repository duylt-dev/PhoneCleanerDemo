// :core:mvi — android-library + Compose. LLM.md §3.4.
// Holds the base class every screen ViewModel extends. Knows :core:common and :domain; knows no feature.
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.pion.phonecleaner.core.mvi"
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
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.bundles.compose)
    implementation(libs.kotlinx.coroutines.android)
    api(libs.kotlinx.collections.immutable)
    testImplementation(libs.bundles.test.unit)
}
