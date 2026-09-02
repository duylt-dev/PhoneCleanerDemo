package com.pion.phonecleaner.feature.photo.testing

import com.pion.phonecleaner.domain.model.photo.GeotagScanProgress
import com.pion.phonecleaner.domain.model.photo.PhotoGroup
import com.pion.phonecleaner.domain.model.photo.PhotoId
import com.pion.phonecleaner.domain.model.photo.SimilarPhotoSession
import com.pion.phonecleaner.domain.model.photo.SimilarScanProgress
import com.pion.phonecleaner.domain.model.photo.StripStep
import com.pion.phonecleaner.domain.repository.ExifRepository
import com.pion.phonecleaner.domain.repository.SimilarPhotoScanner
import com.pion.phonecleaner.domain.repository.SimilarPhotoSessionStore
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toImmutableSet
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.update

/**
 * The two scanning engines and the session store, as hand-written fakes. **No mocking library** —
 * `LLM.md` §9. The row builders and the repository fakes are in `PhotoFakes.kt`.
 */
internal class FakeSimilarPhotoScanner(
    var emissions: List<SimilarScanProgress> = emptyList(),
) : SimilarPhotoScanner {
    override fun scan(): Flow<SimilarScanProgress> = flow { emissions.forEach { emit(it) } }
}

/**
 * The geotag scan and the strip, both as flows. [strippedIds] records what the strip was actually
 * asked for — the half the competitor's engine throws away.
 */
internal class FakeExifRepository(
    var scan: List<GeotagScanProgress> = emptyList(),
    var steps: List<StripStep> = emptyList(),
    /** Emit [steps] and then never complete — a strip still in flight. */
    var hangs: Boolean = false,
) : ExifRepository {
    var strippedIds: List<PhotoId> = emptyList()

    override fun photosWithLocation(): Flow<GeotagScanProgress> = flow { scan.forEach { emit(it) } }

    override fun stripLocation(ids: List<PhotoId>): Flow<StripStep> = flow {
        strippedIds = ids
        steps.forEach { emit(it) }
        if (hangs) awaitCancellation()
    }
}

/**
 * Mirrors the real `InMemorySimilarPhotoSessionStore`, including the two rules that matter to the
 * screens: `put` pre-selects everything but each group's opener, and `remove` drops a group that
 * falls to a single member.
 */
internal class FakeSimilarPhotoSessionStore : SimilarPhotoSessionStore {
    private val _session = MutableStateFlow<SimilarPhotoSession?>(null)
    override val session: StateFlow<SimilarPhotoSession?> = _session.asStateFlow()

    override fun put(groups: ImmutableList<PhotoGroup>, skipped: Int) {
        _session.value = SimilarPhotoSession(
            groups = groups,
            selectedIds = groups.asSequence()
                .flatMap { it.photos.asSequence().drop(1) }
                .map { it.id }
                .toImmutableSet(),
            skipped = skipped,
        )
    }

    override fun select(ids: Set<PhotoId>) {
        _session.update { it?.copy(selectedIds = ids.toImmutableSet()) }
    }

    override fun remove(ids: Set<PhotoId>) {
        if (ids.isEmpty()) return
        _session.update { current ->
            current ?: return@update null
            val groups = current.groups
                .map { g -> g.copy(photos = g.photos.filterNot { it.id in ids }.toImmutableList()) }
                .filter { it.photos.size > 1 }
                .toImmutableList()
            val alive = groups.flatMap { it.photos }.map { it.id }.toSet()
            current.copy(
                groups = groups,
                selectedIds = current.selectedIds.filter { it in alive }.toImmutableSet(),
            )
        }
    }

    override fun clear() {
        _session.value = null
    }
}
