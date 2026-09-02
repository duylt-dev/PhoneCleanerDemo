package com.pion.phonecleaner.domain.policy

import com.pion.phonecleaner.domain.model.notification.NotificationFacts
import com.pion.phonecleaner.domain.model.notification.NotificationHidingApp
import com.pion.phonecleaner.domain.model.notification.NotificationHidingSettings
import kotlinx.collections.immutable.persistentListOf
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Six branches, six tests, no fakes and no `Context`
 * (`docs/screens/17-notification-and-permissions.md` §5).
 *
 * This is the whole reason the filter was lifted out of `DevitioService`: the competitor's version can
 * only be exercised with a bound `NotificationListenerService` and a real `StatusBarNotification`.
 */
class NotificationInterceptionPolicyTest {

    private val policy = DefaultNotificationInterceptionPolicy()

    private val enabled = NotificationHidingSettings(
        isMasterEnabled = true,
        apps = persistentListOf(NotificationHidingApp("com.chat", "chat", isHidingEnabled = true)),
    )

    private fun facts(
        packageName: String = "com.chat",
        isSystemApp: Boolean = false,
        isOngoing: Boolean = false,
        isGroupSummary: Boolean = false,
        category: String? = null,
        hasContent: Boolean = true,
    ) = NotificationFacts(packageName, isSystemApp, isOngoing, isGroupSummary, category, hasContent)

    @Test
    fun `an opted-in app with content is hidden`() {
        assertTrue(policy.shouldHide(facts(), enabled))
    }

    @Test
    fun `the master switch off hides nothing`() {
        assertFalse(policy.shouldHide(facts(), enabled.copy(isMasterEnabled = false)))
    }

    @Test
    fun `an app the user did not opt in is left alone`() {
        assertFalse(policy.shouldHide(facts(packageName = "com.other"), enabled))
    }

    @Test
    fun `a system app is left alone`() {
        assertFalse(policy.shouldHide(facts(isSystemApp = true), enabled))
    }

    @Test
    fun `an ongoing notification is left alone`() {
        assertFalse(policy.shouldHide(facts(isOngoing = true), enabled))
    }

    @Test
    fun `a group summary is left alone`() {
        // Hiding a summary as if it were a message leaves the shade holding an empty summary after its
        // children are cancelled. Only FLAG_ONGOING_EVENT is filtered by the competitor (§5.2).
        assertFalse(policy.shouldHide(facts(isGroupSummary = true), enabled))
    }

    @Test
    fun `calls, alarms and transport controls are left alone`() {
        listOf("call", "alarm", "transport").forEach { category ->
            assertFalse(category, policy.shouldHide(facts(category = category), enabled))
        }
    }

    @Test
    fun `a blank notification is neither stored nor cancelled`() {
        assertFalse(policy.shouldHide(facts(hasContent = false), enabled))
    }

    @Test
    fun `the service's starting value hides nothing`() {
        assertFalse(policy.shouldHide(facts(), NotificationHidingSettings.Disabled))
    }
}
