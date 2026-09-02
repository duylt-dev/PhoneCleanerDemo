// :feature:settings — android-library + Compose. LLM.md §3.7.
// Screens: settings · language · about · webview · permissioncentre · devtools (src/debug only)
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
    namespace = "com.pion.phonecleaner.feature.settings"
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

    // The ONLY reason androidx.appcompat is in the version catalogue, and its entry says so:
    // AppCompatDelegate.setApplicationLocales is the correct "apply a locale" call for the 17-locale
    // in-app picker (docs/screens/20-settings-language-and-push.md §2.3, §2.4 delta 1). It is a
    // platform call, so it lives in LanguageRoute's effect collector and nowhere else — the
    // ViewModel may not name Locale, Configuration, Resources or AppCompatDelegate (MVI §8).
    // Used for nothing else in this module: no AppCompatActivity, no AppCompat theme, no widgets.
    implementation(libs.androidx.appcompat)

    debugImplementation(libs.androidx.compose.ui.tooling)
    testImplementation(libs.bundles.test.unit)
}
