package com.pion.phonecleaner.data.onboarding

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import com.pion.phonecleaner.core.common.concurrent.DispatcherProvider
import com.pion.phonecleaner.data.datastore.OnboardingPrefs
import com.pion.phonecleaner.domain.model.onboarding.OnboardingState
import com.pion.phonecleaner.domain.repository.OnboardingStateRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/**
 * The first-run latches over the one `DataStore<Preferences>`.
 *
 * It replaces `od.d0`'s three disagreeing flags — `flux_first_flux_value`,
 * `flux_first_launcher_phone_check_shown` and `need_req_noti_perm_spla` — whose write points differ
 * and can therefore disagree (`docs/screens/10-splash-and-onboarding.md:318` delta 10). One record,
 * one write point per flag, observed as a `Flow`.
 *
 * **Every write is one key**, and none of them is `commit()` on the calling thread: `od.d0`'s every
 * write is (`java/od/d0.java:78-81`), inside the frame budget of a screen transition.
 */
internal class DataStoreOnboardingStateRepository(
    private val dataStore: DataStore<Preferences>,
    private val dispatchers: DispatcherProvider,
) : OnboardingStateRepository {

    /**
     * `distinctUntilChanged` so a write to an unrelated key in the same store does not re-emit these
     * four booleans — the splash re-enters its reducer on every emission.
     *
     * `flowOn(dispatchers.io)` is set **here**, in the repository, not by the caller (`LLM.md` §6.5):
     * `cd.d.d`'s caller picks the dispatcher, so the same repository behaves differently per screen.
     */
    override fun observe(): Flow<OnboardingState> = dataStore.data
        .map { prefs ->
            OnboardingState(
                hasCompletedFirstRun = prefs[OnboardingPrefs.ONBOARDING_COMPLETED] == true,
                hasAcceptedTerms = prefs[TERMS_ACCEPTED] == true,
                hasSeenDeviceCheck = prefs[OnboardingPrefs.DEVICE_CHECK_SHOWN] == true,
                hasAnsweredNotificationAsk =
                    prefs[OnboardingPrefs.NOTIFICATION_PERMISSION_ANSWERED] == true,
            )
        }
        .distinctUntilChanged()
        .flowOn(dispatchers.io)

    override suspend fun markFirstRunCompleted() = setTrue(OnboardingPrefs.ONBOARDING_COMPLETED)

    override suspend fun markTermsAccepted() = setTrue(TERMS_ACCEPTED)

    override suspend fun markDeviceCheckSeen() = setTrue(OnboardingPrefs.DEVICE_CHECK_SHOWN)

    override suspend fun markNotificationAskAnswered() =
        setTrue(OnboardingPrefs.NOTIFICATION_PERMISSION_ANSWERED)

    private suspend fun setTrue(key: Preferences.Key<Boolean>) {
        withContext(dispatchers.io) {
            dataStore.edit { it[key] = true }
        }
    }

    private companion object {
        /**
         * UNKNOWN — where this key belongs.
         *
         * `LLM.md` §4 puts every DataStore key in `data/datastore/<Concern>Prefs.kt`, and
         * `OnboardingPrefs` is that file for this cluster. It declares three keys and **no
         * terms-accepted key**, because `docs/screens/10-splash-and-onboarding.md:293` describes the
         * split as three flags while `docs/screens/11-home.md:356` delta 17 needs a **fourth**:
         * `flux_first_flux_value` answers two unrelated questions, and separating them costs one key.
         *
         * It is declared here rather than added to `OnboardingPrefs` because that file is not this
         * change's to edit. **It belongs in `OnboardingPrefs` as
         * `val TERMS_ACCEPTED = booleanPreferencesKey("onboarding_terms_accepted")`**, and moving it
         * there is a rename with no data change as long as the string is carried across unchanged.
         */
        val TERMS_ACCEPTED = booleanPreferencesKey("onboarding_terms_accepted")
    }
}
