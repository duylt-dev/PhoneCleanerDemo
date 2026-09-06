package com.pion.phonecleaner.data.storage

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Environment
import android.os.storage.StorageManager
import androidx.core.content.ContextCompat
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import com.pion.phonecleaner.core.common.concurrent.DispatcherProvider
import com.pion.phonecleaner.core.common.result.AppResult
import com.pion.phonecleaner.core.common.result.asSuccess
import com.pion.phonecleaner.data.datastore.StorageAccessPrefs
import com.pion.phonecleaner.domain.repository.StorageRootProvider
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.File

/**
 * **The only thing in the app that knows which roots are readable** (`docs/system-architecture.md`
 * §8.4). A scanner asks it; a scanner never calls `Environment.getExternalStorageDirectory()`.
 *
 * That one rule is what makes the storage branch switchable: when the branch changes, this class and
 * `storageDataModule` change, and no screen, ViewModel or use case does. The competitor's equivalent
 * is `xc.z` — the storage root cached in a static for the process lifetime, with a hard-coded
 * `/storage/emmc/` override, so a removable SD card is never scanned
 * (`docs/screens/12-junk-cleaning.md:404`).
 *
 * `MANAGE_EXTERNAL_STORAGE` **is** declared as of the owner decision of 2026-09-06 (the reasoning is
 * in `data/src/main/AndroidManifest.xml`, beside the declaration). It is still not *assumed*: the
 * grant is checked, never presumed, and what this returns without it is exactly what it returned
 * before — our own directories (strategy 1, no permission at all) plus every SAF tree the user has
 * granted (strategy 4). Media is reached through `MediaStoreRepository` and is not a *root*, so it
 * appears in [coveredSurfaces] and not in [readableRoots].
 */
internal class AndroidStorageRootProvider(
    private val context: Context,
    private val store: DataStore<Preferences>,
    private val dispatchers: DispatcherProvider,
) : StorageRootProvider {

    /**
     * Filesystem paths and `content://` tree URIs mixed in one list, because
     * `StorageScanner.walk(config)` takes one list of roots. `DefaultStorageScanner` splits them on
     * the `content://` prefix — an absolute path can never start with it.
     */
    override suspend fun readableRoots(): AppResult<ImmutableList<String>> =
        withContext(dispatchers.io) {
            buildList {
                addAll(ownDirectories())
                if (hasFullVolumeAccess()) addAll(volumePaths())
                addAll(validPersistedTreeUris())
            }.distinct().toImmutableList().asSuccess()
        }

    /**
     * What a scan actually covered, so a result screen can say so rather than implying "everything
     * scanned" (§8.4, consequence 1). The competitor's file scan *"silently degrades to installed
     * packages only and reports nothing about it"* (§8.2) — this list is the difference.
     *
     * UNKNOWN — the token format. Nothing in the corpus fixes it; looked in system-architecture §8.4,
     * §8.3 and §4.5 and in all twelve `docs/screens/` appendices, none of which names a
     * covered-surface string. These are stable machine identifiers, **not user-facing copy**:
     * `:core:ui` maps them to a localised string the way `ErrorMessages.kt` maps `AppError`.
     * Rendering one raw would put an untranslated identifier on screen.
     */
    override suspend fun coveredSurfaces(): AppResult<ImmutableList<String>> =
        withContext(dispatchers.io) {
            buildList {
                add(SURFACE_MEDIA_IMAGES)
                add(SURFACE_MEDIA_VIDEO)
                add(SURFACE_MEDIA_AUDIO)
                add(SURFACE_APP_CACHE)
                if (hasFullVolumeAccess()) add(SURFACE_ALL_VOLUMES)
                validPersistedTreeUris().forEach { add("$SURFACE_TREE_PREFIX$it") }
            }.toImmutableList().asSuccess()
        }

    /**
     * Records a tree the user just granted through `ACTION_OPEN_DOCUMENT_TREE`. The caller takes the
     * persistable permission first; this only remembers the URI.
     *
     * UNKNOWN — the `:domain` port that lets a Route reach this. §8.4 states that persisted grants
     * live in DataStore and are re-validated on read, but names no repository method for adding one,
     * and `StorageRootProvider` has exactly two methods. Looked in system-architecture §8.3/§8.4,
     * §4.4's three permission ports and `docs/screens/14` §WhatsApp. Kept `internal` rather than
     * inventing a port, because a port is a decision the storage branch owns.
     */
    internal suspend fun rememberTree(treeUri: String) {
        store.edit { prefs ->
            val current = prefs[StorageAccessPrefs.PERSISTED_TREE_URIS].orEmpty()
            prefs[StorageAccessPrefs.PERSISTED_TREE_URIS] = current + treeUri
        }
    }

    /**
     * Re-validated on every read (§8.4): a `takePersistableUriPermission` grant survives a reboot but
     * not an uninstall and not a user revocation. A revoked tree is dropped from the store here, so a
     * scanner never walks a URI that will throw — and the caller gets a shorter covered-surface list
     * rather than an empty result that looks like success (§7.6).
     */
    private suspend fun validPersistedTreeUris(): List<String> {
        val stored = store.data.first()[StorageAccessPrefs.PERSISTED_TREE_URIS].orEmpty()
        if (stored.isEmpty()) return emptyList()
        val live = context.contentResolver.persistedUriPermissions
            .filter { it.isReadPermission }
            .mapTo(HashSet()) { it.uri.toString() }
        val valid = stored.filterTo(LinkedHashSet()) { it in live }
        if (valid.size != stored.size) {
            store.edit { it[StorageAccessPrefs.PERSISTED_TREE_URIS] = valid }
        }
        return valid.toList()
    }

    /** Strategy 1: always available, always cleanable, needs no permission (§8.3). */
    private fun ownDirectories(): List<String> =
        listOfNotNull(context.cacheDir, context.externalCacheDir, context.filesDir)
            .map(File::getAbsolutePath)

    /**
     * Every mounted volume. `StorageManager` is what makes a removable card a root as well as
     * internal storage — the competitor caches `Environment.getExternalStorageDirectory()` in a
     * static forever and never scans an SD card at all (`java/xc/z.java:20-29`).
     *
     * `StorageVolume.directory` is API 30, so below it the single-volume call is the only answer
     * there is; it is also the fallback when the enumeration throws on an odd vendor ROM.
     */
    private fun volumePaths(): List<String> {
        val manager = context.getSystemService(StorageManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && manager != null) {
            runCatching { manager.storageVolumes.mapNotNull { it.directory?.absolutePath } }
                .getOrNull()
                ?.takeIf { it.isNotEmpty() }
                ?.let { return it }
        }
        return listOfNotNull(Environment.getExternalStorageDirectory()?.absolutePath)
    }

    /**
     * Whether the shared volumes are readable — **three platform answers, not one**, and the junk
     * scan's `StorageAccessGate` asks the identical question so the gate and the roots can never
     * disagree about what a scan will see.
     *
     *  * **API 30+** — `MANAGE_EXTERNAL_STORAGE`, given on a Settings page. `Android/data` and
     *    `Android/obb` stay unreadable even so (`gioi-han-android-phone-cleaner` §1).
     *  * **API 28** — `READ_EXTERNAL_STORAGE` is genuinely full read access: API 28 predates scoped
     *    storage, which is why `minSdk` staying at 28 costs nothing here. Before 2026-09-06 this
     *    method answered `false` on 28 outright, so a legacy device got the empty-roots branch even
     *    holding a grant that covered the whole volume.
     *  * **API 29** — `false`, and deliberately. Scoped storage is enforced with `targetSdk` 36 and
     *    `WRITE_EXTERNAL_STORAGE` is capped at 28 in the manifest, so there is no all-files access to
     *    hold. A scan there covers media and our own directories, and [coveredSurfaces] says so.
     */
    private fun hasFullVolumeAccess(): Boolean = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.R -> Environment.isExternalStorageManager()
        Build.VERSION.SDK_INT <= Build.VERSION_CODES.P ->
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE) ==
                PackageManager.PERMISSION_GRANTED

        else -> false
    }

    internal companion object {
        const val SURFACE_MEDIA_IMAGES = "media:images"
        const val SURFACE_MEDIA_VIDEO = "media:video"
        const val SURFACE_MEDIA_AUDIO = "media:audio"
        const val SURFACE_APP_CACHE = "app:cache"
        const val SURFACE_ALL_VOLUMES = "volumes:all"
        const val SURFACE_TREE_PREFIX = "tree:"
    }
}
