package com.pion.phonecleaner.data.trash

import android.content.Context
import android.os.Environment
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import com.pion.phonecleaner.core.common.concurrent.DispatcherProvider
import com.pion.phonecleaner.core.common.log.AppLogger
import com.pion.phonecleaner.data.datastore.TrashPrefs
import com.pion.phonecleaner.data.permission.StorageAccessChecks
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File

/**
 * Where the bin lives, per volume.
 *
 * ### Why there is a probe and not a constant
 *
 * Engineer decision E1 puts the root under `context.getExternalFilesDirs(null)`, on the reasoning that
 * a same-volume `File.renameTo()` is O(1). On API 30+ that reasoning has a hole: `/storage/emulated/0`
 * is a FUSE mount and `Android/data/<pkg>` is a pass-through bind mount of the lower filesystem, so
 * the two are different mount points and `rename(2)` returns EXDEV — `renameTo` answers `false` and
 * throws nothing. All-files access does not change it: the grant is a permission, the boundary is a
 * mount.
 *
 * Rather than guess, [resolve] measures once per volume with a zero-byte file and remembers the
 * answer in [TrashPrefs]. Cost: one create, one rename, one delete, once per volume per install.
 * Cost of guessing wrong in either direction: every move silently fails (guessing E1), or the bin
 * survives an uninstall for no reason (guessing the fallback).
 *
 * **Volume discovery is self-contained here, not read off `AndroidStorageRootProvider`.** That class's
 * `volumePaths()` answers the identical question but is `private`, and this phase does not own that
 * file (`LLM.md` §2 boundary is between modules, not within `:data` — this is a file-ownership
 * constraint of the plan, not an architecture rule). A volume's shared root is instead derived from
 * each entry of `context.getExternalFilesDirs(null)` by trimming the `/Android/data/<pkg>/…` suffix —
 * a technique that needs no `StorageManager` call and no permission, since an app-private directory is
 * always enumerable.
 */
internal class TrashRoots(
    private val context: Context,
    private val store: DataStore<Preferences>,
    private val dispatchers: DispatcherProvider,
    private val log: AppLogger,
    private val hasAccess: () -> Boolean = { StorageAccessChecks(context).allFiles() },
    private val renameProbe: (File, File) -> Boolean = { from, to -> from.renameTo(to) },
) {
    private val probeLock = Mutex()

    /** The root to use on the volume containing [forPath], creating it if needed. Null ⇒ no bin here. */
    suspend fun resolve(forPath: String): File? = withContext(dispatchers.io) {
        val volume = matchVolume(forPath) ?: return@withContext null
        val volumeKey = volume.root.absolutePath
        if (!hasAccess()) return@withContext null
        val mode = probeLock.withLock { cachedMode(volumeKey) ?: run {
            // A probe writes at the volume root, which needs the same grant the shared fallback does.
            // Without it, no attempt is cached — so a later grant is tested fresh, not remembered wrong.
            probe(volume).also { cacheMode(volumeKey, it) }
        } }
        val trashDir = when (mode) {
            RootMode.App -> File(volume.appPrivateDir, TRASH_DIR_NAME)
            RootMode.Shared -> {
                File(volume.root, FALLBACK_DIR_NAME)
            }
        }
        val guard = transcodeTempRoot
        check(guard == null || !trashDir.absolutePath.startsWith(guard)) {
            "Trash root ${trashDir.absolutePath} must not be under the video transcode temp dir ($guard)"
        }
        if (!trashDir.isDirectory && !trashDir.mkdirs()) return@withContext null
        ensureNoMedia(trashDir)
        trashDir
    }

    /** Whether the primary volume currently has a usable root — `TrashRepository.isAvailable()`, asked before any specific file is known. */
    suspend fun isAvailable(): Boolean = resolve(PRIMARY_VOLUME_PATH) != null

    /** Every root that exists right now — what `DefaultStorageScanner` must not walk into. No probe, no permission needed: a plain disk check. */
    suspend fun allRoots(): List<String> = withContext(dispatchers.io) {
        volumes().flatMap { listOf(File(it.appPrivateDir, TRASH_DIR_NAME), File(it.root, FALLBACK_DIR_NAME)) }
            .filter(File::isDirectory)
            .map(File::getAbsolutePath)
    }

    /** A missing file is evidence only while its volume and grant are still accessible. */
    fun canInspect(path: String): Boolean {
        val volume = matchVolume(path) ?: return false
        val appRoot = File(volume.appPrivateDir, TRASH_DIR_NAME)
        val sharedRoot = File(volume.root, FALLBACK_DIR_NAME)
        val file = File(path).canonicalFile
        fun inside(root: File) = file.path.startsWith(root.canonicalPath + File.separator)
        return when {
            inside(appRoot) -> volume.appPrivateDir.isDirectory && volume.appPrivateDir.canRead()
            inside(sharedRoot) -> hasAccess() && volume.root.isDirectory && volume.root.canRead()
            else -> false
        }
    }

    /** Longest-prefix match, never a root on a different volume — a wrong guess turns an O(1) rename into a multi-GB copy. */
    private fun matchVolume(forPath: String): Volume? = volumes()
        .filter { forPath == it.root.absolutePath || forPath.startsWith(it.root.absolutePath + File.separator) }
        .maxByOrNull { it.root.absolutePath.length }

    private fun volumes(): List<Volume> = context.getExternalFilesDirs(null)
        .filterNotNull()
        .mapNotNull { appPrivateDir -> volumeRootOf(appPrivateDir)?.let { Volume(it, appPrivateDir) } }

    private fun volumeRootOf(appPrivateDir: File): File? {
        val marker = "${File.separator}Android${File.separator}data${File.separator}"
        val cut = appPrivateDir.absolutePath.indexOf(marker)
        return if (cut > 0) File(appPrivateDir.absolutePath.substring(0, cut)) else null
    }

    /**
     * ```
     * create <volumeRoot>/.pcz-probe-<nanoTime>  (zero bytes)
     * renameTo(<e1Root>/.pcz-probe-<nanoTime>)
     *   true  -> delete the probe, remember "app"
     *   false -> delete the probe, remember "shared"
     * ```
     * The probe file is created at the volume root, not in a media folder, so a scanner never sees it,
     * and its name carries `nanoTime` so two runs cannot collide.
     */
    private fun probe(volume: Volume): RootMode {
        val e1Root = File(volume.appPrivateDir, TRASH_DIR_NAME).apply { mkdirs() }
        val probeName = "$PROBE_PREFIX${System.nanoTime()}"
        val atVolumeRoot = File(volume.root, probeName)
        val moved = runCatching {
            if (!atVolumeRoot.createNewFile()) return@runCatching false
            val destination = File(e1Root, probeName)
            val result = renameProbe(atVolumeRoot, destination)
            destination.delete()
            atVolumeRoot.delete()
            result
        }.onFailure { log.e(it) { "Trash root probe failed for ${volume.root}" } }.getOrDefault(false)
        return if (moved) RootMode.App else RootMode.Shared
    }

    private suspend fun cachedMode(volumeKey: String): RootMode? {
        val stored = store.data.first()[TrashPrefs.ROOT_MODE].orEmpty()
        val entry = stored.firstOrNull { it.startsWith("$volumeKey=") } ?: return null
        return RootMode.entries.firstOrNull { it.token == entry.substringAfter('=') }
    }

    private suspend fun cacheMode(volumeKey: String, mode: RootMode) {
        store.edit { prefs ->
            val kept = prefs[TrashPrefs.ROOT_MODE].orEmpty().filterNot { it.startsWith("$volumeKey=") }
            prefs[TrashPrefs.ROOT_MODE] = (kept + "$volumeKey=${mode.token}").toSet()
        }
    }

    /** Research says `Android/data` is not indexed on API 30+ and the file is redundant there; on API 28 that is unconfirmed, and one zero-byte file is cheaper than finding out from a user's gallery. */
    private fun ensureNoMedia(dir: File) {
        runCatching { File(dir, ".nomedia").createNewFile() }
    }

    /** T6's guard: MUST never equal or contain `VideoOutputPublisher`'s transcode temp dir. */
    private val transcodeTempRoot: String? by lazy {
        context.getExternalFilesDir(Environment.DIRECTORY_MOVIES)?.absolutePath
    }

    private data class Volume(val root: File, val appPrivateDir: File)

    private enum class RootMode(val token: String) { App("app"), Shared("shared") }

    private companion object {
        const val TRASH_DIR_NAME = "trash"
        const val FALLBACK_DIR_NAME = ".PhoneCleanerTrash"
        const val PROBE_PREFIX = ".pcz-probe-"

        /** `resolve` is asked with no file when there is none yet (`isAvailable`); the primary volume answers that generic question. */
        val PRIMARY_VOLUME_PATH: String = Environment.getExternalStorageDirectory()?.absolutePath ?: "/storage/emulated/0"
    }
}
