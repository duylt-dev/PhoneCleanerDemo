package com.pion.phonecleaner.feature.trash

import com.pion.phonecleaner.core.common.error.AppError
import com.pion.phonecleaner.core.common.result.AppResult
import kotlinx.coroutines.withTimeoutOrNull

/** Reconciliation should be short; a batch restore may wait for MediaStore for each item. */
internal const val RECONCILE_TIMEOUT_MILLIS = 30_000L
internal const val ACTION_TIMEOUT_MILLIS = 300_000L

/**
 * A silent collaborator cannot strand the screen's re-entry guard forever. Timeout cancels the
 * operation structurally and waits for any in-flight NonCancellable filesystem commit to settle;
 * it never lets a retry race a move that is still committing. External cancellation is rethrown.
 */
internal suspend fun <T> timedTrashOperation(
    timeoutMillis: Long = ACTION_TIMEOUT_MILLIS,
    block: suspend () -> AppResult<T>,
): AppResult<T> = withTimeoutOrNull(timeoutMillis) { block() }
    ?: AppResult.Failure(AppError.Storage(cause = "Trash operation timed out"))
