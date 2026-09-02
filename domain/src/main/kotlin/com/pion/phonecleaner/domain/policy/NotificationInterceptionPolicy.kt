package com.pion.phonecleaner.domain.policy

import com.pion.phonecleaner.domain.model.notification.NotificationFacts
import com.pion.phonecleaner.domain.model.notification.NotificationHidingSettings

/**
 * "Should this notification be taken out of the shade?" — the competitor's six-test filter, lifted out
 * of the service into a pure function over two values
 * (`docs/screens/17-notification-and-permissions.md` §5).
 *
 * An interface rather than a bare `object` because §4.4 declares it in `notificationDataModule`, and
 * because the service resolves it through Koin's service-locator form: the system builds a
 * `NotificationListenerService` through its no-arg constructor, so nothing can be handed to it (§4.4).
 *
 * It takes no `Context` and no `StatusBarNotification`: `:data` reads the platform once and hands over
 * six scalars, which is what makes the six branches six unit tests.
 */
interface NotificationInterceptionPolicy {

    /** True means: store it, then `cancelNotification`. False means: leave it in the shade untouched. */
    fun shouldHide(facts: NotificationFacts, settings: NotificationHidingSettings): Boolean
}

/**
 * The six tests, in the order they are cheapest to fail.
 *
 * A plain class in `:domain`, not in `:data`, because none of it touches the platform. What it adds
 * over the competitor's single `FLAG_ONGOING_EVENT` test (§5.2):
 *
 *  * **group summaries** — hiding a summary as if it were a message leaves the shade holding an empty
 *    summary after its children are cancelled;
 *  * **call, alarm and transport categories** — a hidden incoming call is the worst outcome this
 *    feature can produce, and media transport controls are not notifications the user wants cleaned;
 *  * **blank entries** — an entry with no title and no body is a row the user cannot identify. The
 *    competitor stores it *and* cancels it; here it is neither stored nor cancelled, so it stays in the
 *    shade where it is at least readable.
 *
 * The three category strings are the AOSP `Notification.CATEGORY_*` values. They are written as literals
 * because `:domain` holds no `android.*` import (`LLM.md` §2); `:data` never re-spells them, it passes
 * `Notification.category` straight through.
 */
class DefaultNotificationInterceptionPolicy : NotificationInterceptionPolicy {

    override fun shouldHide(
        facts: NotificationFacts,
        settings: NotificationHidingSettings,
    ): Boolean = when {
        !settings.isMasterEnabled -> false
        !settings.isHidingEnabledFor(facts.packageName) -> false
        facts.isSystemApp -> false
        facts.isOngoing -> false
        facts.isGroupSummary -> false
        facts.category in ProtectedCategories -> false
        !facts.hasContent -> false
        else -> true
    }

    private companion object {
        /** `Notification.CATEGORY_CALL`, `CATEGORY_ALARM`, `CATEGORY_TRANSPORT`. */
        val ProtectedCategories = setOf("call", "alarm", "transport")
    }
}
