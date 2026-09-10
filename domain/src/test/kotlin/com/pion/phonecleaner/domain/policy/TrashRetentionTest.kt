package com.pion.phonecleaner.domain.policy

import com.pion.phonecleaner.domain.model.trash.TrashEntry
import com.pion.phonecleaner.domain.model.trash.TrashEntryKind
import com.pion.phonecleaner.domain.model.feature.FeatureId
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A pure function test with no fakes — every assertion is over domain types only.
 *
 * **Two days, not thirty** (owner decision D3). A cleaner is used precisely when a device is short of
 * space; a bin that holds a 4 GB junk sweep for a month gives back none of it and turns the tool into
 * the problem. Two days is long enough to notice a mistake the same evening or the next.
 */
class TrashRetentionTest {

    private val now = Instant.fromEpochSeconds(1000)
    private val trashedAt = now - TrashRetention.WINDOW

    @Test
    fun `an entry expires exactly 2 days after it was trashed`() {
        val entry = TrashEntry(
            id = "1",
            originalPath = "/path/file.txt",
            trashedPath = "/trash/file.txt",
            displayName = "file.txt",
            sizeBytes = 1000L,
            fileCount = 1,
            kind = TrashEntryKind.File,
            source = FeatureId.JunkClean,
            trashedAt = trashedAt,
            expiresAt = TrashRetention.expiresAt(trashedAt),
        )

        assertTrue("entry should be expired at the exact deadline", TrashRetention.isExpired(entry, now))
    }

    @Test
    fun `one millisecond before the deadline is not expired`() {
        val entry = TrashEntry(
            id = "1",
            originalPath = "/path/file.txt",
            trashedPath = "/trash/file.txt",
            displayName = "file.txt",
            sizeBytes = 1000L,
            fileCount = 1,
            kind = TrashEntryKind.File,
            source = FeatureId.JunkClean,
            trashedAt = trashedAt,
            expiresAt = TrashRetention.expiresAt(trashedAt),
        )

        val oneMilliBeforeDeadline = now - 1.milliseconds
        assertFalse(
            "entry should not be expired one millisecond before deadline",
            TrashRetention.isExpired(entry, oneMilliBeforeDeadline),
        )
    }

    @Test
    fun `exactly at the deadline IS expired`() {
        val entry = TrashEntry(
            id = "1",
            originalPath = "/path/file.txt",
            trashedPath = "/trash/file.txt",
            displayName = "file.txt",
            sizeBytes = 1000L,
            fileCount = 1,
            kind = TrashEntryKind.File,
            source = FeatureId.JunkClean,
            trashedAt = trashedAt,
            expiresAt = TrashRetention.expiresAt(trashedAt),
        )

        assertTrue("isExpired uses >=, so exactly at deadline IS expired", TrashRetention.isExpired(entry, now))
    }

    @Test
    fun `daysLeft rounds UP - 9 hours remaining reads 1 not 0`() {
        val entry = TrashEntry(
            id = "1",
            originalPath = "/path/file.txt",
            trashedPath = "/trash/file.txt",
            displayName = "file.txt",
            sizeBytes = 1000L,
            fileCount = 1,
            kind = TrashEntryKind.File,
            source = FeatureId.JunkClean,
            trashedAt = trashedAt,
            expiresAt = TrashRetention.expiresAt(trashedAt),
        )

        val timeWithNineHoursRemaining = now - 15.hours
        val daysLeft = TrashRetention.daysLeft(entry, timeWithNineHoursRemaining)

        assertEquals(
            "with 9 hours remaining, daysLeft should round UP to 1, not down to 0",
            1,
            daysLeft,
        )
    }

    @Test
    fun `daysLeft floors at 0 and never goes negative`() {
        val entry = TrashEntry(
            id = "1",
            originalPath = "/path/file.txt",
            trashedPath = "/trash/file.txt",
            displayName = "file.txt",
            sizeBytes = 1000L,
            fileCount = 1,
            kind = TrashEntryKind.File,
            source = FeatureId.JunkClean,
            trashedAt = trashedAt,
            expiresAt = TrashRetention.expiresAt(trashedAt),
        )

        val timeAfterExpiry = now + 1.days
        val daysLeft = TrashRetention.daysLeft(entry, timeAfterExpiry)

        assertEquals("after expiry, daysLeft should floor at 0", 0, daysLeft)
    }
}
