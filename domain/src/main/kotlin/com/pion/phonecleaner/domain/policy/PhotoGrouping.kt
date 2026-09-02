package com.pion.phonecleaner.domain.policy

import com.pion.phonecleaner.domain.model.photo.Photo
import com.pion.phonecleaner.domain.model.photo.PhotoGroup
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/**
 * The two label rules this cluster shares, as pure functions over `:domain` types — a unit test with
 * no fakes (`LLM.md` §4).
 *
 * The compressor (§3) and the privacy screen (§5) both bucket by month and both had that loop inside
 * their engine in the competitor (`nd/b.f` and `nd/e`, two `LinkedHashMap<yearMonth, List>` builds
 * that agree by coincidence). One rule, one place.
 *
 * [timeZone] is a parameter, defaulted, so a test can pin it. The competitor's day boundary is a
 * static **GMT** `SimpleDateFormat` (`md.h4`), which is the wrong day for most of the world
 * (`docs/system-architecture.md` §5.10).
 */
object PhotoGrouping {

    /**
     * Month buckets, newest month first, photos inside each bucket newest first.
     *
     * The key and the label are the same `yyyy-MM` string: for a month bucket the label *is* unique,
     * which is exactly what is not true of the similar screen's day label — see [dayLabel].
     */
    fun byMonth(
        photos: List<Photo>,
        timeZone: TimeZone = TimeZone.currentSystemDefault(),
    ): ImmutableList<PhotoGroup> = photos
        .sortedByDescending { it.takenAt }
        .groupBy { monthLabel(it, timeZone) }
        .map { (label, rows) -> PhotoGroup(key = label, label = label, photos = rows.toImmutableList()) }
        .sortedByDescending { it.key }
        .toImmutableList()

    /** `yyyy-MM` in [timeZone]. */
    fun monthLabel(photo: Photo, timeZone: TimeZone = TimeZone.currentSystemDefault()): String {
        // LocalDate.toString() is ISO-8601 `yyyy-MM-dd`, so the month label is its first seven
        // characters. `monthNumber` is deprecated in kotlinx-datetime 0.8.0 and neither `month.value`
        // nor `month.number` resolves on this platform; the ISO form is the stable spelling.
        val date = photo.takenAt.toLocalDateTime(timeZone).date
        return date.toString().substring(0, 7)
    }

    /**
     * `yyyy-MM-dd` in [timeZone] — the header label of a similar group, taken from its opener, which
     * is what the competitor renders (`docs/reverse-engineering/13-photo-and-media.md` §4.1 step 5).
     *
     * It is a **label, not a key**: two unrelated groups shot on the same day carry the same text,
     * and there the competitor has nothing else to tell them apart. `PhotoGroup.key` does.
     */
    fun dayLabel(photo: Photo, timeZone: TimeZone = TimeZone.currentSystemDefault()): String {
        val date = photo.takenAt.toLocalDateTime(timeZone).date
        return date.toString()
    }
}
