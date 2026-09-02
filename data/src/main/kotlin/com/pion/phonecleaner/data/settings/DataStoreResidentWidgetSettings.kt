package com.pion.phonecleaner.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import com.pion.phonecleaner.core.common.concurrent.DispatcherProvider
import com.pion.phonecleaner.core.common.error.AppError
import com.pion.phonecleaner.core.common.log.AppLogger
import com.pion.phonecleaner.core.common.result.AppResult
import com.pion.phonecleaner.data.datastore.SettingsPrefs
import com.pion.phonecleaner.domain.repository.ResidentWidgetSettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/**
 * The widget's opt-in flag over the one `DataStore<Preferences>`
 * (`docs/screens/20-settings-language-and-push.md` §1.4 delta 5).
 *
 * PENDING OWNER DECISION 4 — this file stores a boolean and does nothing else. It posts no
 * notification, enqueues no worker and observes no lifecycle. When the background layer is built,
 * `WidgetRefreshWorker` must read **this** flag; a second flag is a switch that lies.
 *
 * `distinctUntilChanged` so a write to any other key in the shared store does not re-enter the
 * settings reducer, and `flowOn(dispatchers.io)` **here** rather than at the caller (`LLM.md` §6.5).
 */
internal class DataStoreResidentWidgetSettings(
    private val dataStore: DataStore<Preferences>,
    private val dispatchers: DispatcherProvider,
    private val log: AppLogger,
) : ResidentWidgetSettingsRepository {

    /** Absent means OFF: the default is the decision, so nothing is written on install. */
    override fun isEnabled(): Flow<Boolean> = dataStore.data
        .map { it[SettingsPrefs.RESIDENT_WIDGET_ENABLED] == true }
        .distinctUntilChanged()
        .flowOn(dispatchers.io)

    /**
     * The screen holds no optimistic copy: it re-renders from [isEnabled], so a failed write cannot
     * leave the switch showing a state the store does not have
     * (`docs/screens/20-settings-language-and-push.md` §1.2).
     */
    override suspend fun setEnabled(enabled: Boolean): AppResult<Unit> =
        withContext(dispatchers.io) {
            runCatching {
                dataStore.edit { it[SettingsPrefs.RESIDENT_WIDGET_ENABLED] = enabled }
                Unit
            }.fold(
                onSuccess = { AppResult.Success(it) },
                onFailure = {
                    log.e(it) { "Resident widget switch could not be written" }
                    AppResult.Failure(AppError.Storage(cause = it.message))
                },
            )
        }
}
