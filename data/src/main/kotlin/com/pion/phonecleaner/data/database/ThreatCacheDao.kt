package com.pion.phonecleaner.data.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.pion.phonecleaner.data.database.entity.ThreatCacheEntity
import kotlinx.coroutines.flow.Flow

/**
 * Reads and writes `threat_cache`. Declared `single` in `coreDataModule`
 * (`docs/screens/15-antivirus.md:208`), never in `securityDataModule`.
 *
 * The de-duplication and the `score >= 6` filter are **not** here: they are a domain rule about what
 * counts as a finding, and they live in the repository so both screens get the same answer
 * (`docs/screens/15-antivirus.md` §0.4). This DAO stores what it is given.
 *
 * **One hazard the caller owns.** [insertAll] is REPLACE, so re-inserting an md5 that the user had
 * ignored resets [ThreatCacheEntity.isIgnored] to its default. Read [ignoredMd5s] before a rescan and
 * carry the flag forward, or the "ignore" action lasts exactly until the next scan.
 */
@Dao
interface ThreatCacheDao {

    /** Every cached finding, worst first — including ignored ones, for a "show ignored" toggle. */
    @Query("SELECT * FROM threat_cache ORDER BY score DESC, found_at DESC")
    fun observeAll(): Flow<List<ThreatCacheEntity>>

    /** What the result screen renders: the last scan's findings minus the ones the user ignored. */
    @Query("SELECT * FROM threat_cache WHERE is_ignored = 0 ORDER BY score DESC, found_at DESC")
    fun observeActive(): Flow<List<ThreatCacheEntity>>

    /** `ScanRecord.finishedAtEpochMs`. Null when no scan has ever completed — not "scanned at 0". */
    @Query("SELECT MAX(found_at) FROM threat_cache")
    fun observeLastFoundAt(): Flow<Long?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(findings: List<ThreatCacheEntity>)

    /** After a confirmed removal — `SecurityScanRepository.forget(md5)`. */
    @Query("DELETE FROM threat_cache WHERE md5 = :md5")
    suspend fun deleteByMd5(md5: String): Int

    @Query("DELETE FROM threat_cache")
    suspend fun clearAll(): Int

    /** `SecurityScanRepository.ignore(md5)`. Returns 0 when the md5 is not cached. */
    @Query("UPDATE threat_cache SET is_ignored = 1 WHERE md5 = :md5")
    suspend fun markIgnored(md5: String): Int

    /** Read this before a rescan; see the hazard on the interface KDoc. */
    @Query("SELECT md5 FROM threat_cache WHERE is_ignored = 1")
    suspend fun ignoredMd5s(): List<String>
}
