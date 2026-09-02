// :data — android-library, no Compose, no feature imports. LLM.md §3.6.
// Every repository IMPLEMENTATION, and `coreModule` itself (§6.5). A screen sees only the
// interfaces in :domain, so this module has no edge to any :feature and none to :core:ui.
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.pion.phonecleaner.data"
    compileSdk { version = release(37) }
    defaultConfig { minSdk = 28 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

kotlin {
    compilerOptions {
        // AppClock is a typealias for kotlin.time.Clock, still @ExperimentalTime in Kotlin 2.2.
        // Any module that injects AppClock needs this line; :data is currently the only one,
        // because coreModule is where the binding is declared (LLM.md §6.5).
        optIn.add("kotlin.time.ExperimentalTime")
    }
}

dependencies {
    api(project(":core:common"))
    api(project(":domain"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.collections.immutable)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.datetime)
    implementation(libs.bundles.koin.data)
    implementation(libs.bundles.room)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.documentfile)
    implementation(libs.androidx.exifinterface)
    implementation(libs.androidx.lifecycle.process)

    // Firebase Cloud Messaging — ONLY so PushMessagingService, a manifest component of this module
    // (LLM.md §3.6), can extend FirebaseMessagingService. The SDK's internals are out of scope, and
    // there is no backend (owner decision 1), so nothing sends to it: data/push/ maps an incoming
    // message to a domain type and stops there. The BOM governs the version; the catalogue entry for
    // firebaseBom says "messaging only".
    //
    // No google-services plugin and no google-services.json: neither is needed to COMPILE, and
    // adding the plugin would edit the root build file, which this change does not own. Without that
    // file FirebaseInitProvider logs that the default app failed to initialise and returns — the
    // service simply never receives anything, which is already true with no sender.
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.messaging)
    testImplementation(libs.bundles.test.unit)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.room.testing)
    testImplementation(libs.androidx.work.testing)
}
