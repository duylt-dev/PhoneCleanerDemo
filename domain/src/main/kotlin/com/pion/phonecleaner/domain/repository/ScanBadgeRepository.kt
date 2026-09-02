package com.pion.phonecleaner.domain.repository

import com.pion.phonecleaner.core.common.result.AppResult
import kotlinx.coroutines.flow.Flow

/**
 * The once-a-day red dot on the home Running Apps tile
 * (`docs/screens/18-device-battery-and-apps.md` §1.1, §6.4). Replaces the
 * `flux_running_apps_last_scan_ymd` preference; declared once, in `deviceDataModule` (§8).
 *
 * Two defects are fixed by the port, and both are in the storage, not the display:
 *
 * 1. The competitor's date is a `yyyy-MM-dd` string formatted over a **GMT** calendar, so the badge
 *    clears at 07:00 for a UTC+7 user and at 19:00 the previous day for a UTC−5 one. Here it is the
 *    **local** epoch day, an `Int`. A "today" the user cannot recognise is worse than no badge.
 * 2. The write is a `commit()` **inside the navigation path**, between the ad callback and
 *    `startActivity` — a synchronous disk write on the way to the next screen.
 *    [markScannedToday] is called from its own `launchSafely`; navigation never waits on it.
 */
interface ScanBadgeRepository {

    /** True when today's scan has not happened yet, so the tile should show its dot. */
    fun observeRunningAppsBadge(): Flow<Boolean>

    suspend fun markScannedToday(): AppResult<Unit>
}
