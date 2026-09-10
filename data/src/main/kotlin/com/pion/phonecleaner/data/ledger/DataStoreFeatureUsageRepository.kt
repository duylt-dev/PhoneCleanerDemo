package com.pion.phonecleaner.data.ledger

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import com.pion.phonecleaner.core.common.time.AppClock
import com.pion.phonecleaner.data.datastore.FeatureUsagePrefs
import com.pion.phonecleaner.domain.catalog.FeatureAvailability
import com.pion.phonecleaner.domain.model.feature.FeatureId
import com.pion.phonecleaner.domain.repository.FeatureUsageRepository
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.IOException
import kotlin.random.Random
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant

/**
 * Twenty timestamps, one key each. Declared once, in `coreDataModule` — **six** cluster designs each
 * declared their own, one of them under a different name, which under Koin's silent override is a
 * load-order coin flip at runtime rather than a compile error (`docs/system-architecture.md` §5.1).
 *
 * It replaces `qd.a` (a Kotlin `object` with a mutable static field) and `ae.o0` (538 lines: 20
 * fields, 20 getters, 20 setters, 20 `updateXAndSave()`). The behaviour being removed is specific:
 * **recording one feature use rewrites all twenty keys with `commit()` on the calling thread** —
 * twenty synchronous fsync-class writes inside the frame budget of a screen transition, on every
 * launch of every feature (`docs/screens/21-shared-models-and-ui.md` §5.4 G1). [markUsed] writes one
 * key, suspending.
 *
 * `qd.a.d()` also returns a nullable static cache — `null` until a priming call has run, and every
 * call site is `d()?.X0()`, so a use is **silently not recorded** whenever the cache was not primed
 * (G2). Koin constructs this before any ViewModel that needs it; [markUsed] cannot no-op.
 *
 * Both [clock] and [random] are constructor arguments for the same reason: the competitor's staleness
 * test reads the system clock through `DateUtils.isToday` and its recommendation shuffles on the
 * global RNG, and **no test can pin either** (§5.3).
 */
internal class DataStoreFeatureUsageRepository(
    private val store: DataStore<Preferences>,
    private val clock: AppClock,
    private val random: Random,
) : FeatureUsageRepository {

    /** One key. Fire-and-forget bookkeeping: the caller wraps it in `launchSafely` and never shows a failure. */
    override suspend fun markUsed(feature: FeatureId) {
        store.edit { prefs ->
            prefs[FeatureUsagePrefs.lastUsedAt(feature)] = clock.now().toEpochMilliseconds()
        }
    }

    /** Null means "never used" — never "used at the epoch". */
    override fun lastUsed(feature: FeatureId): Flow<Instant?> = store.data
        .readSafely()
        .map { prefs -> prefs[FeatureUsagePrefs.lastUsedAt(feature)]?.let(Instant::fromEpochMilliseconds) }
        .distinctUntilChanged()

    /**
     * A feature is stale when it has not been used within [STALE_AFTER] of *now*, and a feature that
     * has never been used is stale.
     *
     * **A rolling window, not a calendar day.** §10.3 U10 records that what "stale" means is unsettled
     * and that this document assumes a daily boundary; G5 records what the competitor's calendar-day
     * version costs — `isToday` makes **every** feature go stale at once at local midnight, and a
     * time-zone change re-rolls the whole set. A rolling window keeps the daily cadence the product
     * asked for without either of those, and it needs no time zone at all. The interval is named
     * here, in one place, so the product can change it in one line if U10 resolves the other way.
     *
     * **A feature this release cannot carry out is never stale**, because "stale" is the input to two
     * suggestion surfaces — the home exit offer and the clean-result recommendations — and suggesting
     * an entry point that is drawn locked is an invitation to a door that does not open
     * (`FeatureAvailability`). It is filtered here rather than at each of the two call sites so a
     * third one cannot be written without it.
     */
    override fun staleFeatures(): Flow<ImmutableList<FeatureId>> = store.data
        .readSafely()
        .map { prefs ->
            val horizon = clock.now() - STALE_AFTER
            FeatureId.entries
                .filter { feature ->
                    if (!FeatureAvailability.isAvailable(feature)) return@filter false
                    val last = prefs[FeatureUsagePrefs.lastUsedAt(feature)]
                    last == null || Instant.fromEpochMilliseconds(last) < horizon
                }
                .toImmutableList()
        }
        .distinctUntilChanged()

    /**
     * A random stale feature, or a random one of the rest when none is stale (§5.3).
     *
     * The fallback set is the openable features, not all twenty: [staleFeatures] already drops the
     * ones `FeatureAvailability` locks, and a fallback that put them back would hand the exit offer
     * exactly the destination the filter above exists to keep out. `ifEmpty` is unreachable while any
     * feature is available and is here so this cannot throw if the last one is ever taken away.
     */
    override suspend fun recommend(): FeatureId {
        val stale = staleFeatures().first()
        if (stale.isNotEmpty()) return stale.random(random)
        val openable = FeatureId.entries.filter(FeatureAvailability::isAvailable)
        return openable.ifEmpty { FeatureId.entries }.random(random)
    }

    /**
     * A corrupt or unreadable preferences file reads as "nothing recorded", which is exactly what an
     * empty store means. Throwing instead would take down whichever screen collected first.
     */
    private fun Flow<Preferences>.readSafely(): Flow<Preferences> =
        catch { cause -> if (cause is IOException) emit(emptyPreferences()) else throw cause }

    private companion object {
        val STALE_AFTER = 1.days
    }
}
