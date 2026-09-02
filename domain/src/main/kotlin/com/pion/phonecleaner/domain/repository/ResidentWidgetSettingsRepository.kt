package com.pion.phonecleaner.domain.repository

import com.pion.phonecleaner.core.common.result.AppResult
import kotlinx.coroutines.flow.Flow

/**
 * The on/off switch for the resident status-bar widget — **the one piece of the out-of-app layer
 * that crosses into a screen** (`docs/screens/20-settings-language-and-push.md` §0, §1.4 delta 5).
 *
 * PENDING OWNER DECISION 4. The widget itself, the re-engagement notifications and the notification
 * scenes are all deferred; what is settled is that the widget ships **opt-in, default OFF**. This
 * port is that flag and nothing else. Nothing in this repository posts a notification, enqueues a
 * worker or declares `RECEIVE_BOOT_COMPLETED`, and adding any of those here would decide the
 * deferred half.
 *
 * Why the flag exists before the widget does: since Android 13 a notification the user cannot turn
 * off *inside* the app is one they revoke wholesale, taking every legitimate notification with it.
 * The competitor posts its resident notification from `Application.onCreate`
 * (`java/hc/f.java:79-89`, `java/wd/j.java:113-123`) and has no off switch anywhere in the app.
 *
 * UNKNOWN — §8 open item 1: this name is **not** in `docs/system-architecture.md` §4.1's alias table,
 * because the widget became opt-in after the research closed. §7.1 assigns the binding to
 * `backgroundModule`, which does not exist yet; it is bound in `settingsDataModule` in the meantime.
 * Whoever builds the background layer must **reconcile with this one binding**, not declare a second
 * copy — `WidgetRefreshWorker` and this switch have to read the same flag or the switch lies.
 */
interface ResidentWidgetSettingsRepository {

    /** `false` until the user turns it on. The default is the decision. */
    fun isEnabled(): Flow<Boolean>

    suspend fun setEnabled(enabled: Boolean): AppResult<Unit>
}
