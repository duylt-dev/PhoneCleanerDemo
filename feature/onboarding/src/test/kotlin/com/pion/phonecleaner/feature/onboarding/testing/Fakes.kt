package com.pion.phonecleaner.feature.onboarding.testing

import com.pion.phonecleaner.core.common.error.AppError
import com.pion.phonecleaner.core.common.result.AppResult
import com.pion.phonecleaner.domain.model.feature.FeatureId
import com.pion.phonecleaner.domain.model.onboarding.ConsentHost
import com.pion.phonecleaner.domain.model.onboarding.ConsentOutcome
import com.pion.phonecleaner.domain.model.onboarding.ConsentStatus
import com.pion.phonecleaner.domain.model.onboarding.DeviceCheckProbe
import com.pion.phonecleaner.domain.model.onboarding.DeviceInfoField
import com.pion.phonecleaner.domain.model.onboarding.DeviceInfoValue
import com.pion.phonecleaner.domain.model.onboarding.OnboardingState
import com.pion.phonecleaner.domain.model.permission.AppPermission
import com.pion.phonecleaner.domain.repository.ConsentRepository
import com.pion.phonecleaner.domain.repository.OnboardingStateRepository
import com.pion.phonecleaner.domain.repository.PermissionRepository
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.collections.immutable.toImmutableSet
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Hand-written fakes — **no mocking library** (`LLM.md` §9): a fake you can read beats a mock you
 * have to decode, and every fixture in this corpus is one.
 */
internal class FakeOnboardingStateRepository(
    initial: OnboardingState = OnboardingState(),
) : OnboardingStateRepository {

    private val state = MutableStateFlow(initial)

    /** What was written, in order, so a test can assert the write happened at all. */
    val writes = mutableListOf<String>()

    override fun observe(): Flow<OnboardingState> = state.asStateFlow()

    override suspend fun markFirstRunCompleted() {
        writes += "firstRun"
        state.value = state.value.copy(hasCompletedFirstRun = true)
    }

    override suspend fun markTermsAccepted() {
        writes += "terms"
        state.value = state.value.copy(hasAcceptedTerms = true)
    }

    override suspend fun markDeviceCheckSeen() {
        writes += "deviceCheck"
        state.value = state.value.copy(hasSeenDeviceCheck = true)
    }

    override suspend fun markNotificationAskAnswered() {
        writes += "notificationAsk"
        state.value = state.value.copy(hasAnsweredNotificationAsk = true)
    }
}

internal class FakeConsentRepository(
    private val outcome: ConsentOutcome = ConsentOutcome.NotRequired,
) : ConsentRepository {

    var requests: Int = 0
        private set

    private val status = MutableStateFlow(
        ConsentStatus(outcome, canRequestAds = false, purposeConsents = persistentSetOf()),
    )

    override suspend fun requestConsent(host: ConsentHost): AppResult<ConsentStatus> {
        requests++
        return AppResult.Success(status.value)
    }

    override fun observeStatus(): Flow<ConsentStatus> = status.asStateFlow()
}

internal class FakePermissionRepository(
    private val granted: Set<AppPermission> = emptySet(),
) : PermissionRepository {
    override fun observe(): Flow<ImmutableSet<AppPermission>> =
        MutableStateFlow(granted.toImmutableSet())

    override fun isGranted(permission: AppPermission): Boolean = permission in granted

    override fun missingFor(feature: FeatureId): ImmutableSet<AppPermission> = persistentSetOf()
}

/** [throwOn] is the third path `AppResult` does not cover: `launchSafely`'s catch branch. */
internal class FakeDeviceCheckProbe(
    private val failOn: Set<DeviceInfoField> = emptySet(),
    private val throwOn: Set<DeviceInfoField> = emptySet(),
) : DeviceCheckProbe {

    val reads = mutableListOf<DeviceInfoField>()

    override suspend fun read(field: DeviceInfoField): AppResult<DeviceInfoValue> {
        reads += field
        if (field in throwOn) error("probe exploded")
        if (field in failOn) return AppResult.Failure(AppError.Storage(cause = "unreadable"))
        return AppResult.Success(DeviceInfoValue.Text(field.name))
    }
}

internal class FakeConsentHost : ConsentHost {
    override val platformHost: Any = Unit
}
