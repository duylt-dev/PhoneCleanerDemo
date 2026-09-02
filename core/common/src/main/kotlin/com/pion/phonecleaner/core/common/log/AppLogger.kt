package com.pion.phonecleaner.core.common.log

/**
 * The logging port. One method, no Android type in the signature.
 *
 * `android.util.Log` is never called directly from a ViewModel: `android.jar` on the unit-test
 * classpath is a stub and `Log.e()` throws `RuntimeException: ... not mocked` the first time a real
 * error reaches `launchSafely`'s catch block (MVI doc §1). The Android adapter is
 * `:data/log/AndroidAppLogger.kt`, the only file in the project allowed that import.
 *
 * [NoOp] is the default so an [com.pion.phonecleaner.core.mvi.MviViewModel] subclass constructs on a
 * bare JVM with no test double. That default is what makes the catch branch testable at all.
 */
interface AppLogger {

    fun d(message: () -> String)

    fun e(throwable: Throwable? = null, message: () -> String)

    companion object {
        val NoOp: AppLogger = object : AppLogger {
            override fun d(message: () -> String) = Unit
            override fun e(throwable: Throwable?, message: () -> String) = Unit
        }
    }
}
