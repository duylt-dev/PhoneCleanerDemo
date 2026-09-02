package com.pion.phonecleaner.core.mvi

import com.pion.phonecleaner.core.common.error.AppError

/**
 * What the nine file tools share. A cluster's own contract extends these rather than restating them,
 * so `SelectionBar` and `PageHeader` can take any of the nine without a per-screen adapter.
 */
interface FileToolState : UiState {
    val phase: ToolPhase
    val error: AppError?
}

interface FileToolIntent : UiIntent {
    /** The tool was opened, or the user pulled to refresh. */
    interface Rescan : FileToolIntent

    interface ToggleItem : FileToolIntent {
        val id: String
    }

    interface ToggleSelectAll : FileToolIntent

    interface DeleteSelected : FileToolIntent
}

interface FileToolEffect : UiEffect {
    /** Deletion finished; the clean-result screen is the destination five clusters share. */
    interface CleanFinished : FileToolEffect {
        val bytesFreed: Long
    }
}
