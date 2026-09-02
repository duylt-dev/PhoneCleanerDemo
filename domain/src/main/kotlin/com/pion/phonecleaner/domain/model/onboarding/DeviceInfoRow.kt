package com.pion.phonecleaner.domain.model.onboarding

/**
 * One line of the one-time device check, and its reveal state.
 *
 * Folds the competitor's inner class `Striden(titleResId, valueText, iconResId, appState)`
 * (`AssimssesActivity.java:104`, `:196-198`), whose `appState` is a **`var` mutated in place** and
 * shared between the `steps` list and the adapter's own list — so a row's state changes underneath a
 * list that cannot see the change. Here `status` is a `val` and the reducer produces a new row
 * (`LLM.md` §8).
 *
 * **No `@StringRes`.** The competitor stores `titleResId` on the row model, which puts an Android
 * resource id in the domain layer and makes the row untestable off device
 * (`docs/screens/10-splash-and-onboarding.md:584` delta 7). The composable maps [field] to a string.
 */
data class DeviceInfoRow(
    /** The identity, and the `LazyColumn` key. One row per constant, in enum order. */
    val field: DeviceInfoField,
    val status: StepStatus = StepStatus.Idle,
    /** Null until this row's own probe has run. Never a fallback value pretending to be a reading. */
    val value: DeviceInfoValue? = null,
)

/**
 * The five lines the check reports — `"Device"`, `"System OS version"`, `"Screen Resolution"`,
 * `"Screen Density"`, `"Storage used"`
 * (`docs/reverse-engineering/10-splash-and-onboarding.md:572`).
 *
 * **Five, not six.** The competitor's sixth row is titled *"Initializing engine"* and its value is the
 * literal string `"Complete"` (`AssimssesActivity.java:569-571`) — a row that reports nothing. It is
 * dropped (`docs/screens/10-splash-and-onboarding.md:578` delta 1).
 */
enum class DeviceInfoField {
    Device,
    OsVersion,
    ScreenResolution,
    ScreenDensity,
    StorageUsed,
}

/** Where one row is in the scripted reveal. */
enum class StepStatus {
    Idle,
    Loading,
    Done,
}

/**
 * A row's reading, kept as **numbers wherever it is a number**.
 *
 * The ViewModel never builds user-facing copy (MVI §5), and byte formatting in particular is
 * `ByteFormatter`'s, called from the composable — `LLM.md` §12 keeps it out of Koin and out of the
 * ViewModel for exactly this reason. A `String` here for every row would put "1.2 GB" and its locale
 * into a `:domain` model, which is how the competitor's three byte formatters came to disagree.
 */
sealed interface DeviceInfoValue {

    /** A platform fact that is already a name: a model, an OS version. Not copy, and not translated. */
    data class Text(val text: String) : DeviceInfoValue

    /** Screen size in pixels. The composable arranges them; it does not decide which is which. */
    data class Pixels(val width: Int, val height: Int) : DeviceInfoValue

    data class Dpi(val densityDpi: Int) : DeviceInfoValue

    /** Volume totals in BYTES. Formatted at render, never stored formatted. */
    data class Storage(val usedBytes: Long, val totalBytes: Long) : DeviceInfoValue
}
