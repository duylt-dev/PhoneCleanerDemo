package com.pion.phonecleaner.domain.model.device

/**
 * The six battery facts this cluster reads. **Ordinal == display order.**
 *
 * One enum drives two screens: the `batteryscan` checklist's six steps
 * (`docs/screens/18-device-battery-and-apps.md` §4.1) and the `batteryinfo` grid's six cells (§5.3).
 * They are the same six things in the same order, so a seventh metric is added in one place.
 *
 * It lives in `:domain` rather than in `BatteryScanContract.kt` where §4.1 writes it, because
 * [ScanStep] — a `:domain` type by §1 — has a `BatteryCheck` field, and `:domain` cannot see a
 * `:feature` package. Nothing else about §4.1 changes.
 *
 * **No `@StringRes`/`@DrawableRes` here.** The competitor stores `nameResId` and `iconResId` on the
 * step model, putting `R` references into data; the label and the glyph are resolved at render time.
 */
enum class BatteryCheck {
    Brightness,
    Temperature,
    Voltage,
    Technology,
    Capacity,
    Health,
}
