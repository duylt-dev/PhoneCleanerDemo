package com.pion.phonecleaner.domain.model.trash

/** What the Settings row and the home tile badge need, without loading the list. */
data class TrashSummary(val entryCount: Int = 0, val totalBytes: Long = 0L) {
    val isEmpty: Boolean get() = entryCount == 0
}
