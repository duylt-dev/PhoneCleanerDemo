package com.pion.phonecleaner.data.security

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import com.pion.phonecleaner.core.common.concurrent.DispatcherProvider
import com.pion.phonecleaner.core.common.error.AppError
import com.pion.phonecleaner.core.common.result.AppResult
import com.pion.phonecleaner.data.datastore.SecurityConsentPrefs
import com.pion.phonecleaner.domain.model.security.ScanConsentState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/**
 * `DataStore<Preferences>`, never `SharedPreferences.commit()`: the competitor writes its consent
 * flag with a synchronous commit on the calling thread, on the scan path
 * (`docs/screens/15-antivirus.md` §1.5, last-but-one row).
 *
 * The one store of the process is injected from `coreModule`; this class creates none
 * (`AppDataStore.kt` rule 1).
 */
internal class DataStoreConsentStore(
    private val dataStore: DataStore<Preferences>,
    private val dispatchers: DispatcherProvider,
) : ConsentStore {

    /**
     * A recorded acceptance against an **older** disclosure reads as [ScanConsentState.Unanswered],
     * which re-asks. That is the whole point of the second key: the text enumerates what leaves the
     * device, so agreeing to an older list is not agreement to a longer one.
     *
     * A recorded *rejection* is honoured regardless of version — re-asking a user who said no,
     * because we changed our own text, is the behaviour §0.5 rejects.
     */
    override suspend fun state(): ScanConsentState = withContext(dispatchers.io) {
        val prefs = dataStore.data.first()
        val answer = prefs[SecurityConsentPrefs.SCAN_DATA_CONSENT]
        val version = prefs[SecurityConsentPrefs.SCAN_CONSENT_VERSION] ?: 0
        when {
            answer == null -> ScanConsentState.Unanswered
            !answer -> ScanConsentState.Rejected
            version < CONSENT_VERSION -> ScanConsentState.Unanswered
            else -> ScanConsentState.Granted
        }
    }

    override suspend fun record(granted: Boolean): AppResult<Unit> = withContext(dispatchers.io) {
        try {
            dataStore.edit { prefs ->
                prefs[SecurityConsentPrefs.SCAN_DATA_CONSENT] = granted
                prefs[SecurityConsentPrefs.SCAN_CONSENT_VERSION] = CONSENT_VERSION
            }
            AppResult.Success(Unit)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (throwable: Throwable) {
            AppResult.Failure(AppError.Storage(cause = throwable.message))
        }
    }

    internal companion object {
        /**
         * The version of the disclosure this build renders — the `antivirus_consent_*` strings of
         * `:feature:antivirus`.
         *
         * `1` because this app has shipped exactly one disclosure; the competitor's text is quoted as
         * research and is not ours. **Raise it in the same change that edits what the disclosure
         * says**, and every user is asked again — which is the requirement §0.5 states and the
         * reason the key exists. It is deliberately not derived from anything automatic: a hash of
         * the strings would re-ask on a translation fix.
         */
        const val CONSENT_VERSION: Int = 1
    }
}
