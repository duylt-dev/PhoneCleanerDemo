package com.pion.phonecleaner.feature.settings.devtools

import app.cash.turbine.test
import com.pion.phonecleaner.feature.settings.testing.FakePushRepository
import com.pion.phonecleaner.feature.settings.testing.MainDispatcherRule
import com.pion.phonecleaner.feature.settings.testing.runVmTest
import com.pion.phonecleaner.core.common.error.AppError
import com.pion.phonecleaner.core.common.result.AppResult
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The bench exists to prove one property: a simulated message takes **the same path** a real one
 * does. Every assertion below is about `PushRepository.handle` being the thing that runs.
 */
internal class DevToolsViewModelTest {

    @get:Rule
    val mainDispatcher = MainDispatcherRule()

    private val push = FakePushRepository()

    private fun viewModel() = DevToolsViewModel(push)

    @Test
    fun `a payload becomes the data map of a message handed to the real port`() =
        mainDispatcher.runVmTest {
            val vm = viewModel()
            vm.onIntent(DevToolsIntent.PayloadChanged("action=open\n  spaced = value \nbad line"))

            vm.effects.test {
                vm.onIntent(DevToolsIntent.DeliverTapped)

                val effect = awaitItem()
                assertTrue(effect is DevToolsEffect.Delivered)
                assertEquals(2, effect.dataKeyCount)
            }

            assertEquals(1, push.handled.size)
            assertEquals(
                mapOf("action" to "open", "spaced" to "value"),
                push.handled.single().data,
            )
        }

    /** Both scalar fields are the sender's, and there is no sender. Nothing is fabricated. */
    @Test
    fun `the simulated message carries no invented id or timestamp`() = mainDispatcher.runVmTest {
        val vm = viewModel()
        vm.onIntent(DevToolsIntent.PayloadChanged("k=v"))

        vm.onIntent(DevToolsIntent.DeliverTapped)

        val message = push.handled.single()
        assertEquals(null, message.messageId)
        assertEquals(0L, message.sentAtMillis)
    }

    @Test
    fun `an empty payload delivers nothing`() = mainDispatcher.runVmTest {
        val vm = viewModel()
        vm.onIntent(DevToolsIntent.PayloadChanged("   \nnot a pair\n=novalue"))

        vm.effects.test {
            vm.onIntent(DevToolsIntent.DeliverTapped)
            assertTrue(awaitItem() is DevToolsEffect.NothingToDeliver)
        }
        assertTrue(push.handled.isEmpty())
    }

    @Test
    fun `a failing port is reported, not swallowed`() = mainDispatcher.runVmTest {
        push.result = AppResult.Failure(AppError.Unexpected("no"))
        val vm = viewModel()
        vm.onIntent(DevToolsIntent.PayloadChanged("k=v"))

        vm.effects.test {
            vm.onIntent(DevToolsIntent.DeliverTapped)
            assertTrue(awaitItem() is DevToolsEffect.DeliveryFailed)
        }
    }

    @Test
    fun `a throwing port becomes an effect, not a thrown test`() = mainDispatcher.runVmTest {
        push.throwOnHandle = true
        val vm = viewModel()
        vm.onIntent(DevToolsIntent.PayloadChanged("k=v"))

        vm.effects.test {
            vm.onIntent(DevToolsIntent.DeliverTapped)
            assertTrue(awaitItem() is DevToolsEffect.DeliveryFailed)
        }
    }

    @Test
    fun `back raises navigate back`() = mainDispatcher.runVmTest {
        val vm = viewModel()

        vm.effects.test {
            vm.onIntent(DevToolsIntent.BackPressed)
            assertTrue(awaitItem() is DevToolsEffect.NavigateBack)
        }
    }
}
