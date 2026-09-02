package com.pion.phonecleaner.domain.model.device

/**
 * The scripted-scan vocabulary (`docs/screens/18-device-battery-and-apps.md` §1).
 *
 * **`ScanPhase` is a retired name**, not an available one (`docs/system-architecture.md` §4.5): three
 * other clusters own incompatible concepts under it and each would be one careless import away from
 * compiling against the wrong one.
 *
 * A step is never mutated in place. The competitor's setter mutates the same objects its adapter's
 * second `ArrayList` holds, which is why it needs `notifyItemChanged` called with exactly the right
 * index; here the list is rebuilt by `copy` and the whole class of desynchronisation disappears.
 */
data class ScanStep(
    val id: BatteryCheck,
    val state: StepState = StepState.Idle,
)

enum class StepState { Idle, Running, Done }
