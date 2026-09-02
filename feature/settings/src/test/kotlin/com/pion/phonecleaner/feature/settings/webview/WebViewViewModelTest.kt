package com.pion.phonecleaner.feature.settings.webview

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.pion.phonecleaner.core.common.error.AppError
import com.pion.phonecleaner.domain.model.settings.LegalDocument
import com.pion.phonecleaner.feature.settings.testing.FakeLegalDocumentUrls
import com.pion.phonecleaner.feature.settings.testing.MainDispatcherRule
import com.pion.phonecleaner.feature.settings.testing.runVmTest
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

internal class WebViewViewModelTest {

    @get:Rule
    val mainDispatcher = MainDispatcherRule()

    private val urls = FakeLegalDocumentUrls(
        urls = mapOf(LegalDocument.TermsOfService to "https://example.invalid/terms"),
    )

    private fun viewModel(argument: Any? = LegalDocument.TermsOfService.name) = WebViewViewModel(
        savedStateHandle = SavedStateHandle(
            if (argument == null) emptyMap() else mapOf(WebViewArgs.DOCUMENT to argument),
        ),
        urls = urls,
    )

    // ── the route argument IS the initial state ────────────────────────────────────────────────

    @Test
    fun `the argument is read once, as a name`() {
        val state = viewModel().state.value

        assertEquals(LegalDocument.TermsOfService, state.document)
        assertEquals("https://example.invalid/terms", state.url)
        assertTrue(state.isLoading)
        assertFalse(state.isUnavailable)
    }

    @Test
    fun `the argument is read when Navigation hands back the enum itself`() {
        val state = viewModel(argument = LegalDocument.TermsOfService).state.value

        assertEquals(LegalDocument.TermsOfService, state.document)
    }

    @Test
    fun `a missing argument falls back to the privacy policy rather than crashing`() {
        assertEquals(LegalDocument.PrivacyPolicy, viewModel(argument = null).state.value.document)
    }

    /** A screen with nothing to load is not "loading": it says so instead of spinning forever. */
    @Test
    fun `an unconfigured document is unavailable and not loading`() {
        val state = viewModel(argument = LegalDocument.PrivacyPolicy.name).state.value

        assertTrue(state.isUnavailable)
        assertFalse(state.isLoading)
    }

    // ── reducers ──────────────────────────────────────────────────────────────────────────────

    @Test
    fun `page transitions drive isLoading and clear the previous error`() {
        val vm = viewModel()

        vm.onIntent(WebViewIntent.PageFailed(AppError.NoNetwork))
        assertFalse(vm.state.value.isLoading)
        assertEquals(AppError.NoNetwork, vm.state.value.error)

        vm.onIntent(WebViewIntent.PageStarted)
        assertTrue(vm.state.value.isLoading)
        assertEquals(null, vm.state.value.error)

        vm.onIntent(WebViewIntent.PageFinished)
        assertFalse(vm.state.value.isLoading)
    }

    // ── effects ───────────────────────────────────────────────────────────────────────────────

    @Test
    fun `retry clears the error and asks for a reload`() = mainDispatcher.runVmTest {
        val vm = viewModel()
        vm.onIntent(WebViewIntent.PageFailed(AppError.NoNetwork))

        vm.effects.test {
            vm.onIntent(WebViewIntent.RetryTapped)
            assertTrue(awaitItem() is WebViewEffect.ReloadPage)
        }
        assertTrue(vm.state.value.isLoading)
        assertEquals(null, vm.state.value.error)
    }

    @Test
    fun `retry on an unavailable document does nothing`() = mainDispatcher.runVmTest {
        val vm = viewModel(argument = LegalDocument.PrivacyPolicy.name)

        vm.effects.test {
            vm.onIntent(WebViewIntent.RetryTapped)
            expectNoEvents()
        }
    }

    @Test
    fun `an in-page link is handed out rather than swallowed`() = mainDispatcher.runVmTest {
        val vm = viewModel()

        vm.effects.test {
            vm.onIntent(WebViewIntent.ExternalLinkTapped("https://example.invalid/sub"))

            val effect = awaitItem()
            assertTrue(effect is WebViewEffect.OpenInBrowser)
            assertEquals("https://example.invalid/sub", effect.url)
        }
    }

    @Test
    fun `back raises navigate back`() = mainDispatcher.runVmTest {
        val vm = viewModel()

        vm.effects.test {
            vm.onIntent(WebViewIntent.BackPressed)
            assertTrue(awaitItem() is WebViewEffect.NavigateBack)
        }
    }
}
