package com.pion.phonecleaner.core.common.error

/**
 * Every way an operation is allowed to fail.
 *
 * Carries no string a user sees: mapping to a `@StringRes` happens at render time in
 * `:core:ui/error/ErrorMessages.kt` (LLM.md §3.5), so one error reads correctly in all 17 locales.
 */
sealed interface AppError {

    /** A runtime or special-access permission the operation needed was not granted. */
    data class PermissionDenied(val permission: String? = null) : AppError

    data object NoNetwork : AppError

    data class NotFound(val what: String? = null) : AppError

    /** Reading, writing or deleting failed at the storage layer. */
    data class Storage(val path: String? = null, val cause: String? = null) : AppError

    /** The gap between what the layer below promised and what it did. Raised by `launchSafely`. */
    data class Unexpected(val message: String? = null) : AppError
}
