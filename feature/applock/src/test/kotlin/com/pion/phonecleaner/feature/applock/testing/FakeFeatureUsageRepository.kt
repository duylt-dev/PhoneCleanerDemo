package com.pion.phonecleaner.feature.applock.testing

import com.pion.phonecleaner.domain.model.feature.FeatureId
import com.pion.phonecleaner.domain.repository.FeatureUsageRepository
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlin.time.Instant

/**
 * Records which feature was stamped, and nothing else.
 *
 * It exists as its own file rather than inside `AppLockFakes.kt` because that file is already at the
 * size rule's edge, and because [marked] carries the one assertion that matters here: the App Lock
 * home must stamp `FeatureId.AppLock`. The competitor stamps the **Permission Manager's** key from
 * this screen, so two features share one "last used" timestamp and the home screen's staleness
 * recommendation is wrong for both (`docs/screens/16-app-lock.md` §1.5).
 */
internal class FakeFeatureUsageRepository : FeatureUsageRepository {

    val marked = mutableListOf<FeatureId>()

    override suspend fun markUsed(feature: FeatureId) {
        marked += feature
    }

    override fun lastUsed(feature: FeatureId): Flow<Instant?> = MutableStateFlow(null)

    override fun staleFeatures(): Flow<ImmutableList<FeatureId>> =
        MutableStateFlow(persistentListOf())

    override suspend fun recommend(): FeatureId = FeatureId.AppLock
}
