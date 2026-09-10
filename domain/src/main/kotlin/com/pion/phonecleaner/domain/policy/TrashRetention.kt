package com.pion.phonecleaner.domain.policy

import com.pion.phonecleaner.domain.model.trash.TrashEntry
import kotlin.math.ceil
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant

/**
 * The retention window, and nothing else. A pure rule over domain types is a unit test with no fakes
 * (`LLM.md` §4).
 *
 * **Two days, not thirty** (owner decision D3). A cleaner is used precisely when a device is short of
 * space; a bin that holds a 4 GB junk sweep for a month gives back none of it and turns the tool into
 * the problem. Two days is long enough to notice a mistake the same evening or the next.
 */
object TrashRetention {

    /** The one place 2 days is written. Every call site names this, never a literal `Duration`. */
    val WINDOW: Duration = 2.days

    /** Stored on [TrashEntry.expiresAt] at move time — see that class's KDoc for why it is stored. */
    fun expiresAt(trashedAt: Instant): Instant = trashedAt + WINDOW

    fun isExpired(entry: TrashEntry, now: Instant): Boolean = now >= entry.expiresAt

    /**
     * Whole days remaining, rounded **up**, floored at 0. Rounded up so an entry with nine hours left
     * reads "1 day left" rather than "0 days left" while it is still restorable — a countdown that
     * says zero on something the user can still recover is the reading that costs a file.
     */
    fun daysLeft(entry: TrashEntry, now: Instant): Int {
        val remaining = entry.expiresAt - now
        if (remaining <= Duration.ZERO) return 0
        return ceil(remaining / 1.days).toInt()
    }
}
