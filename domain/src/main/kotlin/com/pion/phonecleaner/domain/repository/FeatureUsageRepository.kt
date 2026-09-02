package com.pion.phonecleaner.domain.repository

import com.pion.phonecleaner.domain.model.feature.FeatureId
import kotlinx.collections.immutable.ImmutableList
import kotlinx.coroutines.flow.Flow
import kotlin.time.Instant

/**
 * When each of the twenty features was last used.
 *
 * ONE binding, in `coreDataModule`, implemented by `DataStoreFeatureUsageRepository(dataStore, clock,
 * random)`. **Six independently written cluster designs each declared their own `single` for this** —
 * the highest-risk collision in the corpus, and one Koin resolves by silently taking whichever module
 * loaded last (`docs/system-architecture.md` §5.4, `LLM.md` §6.4).
 *
 * `clock` and `random` are constructor arguments of the implementation for the same reason: the
 * competitor's staleness test reads the system clock through `DateUtils.isToday` and its
 * recommendation shuffles the global RNG, and no test can pin either
 * (`docs/screens/21-shared-models-and-ui.md:5.3`).
 *
 * [markUsed] writes **one** key. The competitor rewrites all twenty with `commit()` on the calling
 * thread on every feature launch — 20 synchronous writes plus an fsync inside the frame budget of a
 * screen transition (delta G1, same section).
 */
interface FeatureUsageRepository {

    /** Records one use. Writes ONE key. Never called on the main thread. */
    suspend fun markUsed(feature: FeatureId)

    fun lastUsed(feature: FeatureId): Flow<Instant?>

    /**
     * Features not used since the configured day boundary.
     *
     * PENDING PRODUCT DECISION — `docs/screens/21-shared-models-and-ui.md` delta G5 records that the
     * competitor's calendar-day test makes every feature stale at once at local midnight, and that a
     * daily reset is "a product decision wearing engineering clothes". The boundary is therefore the
     * implementation's injected `clock`, explicit and testable; nothing here fixes its length.
     */
    fun staleFeatures(): Flow<ImmutableList<FeatureId>>

    /** A random stale feature, or a random one of all twenty if none is stale. */
    suspend fun recommend(): FeatureId
}
