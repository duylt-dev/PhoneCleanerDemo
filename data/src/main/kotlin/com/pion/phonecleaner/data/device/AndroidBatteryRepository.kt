package com.pion.phonecleaner.data.device

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.database.ContentObserver
import android.os.BatteryManager
import android.provider.Settings
import androidx.core.content.ContextCompat
import com.pion.phonecleaner.core.common.concurrent.DispatcherProvider
import com.pion.phonecleaner.domain.model.device.BatterySnapshot
import com.pion.phonecleaner.domain.repository.BatteryRepository
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlin.time.Duration.Companion.milliseconds

/**
 * One `callbackFlow` over the three sources the competitor wires by hand
 * (`docs/screens/18-device-battery-and-apps.md` §5.2): the sticky `ACTION_BATTERY_CHANGED` broadcast
 * and the two `Settings.System` brightness keys.
 *
 * **Registration and teardown are one expression**, so there is no path where a receiver outlives the
 * screen. `HospgraActivity` registers in `onResume`, unregisters in `onPause`, and wraps both
 * `unregister` calls in bare `try`/`catch (Exception)` blocks — the `catch` exists because the
 * pairing is not guaranteed by the lifecycle it is written against.
 *
 * **`RECEIVER_NOT_EXPORTED` is passed explicitly**, and it is `4` (`RECEIVER_EXPORTED` is `2` — one
 * research report has the two inverted). `ACTION_BATTERY_CHANGED` is a protected system broadcast, so
 * the competitor's omission is tolerated today *(inferred, medium-high — not tested on an API-34
 * device)*; it is a latent crash the day a second filter action is added.
 *
 * `.conflate()` replaces the hand-rolled `removeCallbacks`/`post` throttle, and
 * `.distinctUntilChanged()` is what stops the ~1 Hz charging broadcast from costing six `setText`s, a
 * `SpannableStringBuilder` and a `String.format` per second.
 *
 * The collector bounds the lifetime: `collectAsStateWithLifecycle()` stops at `ON_STOP`, which is
 * what the competitor's `onResume`/`onPause` pair achieves — without two lifecycle overrides.
 */
internal class AndroidBatteryRepository(
    context: Context,
    private val dispatchers: DispatcherProvider,
) : BatteryRepository {

    private val appContext = context.applicationContext

    override fun observe(): Flow<BatterySnapshot> = callbackFlow {
        val emit = { trySend(readSnapshot()) }

        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                emit()
            }
        }
        val brightnessObserver = object : ContentObserver(null) {
            override fun onChange(selfChange: Boolean) {
                emit()
            }
        }

        ContextCompat.registerReceiver(
            appContext,
            receiver,
            IntentFilter(Intent.ACTION_BATTERY_CHANGED),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        BRIGHTNESS_KEYS.forEach { key ->
            appContext.contentResolver.registerContentObserver(
                Settings.System.getUriFor(key),
                false,
                brightnessObserver,
            )
        }

        // The sticky broadcast, read once up front, so the first frame is not empty.
        emit()

        awaitClose {
            runCatching { appContext.unregisterReceiver(receiver) }
            runCatching { appContext.contentResolver.unregisterContentObserver(brightnessObserver) }
        }
    }
        .conflate()
        .distinctUntilChanged()
        .flowOn(dispatchers.default)

    /**
     * Reads the sticky broadcast directly rather than trusting the `Intent` the receiver was handed:
     * the brightness observer fires with no battery `Intent` at all, and both paths must produce a
     * complete snapshot.
     */
    private fun readSnapshot(): BatterySnapshot {
        val status = appContext.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val percent = batteryPercent(
            level = status?.batteryInt(BatteryManager.EXTRA_LEVEL) ?: ABSENT,
            scale = status?.batteryInt(BatteryManager.EXTRA_SCALE, DEFAULT_SCALE) ?: DEFAULT_SCALE,
        )
        val rawTechnology = status?.getStringExtra(BatteryManager.EXTRA_TECHNOLOGY).orEmpty()
        val state = chargeState(status?.batteryInt(BatteryManager.EXTRA_STATUS) ?: ABSENT)
        return BatterySnapshot(
            percent = percent,
            voltageMillivolts = (status?.batteryInt(BatteryManager.EXTRA_VOLTAGE) ?: ABSENT)
                .coerceAtLeast(0),
            temperatureCelsius = temperatureCelsius(
                status?.batteryInt(BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0,
            ),
            health = batteryHealth(status?.batteryInt(BatteryManager.EXTRA_HEALTH) ?: ABSENT),
            technology = batteryTechnology(rawTechnology),
            rawTechnology = rawTechnology,
            chargeState = state,
            capacityMah = BatteryCapacityReader.designCapacityMah(appContext),
            brightnessPercent = ScreenBrightnessReader.percent(appContext),
            chargeTimeRemaining = chargeTimeRemaining(state),
        )
    }

    /**
     * Charging: `BatteryManager.computeChargeTimeRemaining()` (API 28+, and `minSdk` is 28), which
     * returns `-1` when the platform cannot compute it. That `-1` becomes `null`.
     *
     * Discharging: **`null`, deliberately.** The competitor answers `percent × 480 / 100` minutes —
     * a flat eight-hours-at-full assumption whose only input is the percentage, so it *is* the
     * percentage in another unit. (Its charging fallback below API 28 is the mirror form,
     * `(100 − percent) × 1.2`; the two are easy to transpose and one research report did.)
     *
     * > UNKNOWN — whether a discharge estimate ships at all is open question 5 of
     * > `docs/screens/18-device-battery-and-apps.md` §9, which calls it "not a design question — a
     * > product one". §5.5 names the only honest source, a `BATTERY_PROPERTY_CURRENT_NOW` +
     * > `CHARGE_COUNTER` integral, and no report states the sign convention `CURRENT_NOW` uses on
     * > this app's target devices — OEMs differ on whether discharge is negative. A row that renders
     * > "Not available" is the conservative branch; a number derived from an unverified sign is not.
     */
    private fun chargeTimeRemaining(state: com.pion.phonecleaner.domain.model.device.ChargeState) =
        when (state) {
            com.pion.phonecleaner.domain.model.device.ChargeState.CHARGING ->
                appContext.getSystemService(BatteryManager::class.java)
                    ?.computeChargeTimeRemaining()
                    ?.takeIf { it > 0L }
                    ?.milliseconds

            else -> null
        }

    private companion object {
        /** The platform's nominal denominator; a non-positive one means "not reported". */
        const val DEFAULT_SCALE = 100

        val BRIGHTNESS_KEYS = listOf(
            Settings.System.SCREEN_BRIGHTNESS,
            Settings.System.SCREEN_BRIGHTNESS_MODE,
        )
    }
}
