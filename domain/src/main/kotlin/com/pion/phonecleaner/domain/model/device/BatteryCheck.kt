package com.pion.phonecleaner.domain.model.device

/**
 * The seven battery facts this cluster reads. **Ordinal == display order.**
 *
 * One enum drives two screens: the `batteryscan` checklist's steps
 * (`docs/screens/18-device-battery-and-apps.md` §4.1) and the `batteryinfo` grid's cells (§5.3).
 * They are the same facts in the same order, so a metric is added in one place — which is exactly
 * what [CurrentCharge] was: it landed as one enum constant, one label and one `when` arm, and
 * appeared on all three surfaces at once.
 *
 * [CurrentCharge] sits immediately before [Capacity] because the pair reads as one sentence — what
 * is in the pack now, out of what the pack holds.
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
    CurrentCharge,
    Capacity,
    Health,
}
