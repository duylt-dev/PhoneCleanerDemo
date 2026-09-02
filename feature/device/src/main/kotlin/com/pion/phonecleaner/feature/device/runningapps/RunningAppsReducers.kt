package com.pion.phonecleaner.feature.device.runningapps

import kotlinx.collections.immutable.toImmutableList

/**
 * The one reducer that may set `RunningApp.isStopped`
 * (`docs/screens/18-device-battery-and-apps.md` §6.5).
 *
 * It is pure and it takes the platform's answer as a parameter, so the rule it encodes — **a row is
 * marked stopped only when Android says so** — is a unit test with no fakes.
 *
 * `isStopped = false` is not written back. The competitor's Stop path claims a result it cannot
 * observe; ours says nothing at all when the bit is absent, which is a different thing from saying
 * the app is running.
 *
 * The list is rebuilt by `copy` into a **new** `ImmutableList`. The competitor mutates an `ArrayList`
 * its adapter also holds, so `notifyDataSetChanged()` is the only thing keeping the two consistent.
 */
internal fun RunningAppsState.withVerifiedStop(
    packageName: String,
    isStopped: Boolean,
): RunningAppsState = copy(
    awaitingForceStopOf = null,
    apps = if (!isStopped) {
        apps
    } else {
        apps.map { app ->
            if (app.packageName == packageName) app.copy(isStopped = true) else app
        }.toImmutableList()
    },
)
