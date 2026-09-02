package com.pion.phonecleaner.domain.repository

import com.pion.phonecleaner.domain.model.junk.JunkCategory
import com.pion.phonecleaner.domain.model.junk.JunkSession
import kotlinx.collections.immutable.ImmutableList
import kotlinx.coroutines.flow.StateFlow

/**
 * The in-memory hand-off between the three junk screens.
 *
 * A `single` holding a `StateFlow<JunkSession?>` is honest about what it is: a hand-off with an
 * owner and an explicit [clear], not three `volatile` statics with no lifecycle at all (`wc.g`,
 * `docs/screens/12-junk-cleaning.md` §1.4).
 *
 * PLACEMENT — §1.4 writes this interface into `:data/junk/JunkSessionStore.kt`. It cannot live
 * there: all three junk ViewModels take it as a constructor argument, and `:feature:*` may not
 * depend on `:data` (`LLM.md` §2, §3.7). The **interface** therefore sits with the other junk ports;
 * the implementation, `InMemoryJunkSessionStore`, is in `:data/junk/` exactly as §1.4 places it.
 * Same correction shape as `AppLanguage`'s: the rule bends by moving the type, never by weakening
 * the rule.
 *
 * DECLARED IN `junkDataModule`.
 */
interface JunkSessionStore {

    /** `null` means "no scan in this process" — the process-death branch every consumer carries. */
    val session: StateFlow<JunkSession?>

    /** Publishes a finished scan. **Everything starts selected**, as it does in the competitor. */
    fun put(categories: ImmutableList<JunkCategory>, totalBytes: Long)

    /**
     * Records what the review screen has ticked, for the clean screen to read.
     *
     * > Added by the same correction that added `JunkSession.selectedPaths`
     * > (`docs/screens/12-junk-cleaning.md` §1.4): `r2-03` §B2 defines neither and then reads the
     * > field.
     */
    fun select(paths: Set<String>)

    fun clear()
}
