package com.pion.phonecleaner.data.push

import com.pion.phonecleaner.core.common.log.AppLogger
import com.pion.phonecleaner.core.common.result.AppResult
import com.pion.phonecleaner.domain.model.push.PushMessage
import com.pion.phonecleaner.domain.repository.PushRepository

/**
 * What happens to a received push message today: it is logged, and nothing else.
 *
 * **Named for what it does.** `LLM.md` §5 wants an implementation named for its mechanism; the
 * mechanism here is deliberate inaction, and calling it `DefaultPushRepository` would suggest a
 * default that acts.
 *
 * ### Why it is empty, and why the seam exists anyway
 *
 * 1. **There is no backend and none is in scope** (owner decision 1, `LLM.md` §1). Nothing sends to
 *    this app, so no payload contract exists to write against. Inventing one — a key named
 *    `action_id`, a scene id, a feature index — would look checked and would be a guess.
 * 2. **PENDING OWNER DECISION 4** defers notification scenes, re-engagement notifications and the
 *    resident status-bar widget. Posting a notification from here would decide that.
 *
 * So this class holds the boundary at exactly the point the task allows: a real message is received,
 * mapped to a domain type, and dropped with a log line. `DevToolsViewModel` drives **this same
 * method** — a bench that exercises a copy of the pipeline tests the copy
 * (`docs/screens/20-settings-language-and-push.md` §6.2).
 *
 * The competitor's equivalent path is the reason the seam is worth having: its out-of-app entry
 * `mf.EnsileActivity` is `exported="true"` with no `intent-filter`, no permission and no caller
 * check, and does `putExtras(getIntent())` verbatim into a launch (`LLM.md` §7.3), so any installed
 * app can drive it to any module. Ours has no exported trampoline, and a payload that is only logged
 * cannot route anything.
 */
internal class InertPushRepository(
    private val log: AppLogger,
) : PushRepository {

    override suspend fun handle(message: PushMessage): AppResult<Unit> {
        log.d { "Push message ${message.messageId} received with ${message.data.size} data keys; no handler is defined" }
        return AppResult.Success(Unit)
    }
}
