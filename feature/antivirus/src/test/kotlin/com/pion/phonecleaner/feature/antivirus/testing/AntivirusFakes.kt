package com.pion.phonecleaner.feature.antivirus.testing

import com.pion.phonecleaner.core.common.error.AppError
import com.pion.phonecleaner.core.common.result.AppResult
import com.pion.phonecleaner.domain.model.feature.FeatureId
import com.pion.phonecleaner.domain.model.file.DeleteOutcome
import com.pion.phonecleaner.domain.model.file.ScannedFile
import com.pion.phonecleaner.domain.model.security.ScanConsentState
import com.pion.phonecleaner.domain.model.security.ScanRecord
import com.pion.phonecleaner.domain.model.security.SecurityScanPhase
import com.pion.phonecleaner.domain.model.security.ThreatVerdict
import com.pion.phonecleaner.domain.repository.FeatureUsageRepository
import com.pion.phonecleaner.domain.repository.FileDeleter
import com.pion.phonecleaner.domain.repository.SecurityScanRepository
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flow
import kotlin.time.Instant

/**
 * Hand-written fakes — **no mocking library anywhere in this project** (`LLM.md` §9). A fake states
 * what it does in Kotlin; a mock states it in a DSL that compiles whatever you type.
 */

/**
 * [scanPhases] is what `scan()` emits, in order, and then completes. [scanNeverCompletes] instead
 * hangs after emitting — the shape a real cloud scan has while it is running, and the only way to
 * test the timeout and the stop path.
 */
internal class FakeSecurityScanRepository(
    var scanPhases: List<SecurityScanPhase> = emptyList(),
    var scanNeverCompletes: Boolean = false,
    var consent: ScanConsentState = ScanConsentState.Granted,
    var recordConsentResult: AppResult<Unit> = AppResult.Success(Unit),
    var recordFinishedResult: AppResult<Unit> = AppResult.Success(Unit),
    var forgetResult: AppResult<Unit> = AppResult.Success(Unit),
    var ignoreResult: AppResult<Unit> = AppResult.Success(Unit),
) : SecurityScanRepository {

    val lastResult = MutableStateFlow<ScanRecord?>(null)

    val consentWrites = mutableListOf<Boolean>()
    val recorded = mutableListOf<List<ThreatVerdict>>()
    val forgotten = mutableListOf<String>()
    val ignored = mutableListOf<String>()

    /** Set while a collector is attached, cleared by `awaitClose` — the cancellation assertion. */
    var isScanCollected: Boolean = false
        private set

    override fun scan(): Flow<SecurityScanPhase> = if (scanNeverCompletes) {
        callbackFlow {
            isScanCollected = true
            scanPhases.forEach { trySend(it) }
            awaitClose { isScanCollected = false }
        }
    } else {
        flow {
            isScanCollected = true
            scanPhases.forEach { emit(it) }
            isScanCollected = false
        }
    }

    override suspend fun consent(): ScanConsentState = consent

    override suspend fun recordConsent(granted: Boolean): AppResult<Unit> {
        consentWrites += granted
        if (recordConsentResult is AppResult.Success) {
            consent = if (granted) ScanConsentState.Granted else ScanConsentState.Rejected
        }
        return recordConsentResult
    }

    override fun observeLastResult(): Flow<ScanRecord?> = lastResult

    override suspend fun recordScanFinished(findings: List<ThreatVerdict>): AppResult<Unit> {
        recorded += findings
        return recordFinishedResult
    }

    override suspend fun forget(md5: String): AppResult<Unit> {
        forgotten += md5
        if (forgetResult is AppResult.Success) {
            lastResult.value = lastResult.value?.let { record ->
                record.copy(findings = record.findings.filterNot { it.md5 == md5 }.toImmutableList())
            }
        }
        return forgetResult
    }

    override suspend fun ignore(md5: String): AppResult<Unit> {
        ignored += md5
        if (ignoreResult is AppResult.Success) {
            lastResult.value = lastResult.value?.let { record ->
                record.copy(findings = record.findings.filterNot { it.md5 == md5 }.toImmutableList())
            }
        }
        return ignoreResult
    }

    fun emitRecord(findings: List<ThreatVerdict>, atEpochMs: Long = 1_000L) {
        lastResult.value = ScanRecord(atEpochMs, findings.toImmutableList())
    }
}

internal class FakeFeatureUsageRepository : FeatureUsageRepository {
    val marked = mutableListOf<FeatureId>()
    override suspend fun markUsed(feature: FeatureId) { marked += feature }
    override fun lastUsed(feature: FeatureId): Flow<Instant?> = MutableStateFlow(null)
    override fun staleFeatures(): Flow<ImmutableList<FeatureId>> =
        MutableStateFlow(persistentListOf())
    override suspend fun recommend(): FeatureId = FeatureId.Antivirus
}

/** Deletes nothing; records what it was asked to delete and answers with [outcome]. */
internal class FakeFileDeleter(
    var outcome: AppResult<DeleteOutcome> = AppResult.Success(
        DeleteOutcome.Deleted(persistentListOf(), 0L, persistentListOf()),
    ),
) : FileDeleter {
    val requested = mutableListOf<List<ScannedFile>>()
    override suspend fun delete(files: List<ScannedFile>): AppResult<DeleteOutcome> {
        requested += files
        return outcome
    }
}

/** A finding, with only the fields a given test cares about set. */
internal fun verdict(
    md5: String,
    packageName: String = "",
    score: Int = 8,
    apkPath: String = "/storage/emulated/0/Download/$md5.apk",
    label: String = md5,
    apkSizeBytes: Long = 1_024L,
): ThreatVerdict = ThreatVerdict(
    md5 = md5,
    packageName = packageName,
    apkPath = apkPath,
    label = label,
    score = score,
    vid = "vid-$md5",
    familyName = "family-$md5",
    summary = "summary-$md5",
    category = "category",
    apkSizeBytes = apkSizeBytes,
)

internal val storageFailure = AppError.Storage(path = "/nope")
