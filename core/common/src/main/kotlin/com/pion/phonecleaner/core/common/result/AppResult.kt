package com.pion.phonecleaner.core.common.result

import com.pion.phonecleaner.core.common.error.AppError

/**
 * The result of an operation that is allowed to fail in a way the user can be told about.
 *
 * Every repository returns this rather than throwing, so `launchSafely`'s catch block is a floor
 * for the unexpected — not the normal error path (MVI doc §1).
 */
sealed interface AppResult<out T> {

    data class Success<out T>(val value: T) : AppResult<T>

    data class Failure(val error: AppError) : AppResult<Nothing>

    val isSuccess: Boolean get() = this is Success

    fun getOrNull(): T? = (this as? Success)?.value

    fun errorOrNull(): AppError? = (this as? Failure)?.error
}

inline fun <T, R> AppResult<T>.map(transform: (T) -> R): AppResult<R> = when (this) {
    is AppResult.Success -> AppResult.Success(transform(value))
    is AppResult.Failure -> this
}

inline fun <T, R> AppResult<T>.fold(
    onSuccess: (T) -> R,
    onFailure: (AppError) -> R,
): R = when (this) {
    is AppResult.Success -> onSuccess(value)
    is AppResult.Failure -> onFailure(error)
}

fun <T> T.asSuccess(): AppResult<T> = AppResult.Success(this)

fun AppError.asFailure(): AppResult<Nothing> = AppResult.Failure(this)
