// :app — the only module that may depend on everything. LLM.md §3.9, §10.
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.pion.phonecleaner"
    compileSdk { version = release(37) }

    defaultConfig {
        applicationId = "com.pion.phonecleaner"
        minSdk = 28      // owner decision, asked twice with 26 on the table — LLM.md §10.1
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            // Signed with the DEBUG key, deliberately. No release keystore exists in this repo and
            // no owner decision names one; this variant is built to be installed on the test device
            // (SM-A165F), not uploaded to Play — Play rejects the Android debug certificate outright.
            // Keeping the same signer as the debug build is also what lets an install land on top of
            // one without an uninstall, so a run's DataStore state survives the swap.
            // Replace this in the same commit a real keystore lands. LLM.md §10.4.
            signingConfig = signingConfigs.getByName("debug")

            optimization {
                // UNRESOLVED — LLM.md §10.4. Anything reflective (Room, kotlinx.serialization, the
                // TrustLook SDK) needs its keep rules written in the same commit that enables this.
                enable = false
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        // AGP does not generate BuildConfig unless asked. App.kt reads BuildConfig.DEBUG to set the
        // log gate by direct assignment before startKoin (LLM.md §6.2).
        buildConfig = true
    }
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
    implementation(project(":data"))

    implementation(project(":feature:onboarding"))
    implementation(project(":feature:home"))
    implementation(project(":feature:junk"))
    implementation(project(":feature:photo"))
    implementation(project(":feature:files"))
    implementation(project(":feature:cleanresult"))
    implementation(project(":feature:antivirus"))
    implementation(project(":feature:applock"))
    implementation(project(":feature:vault"))
    implementation(project(":feature:notification"))
    implementation(project(":feature:device"))
    implementation(project(":feature:network"))
    implementation(project(":feature:settings"))

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.bundles.compose)
    implementation(libs.bundles.koin)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.kotlinx.serialization.json)

    testImplementation(libs.bundles.test.unit)
    testImplementation(libs.koin.test.junit4)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.bundles.test.android)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
