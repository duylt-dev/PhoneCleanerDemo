package com.pion.phonecleaner.core.common.time

import kotlin.time.Clock

/**
 * Injected so a test can pin "now". Bound to [Clock.System] in `coreModule`.
 *
 * `kotlin.time.Clock` is still `@ExperimentalTime` in Kotlin 2.2, and a typealias propagates that:
 * any module injecting `AppClock` must add
 * `kotlin { compilerOptions { optIn.add("kotlin.time.ExperimentalTime") } }` to its build file.
 * Today only `:data` does, because that is where the binding is declared.
 *
 * The frequency quota and the GMT day roll described in
 * `docs/reverse-engineering/03-out-of-app-behaviour.md` are untestable against a hard-coded system clock.
 */
typealias AppClock = Clock

val SystemAppClock: AppClock get() = Clock.System
