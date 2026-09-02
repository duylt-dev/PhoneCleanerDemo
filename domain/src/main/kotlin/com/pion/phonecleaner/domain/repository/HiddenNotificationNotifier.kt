package com.pion.phonecleaner.domain.repository

import com.pion.phonecleaner.core.common.result.AppResult

/**
 * The id-900 "we hid N notifications" summary — `od.i.G` / `od.i.l`
 * (`docs/screens/17-notification-and-permissions.md` §0.2, §5.1).
 *
 * OPEN (appendix §6 item 1) — whether this summary ships at all, and under which notification policy,
 * is not settled. Nothing here decides it: the port exists, `advertise` is a no-op in every case it
 * cannot honour, and the only caller is the interceptor service.
 *
 * [advertise] **no-ops** without `POST_NOTIFICATIONS`, with an empty list, or inside the throttle
 * window. The throttle timestamp is advanced in a `finally`: the competitor writes it inside the `try`
 * after the post, so a throw leaves it un-advanced and the next intercepted notification retries
 * immediately, in a loop (§5.2).
 *
 * It returns `AppResult` and never throws upward — it is called from a system binder thread's coroutine,
 * where an escaping exception has no owner.
 */
interface HiddenNotificationNotifier {

    suspend fun advertise(count: Int, packageNames: List<String>): AppResult<Unit>

    /** Removes the summary. Called when the store is emptied or hiding is switched off. */
    fun dismissSummary()
}
