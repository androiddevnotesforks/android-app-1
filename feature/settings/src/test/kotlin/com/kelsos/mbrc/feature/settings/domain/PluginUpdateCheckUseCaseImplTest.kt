package com.kelsos.mbrc.feature.settings.domain

import app.cash.turbine.test
import com.kelsos.mbrc.core.common.test.testDispatcher
import com.kelsos.mbrc.core.networking.client.UiMessage
import com.kelsos.mbrc.core.networking.client.UiMessageQueue
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import java.time.Instant
import java.time.temporal.ChronoUnit
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test

private const val ANCIENT_PLUGIN = "1.0.0"
private const val MINIMUM_REQUIRED = "1.4.0"

class PluginUpdateCheckUseCaseImplTest {
  private val manager: SettingsManager = mockk(relaxed = true)
  private val releaseParser: GithubReleaseParser = GithubReleaseParser { "v9.9.9" }
  private val queue = MutableSharedFlow<UiMessage>(extraBufferCapacity = 20)
  private val uiMessage: UiMessageQueue = mockk {
    io.mockk.every { messages } returns queue
  }

  private lateinit var useCase: PluginUpdateCheckUseCaseImpl

  @Before
  fun setUp() {
    useCase = PluginUpdateCheckUseCaseImpl(manager, uiMessage, releaseParser)
  }

  private fun checkedLongAgo() {
    coEvery { manager.getLastUpdated(any()) } returns
      Instant.now().minus(30, ChronoUnit.DAYS)
  }

  private fun checkedJustNow() {
    coEvery { manager.getLastUpdated(any()) } returns Instant.now()
  }

  /**
   * BUG: this documents current behaviour, not correct behaviour.
   *
   * `toVersionArray` calls `split("\\.", limit = 3)`, but Kotlin's `String.split` takes *literal*
   * delimiters rather than a regex, so a version never splits on the dot. Every version parses to
   * [0, 0, 0], which makes the comparison in `check()` always return false. The consequence is
   * that a plugin below the minimum supported version is never reported, and the optional
   * "update available" message is never emitted either.
   *
   * The fix is `split(".", limit = 3)`. Once that lands, this test should be replaced by one
   * asserting that a 1.0.0 plugin does emit [UiMessage.PluginUpdateRequired].
   */
  @Test
  fun `an unsupported plugin version is silently accepted because parsing is broken`() =
    runTest(testDispatcher) {
      checkedLongAgo()
      coEvery { manager.pluginUpdateCheckFlow } returns flowOf(false)

      queue.test {
        useCase.checkIfUpdateNeeded(ANCIENT_PLUGIN)

        expectNoEvents()
      }
    }

  @Test
  fun `the required check is skipped entirely when update checks are disabled`() =
    runTest(testDispatcher) {
      checkedLongAgo()
      coEvery { manager.pluginUpdateCheckFlow } returns flowOf(false)

      useCase.checkIfUpdateNeeded(ANCIENT_PLUGIN)

      coVerify(exactly = 0) { manager.setLastUpdated(any(), false) }
    }

  @Test
  fun `no network call happens when the last check was recent`() = runTest(testDispatcher) {
    checkedJustNow()
    coEvery { manager.pluginUpdateCheckFlow } returns flowOf(true)

    queue.test {
      useCase.checkIfUpdateNeeded(ANCIENT_PLUGIN)

      expectNoEvents()
    }
    coVerify(exactly = 0) { manager.setLastUpdated(any(), false) }
  }

  @Test
  fun `the optional check honours the disabled preference`() = runTest(testDispatcher) {
    checkedLongAgo()
    coEvery { manager.pluginUpdateCheckFlow } returns flowOf(false)

    useCase.checkIfUpdateNeeded("9.9.9")

    coVerify(exactly = 0) { manager.setLastUpdated(any(), any()) }
  }
}
