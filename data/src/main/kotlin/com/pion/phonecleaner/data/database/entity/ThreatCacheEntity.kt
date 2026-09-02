package com.pion.phonecleaner.data.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * `threat_cache` — the second and last table (`docs/system-architecture.md` §7.3; md5 is the PK).
 *
 * It is the fix for *"the list lives only in the finished scan Activity's Intent extra"*: closing the
 * competitor's result screen destroys the findings, and re-checking costs a full cloud round trip
 * (`docs/screens/15-antivirus.md:573`). Here the verdicts are written **before** the navigation
 * Effect fires and `observeLastResult()` is the only read path (same file, §0.4).
 *
 * The columns are the ten fields of the domain model `ThreatVerdict`
 * (`docs/screens/15-antivirus.md:31-45`) plus [foundAtEpochMillis], which carries `ScanRecord
 * .finishedAtEpochMs` (same file, :147). The SDK's own twenty-field, mutable, `Serializable` row type
 * must not leak past the data layer (same file, §0.1), so nothing here is that type.
 *
 * [isIgnored] serves `SecurityScanRepository.ignore(md5)` — *"the action the competitor has no
 * version of"*.
 *
 * UNKNOWN — whether the ignore list belongs on this row or in its own DataStore key.
 * `docs/screens/15-antivirus.md:144` and :514 say only *"an ignore list the repository filters out"*
 * and *"deliberately additive"*; §0.5 puts consent in DataStore but says nothing about ignores, and
 * §0.6 declares a `ScanHistoryStore` without naming its shape. A column is chosen because it is keyed
 * by the same identity as the row it hides and it cannot go out of step with one. If the security
 * cluster wants the other shape, the column is dropped in the same change.
 *
 * Like `hidden_notifications`, this table imports no domain type: the mapping is the security
 * cluster's (`data/security/`), so `:data` builds before that cluster exists.
 */
@Entity(tableName = "threat_cache")
data class ThreatCacheEntity(
    /** Identity. `""` packageName is legal — a loose `.apk` or a test-file hit has no package. */
    @PrimaryKey
    @ColumnInfo(name = "md5")
    val md5: String,
    @ColumnInfo(name = "package_name")
    val packageName: String,
    @ColumnInfo(name = "apk_path")
    val apkPath: String,
    /** `appName ?: packageName ?: file name` — resolved ONCE, in the mapper, never at render. */
    @ColumnInfo(name = "label")
    val label: String,
    /** 0, 1, 6 or 8 in practice. `RiskLevel.of(score)` is the whole risk model, and it is domain. */
    @ColumnInfo(name = "score")
    val score: Int,
    @ColumnInfo(name = "vid")
    val vid: String,
    @ColumnInfo(name = "family_name")
    val familyName: String,
    @ColumnInfo(name = "summary")
    val summary: String,
    @ColumnInfo(name = "category")
    val category: String,
    @ColumnInfo(name = "apk_size_bytes")
    val apkSizeBytes: Long,
    /** Epoch milliseconds of the scan that produced this row — `ScanRecord.finishedAtEpochMs`. */
    @ColumnInfo(name = "found_at")
    val foundAtEpochMillis: Long,
    @ColumnInfo(name = "is_ignored")
    val isIgnored: Boolean = false,
)
