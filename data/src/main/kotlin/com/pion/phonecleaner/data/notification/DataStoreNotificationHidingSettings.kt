package com.pion.phonecleaner.data.notification

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import com.pion.phonecleaner.core.common.concurrent.DispatcherProvider
import com.pion.phonecleaner.core.common.error.AppError
import com.pion.phonecleaner.core.common.log.AppLogger
import com.pion.phonecleaner.core.common.result.AppResult
import com.pion.phonecleaner.data.datastore.NotificationPrefs
import com.pion.phonecleaner.domain.model.notification.NotificationHidingApp
import com.pion.phonecleaner.domain.model.notification.NotificationHidingSettings
import com.pion.phonecleaner.domain.repository.InstalledAppsRepository
import com.pion.phonecleaner.domain.repository.NotificationHidingSettingsStore
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/**
 * `od.g0`'s two flags, over the one DataStore, joined with the installed-app list
 * (`docs/screens/17-notification-and-permissions.md` §0.2, §2.2).
 *
 * DECLARED IN `notificationDataModule`. It **composes** [InstalledAppsRepository] — declared once in
 * `coreDataModule` and named by four clusters — rather than enumerating packages itself; a second
 * enumeration here is the duplicate-`single` collision `LLM.md` §6.4 measures.
 *
 * Doing the join here is what lets every screen open **one** collector: the master switch and the rows
 * arrive in one value, so they can never be a frame out of step (§2.2).
 *
 * **Labels only.** `vd.d.a()` calls `loadIcon` for every launchable app before anything renders, so the
 * first frame waits on N `PackageManager` icon loads; here each visible row asks `AppIconLoader` by
 * package name at draw time (§2.5).
 *
 * The enumeration runs on `dispatchers.default` and the writes on `dispatchers.io`, both chosen here
 * and never by a caller (`LLM.md` §6.5). Every competitor write is a `commit()` on the main thread, one
 * synchronous fsync per switch tap (§2.5).
 */
internal class DataStoreNotificationHidingSettings(
    private val dataStore: DataStore<Preferences>,
    private val installedApps: InstalledAppsRepository,
    private val dispatchers: DispatcherProvider,
    private val log: AppLogger,
) : NotificationHidingSettingsStore {

    override fun observe(): Flow<NotificationHidingSettings> =
        storedState()
            .map { (master, enabled) -> NotificationHidingSettings(master, rows(enabled)) }
            .flowOn(dispatchers.default)

    override suspend fun setMasterEnabled(enabled: Boolean): AppResult<Unit> =
        write("setMasterEnabled") { prefs ->
            prefs[NotificationPrefs.HIDING_MASTER_ENABLED] = enabled
        }

    override suspend fun setAppEnabled(packageName: String, enabled: Boolean): AppResult<Unit> =
        write("setAppEnabled") { prefs ->
            val current = prefs[NotificationPrefs.HIDING_ENABLED_PACKAGES].orEmpty()
            // A per-app write NEVER touches the master flag, whatever this leaves the count at (§2.5).
            prefs[NotificationPrefs.HIDING_ENABLED_PACKAGES] =
                if (enabled) current + packageName else current - packageName
        }

    /**
     * The raw stored pair. `distinctUntilChanged` so an unrelated preference write — a feature-usage
     * stamp, say — is not a re-enumeration of every installed app.
     */
    private fun storedState(): Flow<Pair<Boolean, Set<String>>> =
        dataStore.data
            .map { prefs ->
                // Absent means enabled: parity with the competitor's default (§2.1).
                val master = prefs[NotificationPrefs.HIDING_MASTER_ENABLED] ?: true
                master to prefs[NotificationPrefs.HIDING_ENABLED_PACKAGES].orEmpty()
            }
            .distinctUntilChanged()

    private suspend fun rows(enabledPackages: Set<String>) =
        when (val result = installedApps.installedApps()) {
            is AppResult.Success -> result.value
                .map { NotificationHidingApp(it.packageName, it.label, it.packageName in enabledPackages) }
                .sortedBy { it.label.lowercase() }
                .toImmutableList()

            is AppResult.Failure -> {
                // An enumeration failure is not "no app has hiding enabled": the screen renders its own
                // empty state, and nothing is written back.
                log.e { "Notification hiding could not enumerate installed apps: ${result.error}" }
                persistentListOf()
            }
        }

    private suspend fun write(what: String, edit: (androidx.datastore.preferences.core.MutablePreferences) -> Unit): AppResult<Unit> =
        withContext(dispatchers.io) {
            runCatching { dataStore.edit(edit) }.fold(
                onSuccess = { AppResult.Success(Unit) },
                onFailure = {
                    log.e(it) { "Notification hiding $what failed" }
                    AppResult.Failure(AppError.Unexpected(it.message))
                },
            )
        }
}
