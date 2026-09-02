package com.pion.phonecleaner.domain.repository

import com.pion.phonecleaner.core.common.result.AppResult
import com.pion.phonecleaner.domain.model.push.PushMessage

/**
 * The single entry point for a received push message
 * (`docs/screens/20-settings-language-and-push.md` §6.2).
 *
 * **One method, on purpose.** The debug bench calls exactly this — "the same code path a real
 * message takes, never a parallel one. A test bench that exercises a copy of the pipeline tests the
 * copy." The competitor's bench matches on a raw notify id typed into a text field, and negative ids
 * exist (§6.4 delta 3).
 *
 * PENDING OWNER DECISION 4 bounds what an implementation may do: notification scenes, re-engagement
 * notifications and the resident status-bar widget are deferred, so **no implementation of this port
 * may post a notification**. With no backend (owner decision 1) nothing reaches it in production
 * anyway; the seam exists so that the day a payload contract is written, one file changes.
 */
interface PushRepository {

    suspend fun handle(message: PushMessage): AppResult<Unit>
}
