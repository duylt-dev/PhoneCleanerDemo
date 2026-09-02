package com.pion.phonecleaner.domain.model.applock

/**
 * How many digits an App Lock PIN has (`docs/screens/16-app-lock.md` §0: *"`PIN_LENGTH = 4` is a
 * `const val` in `:domain`, shared by the two PIN surfaces"*).
 *
 * It lives here rather than beside the keypad because **three layers have to agree on it**: the two
 * PIN ViewModels decide when a buffer is complete, and `AppLockPinRepository` refuses to store
 * anything of another length. `:core:ui`'s `DefaultPinLength` is the *component's* default and says
 * nothing about the domain rule; a screen passes this value in.
 *
 * Four digits is a 10 000-entry space, which is why the lockout in `PinLockout` is not optional:
 * the competitor has neither a digest nor an attempt counter, so its PIN space is exhaustible in
 * minutes (`docs/reverse-engineering/16-app-lock.md` §3.2).
 */
const val PIN_LENGTH: Int = 4
