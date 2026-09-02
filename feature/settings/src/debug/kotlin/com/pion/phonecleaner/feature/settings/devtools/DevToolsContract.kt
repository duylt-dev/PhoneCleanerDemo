package com.pion.phonecleaner.feature.settings.devtools

import androidx.compose.runtime.Immutable
import com.pion.phonecleaner.core.mvi.UiEffect
import com.pion.phonecleaner.core.mvi.UiIntent
import com.pion.phonecleaner.core.mvi.UiState

/**
 * `devtools` (`docs/screens/20-settings-language-and-push.md` §6.1). **`src/debug` only.**
 *
 * The competitor ships 351 lines of this bench in its **release** APK, unreachable only because a
 * compile-time constant is 5 and one view is `gone` — a harness that can post any notification and
 * rewrite the device id, in a shipped binary (§6.4 delta 1). R8 cannot include what is not compiled,
 * so the whole screen lives in this source set and there is nothing to strip.
 *
 * ### Two thirds of the appendix's bench are deliberately absent
 *
 * | §6.1 field | Status |
 * |---|---|
 * | `availableScenes` · `selectedSceneId` · `PostSceneTapped` | **Cut.** Posting a scene is posting a notification, and PENDING OWNER DECISION 4 defers notification scenes, re-engagement notifications and the resident widget. A bench that posts one decides the deferred half. `NotificationSceneRepository` is therefore not declared either |
 * | `currentDeviceId` · `deviceIdInput` · `SaveDeviceIdTapped` | **Cut.** §6.4 delta 5 records `flux_device_flux_hashedid` as an orphan — "written here and read by nothing in the app module" — and says "if the override is needed, one repository owns both sides". With no backend (owner decision 1) nothing in this app reads a device id, so an override here would be an orphan by construction |
 *
 * What remains is the half that tests something real: a message goes down **the same code path a
 * real one takes**. A bench that exercises a copy of the pipeline tests the copy (§6.2).
 */
@Immutable
data class DevToolsState(
    /** One `key=value` per line. Firebase's `RemoteMessage.data` is exactly a `Map<String, String>`. */
    val payloadInput: String = "",
) : UiState

sealed interface DevToolsIntent : UiIntent {
    data class PayloadChanged(val value: String) : DevToolsIntent
    data object DeliverTapped : DevToolsIntent
    data object BackPressed : DevToolsIntent
}

/**
 * The message arms carry **data, not copy**: the ViewModel never builds a user-facing string, so the
 * Route resolves them against `stringResource` (MVI §5). Even on a debug screen — the rule is what
 * keeps a ViewModel testable without a `Context`.
 */
sealed interface DevToolsEffect : UiEffect {
    data class Delivered(val dataKeyCount: Int) : DevToolsEffect
    data object NothingToDeliver : DevToolsEffect
    data object DeliveryFailed : DevToolsEffect
    data object NavigateBack : DevToolsEffect
}
