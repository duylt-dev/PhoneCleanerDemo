package com.pion.phonecleaner.data.junk

import com.pion.phonecleaner.core.common.time.AppClock
import com.pion.phonecleaner.domain.model.junk.JunkCategory
import com.pion.phonecleaner.domain.model.junk.JunkSession
import com.pion.phonecleaner.domain.repository.JunkSessionStore
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableSet
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * The scan hand-off, as one `single` with an owner and an explicit [clear]
 * (`docs/screens/12-junk-cleaning.md` §1.4).
 *
 * It replaces the three `volatile` statics of `wc.g`, which the review screen then aliases by
 * `categories.addAll(wc.g.b())` — a reference copy, so screen and static hold the same mutable nodes
 * and the selection code mutates both at once (`VibrnancActivity.java:496`, Delta R10). Here the
 * session is an immutable value and the categories inside it are `ImmutableList`s.
 *
 * `clock` is injected so a test can pin [JunkSession.scannedAt]; the competitor reads the system
 * clock directly wherever it needs one.
 */
internal class InMemoryJunkSessionStore(
    private val clock: AppClock,
) : JunkSessionStore {

    private val _session = MutableStateFlow<JunkSession?>(null)
    override val session: StateFlow<JunkSession?> = _session.asStateFlow()

    override fun put(categories: ImmutableList<JunkCategory>, totalBytes: Long) {
        _session.value = JunkSession(
            categories = categories,
            totalBytes = totalBytes,
            // Everything starts selected, matching the competitor: every scanned node arrives with
            // `size > 0`, and `size > 0` *is* "checked" there (§4.2).
            selectedPaths = categories.asSequence()
                .flatMap { it.items.asSequence() }
                .map { it.path }
                .toImmutableSet(),
            scannedAt = clock.now(),
        )
    }

    override fun select(paths: Set<String>) {
        _session.update { current -> current?.copy(selectedPaths = paths.toImmutableSet()) }
    }

    override fun clear() {
        _session.value = null
    }
}
