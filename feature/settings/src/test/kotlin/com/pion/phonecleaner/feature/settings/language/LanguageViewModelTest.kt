package com.pion.phonecleaner.feature.settings.language

import app.cash.turbine.test
import com.pion.phonecleaner.core.common.error.AppError
import com.pion.phonecleaner.domain.model.settings.AppLanguage
import com.pion.phonecleaner.feature.settings.testing.FakeLanguageRepository
import com.pion.phonecleaner.feature.settings.testing.MainDispatcherRule
import com.pion.phonecleaner.feature.settings.testing.StorageFailure
import com.pion.phonecleaner.feature.settings.testing.runVmTest
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

internal class LanguageViewModelTest {

    @get:Rule
    val mainDispatcher = MainDispatcherRule()

    private val repository = FakeLanguageRepository()

    private fun viewModel() = LanguageViewModel(repository)

    // ── reducers ──────────────────────────────────────────────────────────────────────────────

    @Test
    fun `the roster is read once and is not IO`() {
        assertEquals(repository.roster, viewModel().state.value.languages)
    }

    @Test
    fun `the applied language flows into both tags`() {
        repository.applied.value = AppLanguage("ja-JP")

        val state = viewModel().state.value

        assertEquals("ja-JP", state.appliedTag)
        assertEquals("ja-JP", state.selectedTag)
        assertFalse(state.isDirty)
        assertFalse(state.canSave)
    }

    @Test
    fun `null means follow the system and is a real selection`() {
        val state = viewModel().state.value

        assertNull(state.selectedTag)
        assertNull(state.appliedTag)
        assertFalse(state.canSave)
    }

    @Test
    fun `selecting persists nothing`() {
        val vm = viewModel()

        vm.onIntent(LanguageIntent.LanguageSelected("ja-JP"))

        assertEquals("ja-JP", vm.state.value.selectedTag)
        assertTrue(vm.state.value.canSave)
        assertTrue(repository.writes.isEmpty())
    }

    /** A re-emission from an unrelated preference write must not discard an unsaved choice. */
    @Test
    fun `a re-emission does not overwrite a pending selection`() {
        val vm = viewModel()
        vm.onIntent(LanguageIntent.LanguageSelected("ja-JP"))

        repository.applied.value = AppLanguage("en-US")

        assertEquals("ja-JP", vm.state.value.selectedTag)
        assertEquals("en-US", vm.state.value.appliedTag)
    }

    // ── effects ───────────────────────────────────────────────────────────────────────────────

    @Test
    fun `save writes the tag and then raises ApplyLocale`() = mainDispatcher.runVmTest {
        val vm = viewModel()

        vm.effects.test {
            vm.onIntent(LanguageIntent.LanguageSelected("ja-JP"))
            vm.onIntent(LanguageIntent.SaveTapped)

            val effect = awaitItem()
            assertTrue(effect is LanguageEffect.ApplyLocale)
            assertEquals("ja-JP", effect.tag)
            expectNoEvents()
        }
        assertEquals(listOf<String?>("ja-JP"), repository.writes.toList())
        assertFalse(vm.state.value.isApplying)
    }

    @Test
    fun `save with nothing changed cannot no-op because it cannot run`() = mainDispatcher.runVmTest {
        val vm = viewModel()

        vm.effects.test {
            vm.onIntent(LanguageIntent.SaveTapped)
            expectNoEvents()
        }
        assertTrue(repository.writes.isEmpty())
    }

    // ── stuck states ──────────────────────────────────────────────────────────────────────────

    @Test
    fun `a failed write lowers isApplying and reports the error`() = mainDispatcher.runVmTest {
        repository.writeResult = StorageFailure
        val vm = viewModel()

        vm.effects.test {
            vm.onIntent(LanguageIntent.LanguageSelected("ja-JP"))
            vm.onIntent(LanguageIntent.SaveTapped)

            assertTrue(awaitItem() is LanguageEffect.ShowMessage)
        }
        assertFalse(vm.state.value.isApplying)
        assertTrue(vm.state.value.error is AppError.Storage)
        // The second attempt still reaches the repository — the flag came down, not just the spinner.
        vm.onIntent(LanguageIntent.SaveTapped)
        assertEquals(listOf<String?>("ja-JP", "ja-JP"), repository.writes.toList())
    }

    // ── crash containment ─────────────────────────────────────────────────────────────────────

    @Test
    fun `a repository that throws becomes an error state, not a thrown test`() =
        mainDispatcher.runVmTest {
            repository.throwOnWrite = true
            val vm = viewModel()

            vm.onIntent(LanguageIntent.LanguageSelected("ja-JP"))
            vm.onIntent(LanguageIntent.SaveTapped)

            assertFalse(vm.state.value.isApplying)
            assertTrue(vm.state.value.error is AppError.Unexpected)
        }

    @Test
    fun `back raises navigate back and discards nothing to the store`() = mainDispatcher.runVmTest {
        val vm = viewModel()

        vm.effects.test {
            vm.onIntent(LanguageIntent.LanguageSelected("ja-JP"))
            vm.onIntent(LanguageIntent.BackPressed)
            assertTrue(awaitItem() is LanguageEffect.NavigateBack)
        }
        assertTrue(repository.writes.isEmpty())
    }
}
