package com.pion.phonecleaner.domain.repository

/**
 * What the app knows about itself: the four values `about` and `settings` render.
 *
 * Replaces the hand-typed `"v2.0.0.0"` literal at `AuddulgActivity.java:153`
 * (`docs/screens/20-settings-language-and-push.md` §3.4 delta 3) — a string that drifts from the real
 * `versionName` at the first release, in a screen whose only job is to state it.
 *
 * **[isDebugBuild] is a property here and NOT a static `BuildConfig` read.** That is what keeps
 * `AboutViewModel` platform-free and makes the release behaviour testable: a fake answers `false` and
 * the developer row's guard can be asserted, which a `BuildConfig.DEBUG` reference in the reducer can
 * never be (§3.2).
 *
 * The research's separate `BuildConfigProvider` — one interface for one boolean — was merged into
 * this one: one provider, four properties, one binding (§3.2).
 *
 * Not a `Flow`: none of the four values changes while the process lives.
 */
interface AppInfoProvider {

    /** The launcher label, resolved from the platform rather than duplicated as copy. */
    val appName: String

    /** `PackageInfo.versionName`, e.g. "1.0". */
    val versionName: String

    /** `PackageInfo.longVersionCode`. Rendered beside [versionName] so a build is identifiable. */
    val versionCode: Long

    /**
     * True in a debuggable build. Read from `ApplicationInfo.FLAG_DEBUGGABLE`, so it answers for the
     * installed APK rather than for whichever module's `BuildConfig` happened to be imported.
     */
    val isDebugBuild: Boolean
}
