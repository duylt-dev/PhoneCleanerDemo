package com.pion.phonecleaner.data.trash

import android.content.Context
import android.content.ContextWrapper
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import com.pion.phonecleaner.core.common.concurrent.DispatcherProvider
import com.pion.phonecleaner.core.common.log.AppLogger
import com.pion.phonecleaner.core.common.time.AppClock
import com.pion.phonecleaner.data.database.TrashEntryDao
import com.pion.phonecleaner.data.database.TrashSummaryRow
import com.pion.phonecleaner.data.database.entity.TrashEntryEntity
import com.pion.phonecleaner.domain.model.feature.FeatureId
import com.pion.phonecleaner.domain.model.file.FileKind
import com.pion.phonecleaner.domain.model.file.FileOrigin
import com.pion.phonecleaner.domain.model.file.ScannedFile
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import org.robolectric.RuntimeEnvironment
import java.io.File
import kotlin.time.Instant

internal class TrashFixture(base: File, dispatcher: CoroutineDispatcher) {
    val volume = File(base, "volume").apply { mkdirs() }
    val secondVolume = File(base, "sdcard").apply { mkdirs() }
    val appDir = File(volume, "Android/data/com.pion.phonecleaner/files").apply { mkdirs() }
    val secondAppDir = File(secondVolume, "Android/data/com.pion.phonecleaner/files").apply { mkdirs() }
    val context: Context = object : ContextWrapper(RuntimeEnvironment.getApplication()) {
        override fun getExternalFilesDirs(type: String?): Array<File> =
            arrayOf(appDir, secondAppDir).map { if (type == null) it else File(it, type) }.toTypedArray()
        override fun getExternalFilesDir(type: String?): File = if (type == null) appDir else File(appDir, type)
    }
    val dispatchers = object : DispatcherProvider {
        override val main = dispatcher
        override val io = dispatcher
        override val default = dispatcher
    }
    val prefs = object : DataStore<Preferences> {
        override val data = MutableStateFlow(emptyPreferences())
        override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences =
            transform(data.value).also { data.value = it }
    }
    var granted = true
    var probeSucceeds = false
    var probeCalls = 0
    var refuseMove = false
    var remove: (File) -> Boolean = { if (it.isDirectory) it.deleteRecursively() else it.delete() }
    var now = Instant.fromEpochMilliseconds(1_000)
    var scheduled = 0
    val roots = newRoots()
    val mover = TrashMover(context, roots, dispatchers, AppLogger.NoOp,
        rename = { from, to -> !refuseMove && from.renameTo(to) },
        remove = { remove(it) }, restoredIndex = { _, _ -> })
    val dao = FakeTrashDao()
    val repository = RoomTrashRepository(dao, roots, mover, object : AppClock {
        override fun now() = now
    }, dispatchers, AppLogger.NoOp, context, schedulePurge = { scheduled++ })

    fun newRoots() = TrashRoots(context, prefs, dispatchers, AppLogger.NoOp,
        hasAccess = { granted }, renameProbe = { from, to -> probeCalls++; probeSucceeds && from.renameTo(to) })

    fun source(name: String = "source.txt", content: String = "payload"): ScannedFile {
        val file = File(volume, name).apply { parentFile!!.mkdirs(); writeText(content) }
        return ScannedFile(file.path, file.path, file.name, file.length(), FileKind.Other, FileOrigin.PlainFile)
    }

    suspend fun row(id: String = "row", state: String = "TRASHED", content: String = "payload"): TrashEntryEntity {
        val payload = File(roots.resolve(volume.path)!!, id).apply { writeText(content) }
        return newPendingTrashEntry(id, File(volume, "$id.txt").path, payload.path,
            "$id.txt", payload.length(), 1, false, null, FeatureId.Trash, null, now).copy(state = state)
            .also { dao.insert(it) }
    }
}

/** DAO fake retains transitions and supports suspending at a real commit boundary. */
internal class FakeTrashDao : TrashEntryDao {
    val rows = linkedMapOf<String, TrashEntryEntity>()
    private val changes = MutableStateFlow(0)
    var beforeInsert: suspend (TrashEntryEntity) -> Unit = { }
    var beforeState: suspend (String, String) -> Unit = { _, _ -> }
    private fun changed() { changes.value++ }
    override fun observeTrashed() = changes.map { rows.values.filter { it.state == "TRASHED" }.take(500) }
    override fun observeSummary() = changes.map {
        val visible = rows.values.filter { it.state == "TRASHED" }
        TrashSummaryRow(visible.size, visible.sumOf { it.sizeBytes })
    }
    override suspend fun byIds(ids: List<String>) = rows.values.filter { it.id in ids }
    override suspend fun expired(nowEpochMillis: Long) = rows.values.filter { it.state == "TRASHED" && it.expiresAtEpochMillis <= nowEpochMillis }
    override suspend fun unsettled() = rows.values.filter { it.state != "TRASHED" }
    override suspend fun allTrashed() = rows.values.filter { it.state == "TRASHED" }
    override suspend fun insert(entry: TrashEntryEntity) { beforeInsert(entry); rows[entry.id] = entry; changed() }
    override suspend fun setState(id: String, state: String): Int {
        beforeState(id, state)
        return update(id) { it.copy(state = state) }
    }
    override suspend fun setMediaRowCleared(id: String, cleared: Boolean) = update(id) { it.copy(mediaRowCleared = cleared) }
    override suspend fun updateRemaining(id: String, bytes: Long) = update(id) { it.copy(sizeBytes = bytes, state = "TRASHED") }
    override suspend fun deleteById(id: String): Int = if (rows.remove(id) != null) { changed(); 1 } else 0
    private fun update(id: String, transform: (TrashEntryEntity) -> TrashEntryEntity): Int {
        val row = rows[id] ?: return 0
        rows[id] = transform(row)
        changed()
        return 1
    }
}
