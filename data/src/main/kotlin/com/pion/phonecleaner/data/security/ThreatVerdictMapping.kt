package com.pion.phonecleaner.data.security

import com.pion.phonecleaner.data.database.entity.ThreatCacheEntity
import com.pion.phonecleaner.domain.model.security.ThreatVerdict

/**
 * `threat_cache` row ↔ [ThreatVerdict]. The security cluster's mapping, in the security cluster's
 * package: `ThreatCacheEntity`'s own KDoc says the table imports no domain type precisely so that
 * `:data` builds before this cluster exists.
 *
 * The entity carries two columns the domain model does not: `found_at`, which becomes
 * `ScanRecord.finishedAtEpochMs` for the whole record, and `is_ignored`, which is a *filter* — an
 * ignored row is not a finding, so it never reaches the model at all.
 */
internal fun ThreatCacheEntity.toVerdict(): ThreatVerdict = ThreatVerdict(
    md5 = md5,
    packageName = packageName,
    apkPath = apkPath,
    label = label,
    score = score,
    vid = vid,
    familyName = familyName,
    summary = summary,
    category = category,
    apkSizeBytes = apkSizeBytes,
)

internal fun ThreatVerdict.toEntity(
    foundAtEpochMillis: Long,
    isIgnored: Boolean,
): ThreatCacheEntity = ThreatCacheEntity(
    md5 = md5,
    packageName = packageName,
    apkPath = apkPath,
    label = label,
    score = score,
    vid = vid,
    familyName = familyName,
    summary = summary,
    category = category,
    apkSizeBytes = apkSizeBytes,
    foundAtEpochMillis = foundAtEpochMillis,
    isIgnored = isIgnored,
)
