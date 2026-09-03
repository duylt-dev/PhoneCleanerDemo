package com.pion.phonecleaner.feature.files.appmanager

import androidx.compose.runtime.Immutable
import com.pion.phonecleaner.core.common.error.AppError
import com.pion.phonecleaner.core.mvi.ToolPhase
import com.pion.phonecleaner.core.mvi.UiEffect
import com.pion.phonecleaner.core.mvi.UiIntent
import com.pion.phonecleaner.core.mvi.UiState
import com.pion.phonecleaner.core.ui.component.dialog.ConfirmSpec
import com.pion.phonecleaner.domain.model.app.ManagedApp
import com.pion.phonecleaner.domain.model.cleanup.CleanupSummary
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentSetOf

/**
 * `appmanager` (`docs/screens/14-file-tools-and-app-manager.md` §5). Replaces `OperatioActivity`
 * (583 L) plus `Colben`, `vd.c`, `od.q0.c` and `cd.g` — twelve Activity fields, six comparators and
 * a `BroadcastReceiver` used as a completion counter.
 *
 * This screen is **not** a [com.pion.phonecleaner.core.mvi.FileToolState]: its rows are apps, not
 * files, and its "delete" is a serial round trip through the system uninstaller rather than a
 * `FileDeleter` call. [ToolPhase] is still the phase vocabulary, because the scan → list → select
 * shape is the same one (§0.3).
 */
enum class AppSortKey { Size, InstallDate, LastUsed }

@Immutable
data class AppSort(val key: AppSortKey = AppSortKey.Size, val descending: Boolean = true)

/** Whether `PACKAGE_USAGE_STATS` is granted. It gates ONE column, never the screen (§5.5). */
enum class UsageAccess { Unknown, Granted, Denied }

/**
 * The uninstall queue. **One nullable sub-object, not four flat fields**: the four values are only
 * meaningful together and [isFinished] is the condition that navigates away. Flattening them
 * reproduces the competitor's failure mode — two independent `int`s whose equality is never reached
 * when the user cancels one dialog, leaving the screen with no message and no retry (§5.1).
 */
@Immutable
data class UninstallProgress(
    val queue: ImmutableList<String> = persistentListOf(),
    val current: String? = null,
    val removed: ImmutableSet<String> = persistentSetOf(),
    val declined: ImmutableSet<String> = persistentSetOf(),
) {
    val total: Int get() = queue.size + removed.size + declined.size + (if (current != null) 1 else 0)
    val done: Int get() = removed.size + declined.size
    val isFinished: Boolean get() = current == null && queue.isEmpty()
}

@Immutable
data class AppManagerState(
    val phase: ToolPhase = ToolPhase.Idle,
    val apps: ImmutableList<ManagedApp> = persistentListOf(),
    val selectedPackages: ImmutableSet<String> = persistentSetOf(),
    val sort: AppSort = AppSort(),
    val usageAccess: UsageAccess = UsageAccess.Unknown,

    /** How many rows have a measured size yet. The list renders long before this reaches `apps.size`. */
    val sizedCount: Int = 0,
    val confirm: ConfirmSpec? = null,

    /** `null` = not uninstalling. Advanced by intents, never by a job (§5.2). */
    val uninstalling: UninstallProgress? = null,

    /**
     * The in-app explanation shown BEFORE the user is handed out to Settings — it replaces a
     * translucent coach mark drawn over the system Settings app from a delayed `Runnable` (§5.5).
     *
     * A `Boolean` and not the appendix's `PermissionRationale`: no such type exists in `:core:ui`
     * or `:domain`, this screen has exactly one rationale, and its copy is two fixed strings.
     * Inventing a shared type here would be inventing a name (`LLM.md` §11).
     */
    val rationale: Boolean = false,
    val error: AppError? = null,
) : UiState {

    val totalBytes: Long get() = apps.sumOf { it.totalBytes }

    val selectedBytes: Long
        get() = apps.sumOf { if (it.packageName in selectedPackages) it.totalBytes else 0L }

    val selectedCount: Int get() = selectedPackages.size

    val canUninstall: Boolean
        get() = phase == ToolPhase.Ready && selectedCount > 0 && uninstalling == null

    val showEmptyState: Boolean get() = phase == ToolPhase.Ready && apps.isEmpty()

    /**
     * Whether `PACKAGE_USAGE_STATS` is actually granted. A row needs it to read a `0` last-used stamp:
     * without the grant `0` means "never measured", with it `0` means "not opened inside the window",
     * and those are two different sentences.
     */
    val usageAccessGranted: Boolean get() = usageAccess == UsageAccess.Granted

    /** The *Last used* chip is disabled without the grant, because every value would read `0`. */
    val lastUsedSortEnabled: Boolean get() = usageAccessGranted
}

sealed interface AppManagerIntent : UiIntent {
    /**
     * Raised on every `ON_START`, including the return from Settings — for which no app is handed a
     * result. It re-reads the usage-access grant through `PermissionRepository`, which is why the
     * appendix's `UsageAccessResolved` intent is absent: nothing outside the ViewModel knows the
     * answer, so an intent carrying it would be declared and never raised.
     */
    data object ScreenStarted : AppManagerIntent
    data object GrantUsageAccessPressed : AppManagerIntent
    data object RationaleDismissed : AppManagerIntent
    data object RationaleContinued : AppManagerIntent
    data class RowToggled(val packageName: String) : AppManagerIntent

    /**
     * The shared `SelectionBar` owns this action, and every sibling tool has it. What the appendix
     * deletes is the competitor's `isChooseAll` **field** — written by the footer and read by
     * nobody (§5.1) — not the action itself, which here is a pure reducer over `selectedPackages`.
     */
    data object SelectAllToggled : AppManagerIntent

    /** A re-tap flips the direction; a new key lands descending (§5.2). */
    data class SortSelected(val key: AppSortKey) : AppManagerIntent
    data object UninstallPressed : AppManagerIntent
    data object UninstallConfirmed : AppManagerIntent
    data object UninstallDismissed : AppManagerIntent

    /**
     * One system round trip came back. The ViewModel re-checks the package with
     * `UninstallAppUseCase` rather than trusting the result code — §5.2, *"after each round-trip,
     * `PackageManager.isInstalled(pkg)`"*. The competitor counts `PACKAGE_REMOVED` broadcasts and so
     * counts a background update as a removal.
     */
    data class UninstallReturned(val packageName: String) : AppManagerIntent
    data object CompletionAnimationFinished : AppManagerIntent
    data class AppInfoRequested(val packageName: String) : AppManagerIntent
    data object BackPressed : AppManagerIntent
}

sealed interface AppManagerEffect : UiEffect {
    data object OpenUsageAccessSettings : AppManagerEffect

    /** ONE at a time. The competitor fires N `ACTION_DELETE`s in a loop and the system stacks them. */
    data class RequestUninstall(val packageName: String) : AppManagerEffect
    data class OpenAppInfo(val packageName: String) : AppManagerEffect
    data class NavigateToCleanResult(val summary: CleanupSummary) : AppManagerEffect
    data object NavigateBack : AppManagerEffect
}
