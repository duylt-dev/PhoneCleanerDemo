package com.pion.phonecleaner.feature.applock.testing

import com.pion.phonecleaner.core.common.error.AppError
import com.pion.phonecleaner.core.common.result.AppResult
import com.pion.phonecleaner.domain.model.app.InstalledApp
import com.pion.phonecleaner.domain.model.applock.AppLockSettings
import com.pion.phonecleaner.domain.model.applock.LockableApp
import com.pion.phonecleaner.domain.model.applock.PinLockout
import com.pion.phonecleaner.domain.model.applock.PinVerdict
import com.pion.phonecleaner.domain.repository.AppLockPinRepository
import com.pion.phonecleaner.domain.repository.AppLockRepository
import com.pion.phonecleaner.domain.repository.AppLockSettingsRepository
import com.pion.phonecleaner.domain.repository.ForegroundAppMonitor
import com.pion.phonecleaner.domain.repository.InstalledAppsRepository
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toImmutableSet
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.emptyFlow

/**
 * Hand-written fakes — **no mocking library** (`LLM.md` §9): a fake you can read beats a mock you
 * have to decode.
 *
 * **No fake here ever returns a PIN**, because the interface it doubles has no method that could.
 * [FakeAppLockPinRepository] holds the expected PIN as a plain `String` — which is exactly what
 * production must never do, and is safe in a fixture that lives for one test method.
 */
internal class FakeAppLockPinRepository(
    /** The PIN a candidate is compared against. `null` means "no PIN set". */
    var storedPin: String? = "1234",
    initialLockout: PinLockout = PinLockout(),
) : AppLockPinRepository {

    private val lockout = MutableStateFlow(initialLockout)

    /** Every candidate, in order, so a test can assert what was submitted — never asserted equal
     *  to anything a composable saw, because no composable ever sees one. */
    val verified = mutableListOf<String>()
    val saved = mutableListOf<String>()
    var clearCount: Int = 0

    /** Set to make the next call fail, so the `AppResult.Failure` arm is reachable. */
    var failure: AppError? = null

    /** Returned instead of the natural verdict, for the LockedOut and NotSet branches. */
    var nextVerdict: PinVerdict? = null

    override fun isPinSet(): Flow<Boolean> = MutableStateFlow(storedPin != null).asStateFlow()

    override fun observeLockout(): Flow<PinLockout> = lockout.asStateFlow()

    override suspend fun verifyPin(pin: String): AppResult<PinVerdict> {
        verified += pin
        failure?.let { return AppResult.Failure(it) }
        nextVerdict?.let { return AppResult.Success(it) }
        val stored = storedPin ?: return AppResult.Success(PinVerdict.NotSet)
        return if (pin == stored) {
            lockout.value = PinLockout()
            AppResult.Success(PinVerdict.Verified)
        } else {
            val next = PinLockout(failedAttempts = lockout.value.failedAttempts + 1)
            lockout.value = next
            AppResult.Success(PinVerdict.Rejected(next))
        }
    }

    override suspend fun savePin(pin: String): AppResult<Unit> {
        saved += pin
        failure?.let { return AppResult.Failure(it) }
        storedPin = pin
        return AppResult.Success(Unit)
    }

    override suspend fun clearPin(): AppResult<Unit> {
        clearCount++
        failure?.let { return AppResult.Failure(it) }
        storedPin = null
        return AppResult.Success(Unit)
    }
}

internal class FakeAppLockRepository(
    initial: List<LockableApp> = emptyList(),
) : AppLockRepository {

    private val apps = MutableStateFlow(initial.toImmutableList())

    val writes = mutableListOf<Pair<String, Boolean>>()
    var clearListCount: Int = 0
    var failure: AppError? = null

    override fun observeLockableApps(): Flow<ImmutableList<LockableApp>> = apps.asStateFlow()

    override fun lockedPackages(): Flow<ImmutableSet<String>> =
        MutableStateFlow(apps.value.filter { it.isLocked }.map { it.packageName }.toImmutableSet())

    override suspend fun setLocked(packageName: String, locked: Boolean): AppResult<Unit> {
        writes += packageName to locked
        failure?.let { return AppResult.Failure(it) }
        // Re-emits, because that is what makes the new list the change notification.
        apps.value = apps.value
            .map { if (it.packageName == packageName) it.copy(isLocked = locked) else it }
            .toImmutableList()
        return AppResult.Success(Unit)
    }

    override suspend fun clearLockList(): AppResult<Unit> {
        clearListCount++
        failure?.let { return AppResult.Failure(it) }
        apps.value = apps.value.map { it.copy(isLocked = false) }.toImmutableList()
        return AppResult.Success(Unit)
    }
}

internal class FakeAppLockSettingsRepository(
    initial: AppLockSettings = AppLockSettings(),
) : AppLockSettingsRepository {

    private val settings = MutableStateFlow(initial)

    val writes = mutableListOf<Pair<String, Boolean>>()
    var failure: AppError? = null

    override fun observeSettings(): Flow<AppLockSettings> = settings.asStateFlow()

    override suspend fun setAppLockEnabled(enabled: Boolean): AppResult<Unit> {
        writes += "enabled" to enabled
        failure?.let { return AppResult.Failure(it) }
        settings.value = settings.value.copy(isAppLockEnabled = enabled)
        return AppResult.Success(Unit)
    }

    override suspend fun setLockNewlyInstalled(enabled: Boolean): AppResult<Unit> {
        writes += "newlyInstalled" to enabled
        failure?.let { return AppResult.Failure(it) }
        settings.value = settings.value.copy(lockNewlyInstalled = enabled)
        return AppResult.Success(Unit)
    }
}

internal class FakeInstalledAppsRepository(
    private val apps: List<InstalledApp> = emptyList(),
) : InstalledAppsRepository {

    override suspend fun installedApps(
        includeWithoutLauncher: Boolean,
    ): AppResult<ImmutableList<InstalledApp>> = AppResult.Success(apps.toImmutableList())

    override suspend fun find(packageName: String): AppResult<InstalledApp?> =
        AppResult.Success(apps.firstOrNull { it.packageName == packageName })
}

internal class FakeForegroundAppMonitor : ForegroundAppMonitor {

    val unlocked = mutableListOf<String>()
    var startCount: Int = 0
    var stopCount: Int = 0

    override fun start() { startCount++ }

    override fun stop() { stopCount++ }

    override fun noteUnlocked(packageName: String) { unlocked += packageName }

    override val lockRequests: Flow<String> = emptyFlow()
}

/** Four rows is enough to show a toggle changing exactly one of them. */
internal fun lockableApps(): List<LockableApp> = persistentListOf(
    LockableApp(packageName = "com.a", label = "Alpha", isLocked = false),
    LockableApp(packageName = "com.b", label = "Beta", isLocked = true),
)

internal fun installedApp(packageName: String, label: String) =
    InstalledApp(packageName = packageName, label = label, uid = 0, apkBytes = 0L)

internal val NoLockedPackages: ImmutableSet<String> = persistentSetOf()
