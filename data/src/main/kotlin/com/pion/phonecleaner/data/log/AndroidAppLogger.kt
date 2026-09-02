package com.pion.phonecleaner.data.log

import android.util.Log
import com.pion.phonecleaner.core.common.log.AppLogger

/**
 * The ONLY file in the project allowed to import `android.util.Log` (LLM.md §3.6).
 *
 * [enabled] is a plain `@Volatile var` set by direct assignment in `Application.onCreate` BEFORE
 * `startKoin` — never read through DI. A gate read as `by inject(named(...))` throws
 * `IllegalStateException` on the first receiver or SDK callback that beats `startKoin`, on a thread
 * nothing wraps, taking the process down rather than the log line. That exact shape once turned a
 * connected-test suite from 14/14 green to 1/14 (MVI doc §1).
 */
class AndroidAppLogger(private val tag: String = "PhoneCleaner") : AppLogger {

    override fun d(message: () -> String) {
        if (enabled) Log.d(tag, message())
    }

    override fun e(throwable: Throwable?, message: () -> String) {
        if (enabled) Log.e(tag, message(), throwable)
    }

    companion object {
        @Volatile
        @JvmStatic
        var enabled: Boolean = false
    }
}
