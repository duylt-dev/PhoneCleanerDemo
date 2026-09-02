package com.pion.phonecleaner.data.di

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import com.pion.phonecleaner.core.common.concurrent.DefaultDispatcherProvider
import com.pion.phonecleaner.core.common.concurrent.DispatcherProvider
import com.pion.phonecleaner.core.common.log.AppLogger
import com.pion.phonecleaner.core.common.policy.MinimumDuration
import com.pion.phonecleaner.core.common.time.AppClock
import com.pion.phonecleaner.core.common.time.SystemAppClock
import com.pion.phonecleaner.data.datastore.appDataStore
import com.pion.phonecleaner.data.log.AndroidAppLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import org.koin.android.ext.koin.androidContext
import org.koin.core.qualifier.named
import org.koin.dsl.module
import kotlin.time.Duration.Companion.seconds

/**
 * `coreModule` — the shared platform bindings. LLM.md §6.5 gives each of these exactly one
 * declaring module; two `single`s of one type are a silent override, not an error.
 *
 * It lives in `:data` and not in `:core:common` because `AndroidAppLogger` is an Android type and
 * `:core:common` is a `kotlin("jvm")` module with no Android on its classpath.
 */
val coreModule = module {
    single<DispatcherProvider> { DefaultDispatcherProvider() }
    single<AppLogger> { AndroidAppLogger() }
    single<AppClock> { SystemAppClock }
    single { MinimumDuration(MINIMUM_VISIBLE_SCAN) }
    single(named(APP_SCOPE)) { CoroutineScope(SupervisorJob() + get<DispatcherProvider>().default) }

    /**
     * The ONE `DataStore<Preferences>` in the process. `appDataStore` is a
     * `preferencesDataStore` property delegate, so a second declaration of this type — here or in
     * any cluster module — is either an `IllegalStateException` on the same file name or a second
     * store on a different one, which is precisely the split `od.d0` + `od.g0` already are.
     *
     * It replaces both of those: two `SharedPreferences` files whose every write is `commit()` on
     * the calling thread (`java/od/d0.java:78-81`).
     */
    single<DataStore<Preferences>> { androidContext().appDataStore }
}

const val APP_SCOPE = "appScope"

/**
 * How long a scan stays on screen even when it finishes instantly, so the result does not flash.
 * A value, not a magic number at a call site, because every tool must agree on it.
 */
private val MINIMUM_VISIBLE_SCAN = 2.seconds
