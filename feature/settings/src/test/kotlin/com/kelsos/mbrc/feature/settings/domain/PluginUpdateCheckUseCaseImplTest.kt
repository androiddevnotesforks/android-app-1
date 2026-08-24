package com.kelsos.mbrc.feature.settings.domain

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.kelsos.mbrc.core.common.test.testDispatcher
import com.kelsos.mbrc.core.networking.client.UiMessage
import com.kelsos.mbrc.core.networking.client.UiMessageQueue
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
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
private const val NEWER_THAN_MINIMUM = "1.5.0"

class PluginUpdateCheckUseCaseImplTest {
  private val manager: SettingsManager = mockk(relaxed = true)
  private val releaseParser: GithubReleaseParser = GithubReleaseParser { "v9.9.9" }
  private val queue = MutableSharedFlow<UiMessage>(extraBufferCapacity = 20)
  private val uiMessage: UiMessageQueue = mockk {
    every { messages } returns queue
  }

  private lateinit var useCase: PluginUpdateCheckUseCaseImpl

  @Before
  fun setUp() {
    useCase = PluginUpdateCheckUseCaseImpl(manager, uiMessage, releaseParser)
  }

  private fun checkedLongAgo() {
    coEvery { manager.getLastUpdated(any()) } returns Instant.now().minus(30, ChronoUnit.DAYS)
  }

  private fun checkedJustNow() {
    coEvery { manager.getLastUpdated(any()) } returns Instant.now()
  }

  @Test
  fun `a plugin below the minimum supported version is reported`() = runTest(testDispatcher) {
    checkedLongAgo()

    queue.test {
      useCase.checkIfUpdateNeeded(ANCIENT_PLUGIN)

      val message = awaitItem()
      assertThat(message).isInstanceOf(UiMessage.PluginUpdateRequired::class.java)
      val required = message as UiMessage.PluginUpdateRequired
      assertThat(required.pluginVersion).isEqualTo(ANCIENT_PLUGIN)
      assertThat(required.minimumVersion).isEqualTo(MINIMUM_REQUIRED)
    }
  }

  @Test
  fun `reporting an unsupported plugin records the check so it does not repeat daily`() =
    runTest(testDispatcher) {
      checkedLongAgo()

      useCase.checkIfUpdateNeeded(ANCIENT_PLUGIN)

      coVerify { manager.setLastUpdated(any(), required = true) }
    }

  @Test
  fun `an unsupported plugin is reported at most once a day`() = runTest(testDispatcher) {
    checkedJustNow()

    queue.test {
      useCase.checkIfUpdateNeeded(ANCIENT_PLUGIN)

      expectNoEvents()
    }
    coVerify(exactly = 0) { manager.setLastUpdated(any(), any()) }
  }

  @Test
  fun `a supported plugin never triggers the required message`() = runTest(testDispatcher) {
    checkedLongAgo()
    coEvery { manager.pluginUpdateCheckFlow } returns flowOf(false)

    queue.test {
      useCase.checkIfUpdateNeeded(NEWER_THAN_MINIMUM)

      expectNoEvents()
    }
  }

  @Test
  fun `a version equal to the minimum is supported`() = runTest(testDispatcher) {
    checkedLongAgo()
    coEvery { manager.pluginUpdateCheckFlow } returns flowOf(false)

    queue.test {
      useCase.checkIfUpdateNeeded(MINIMUM_REQUIRED)

      expectNoEvents()
    }
  }

  @Test
  fun `the optional check is skipped when the preference is off`() = runTest(testDispatcher) {
    checkedLongAgo()
    coEvery { manager.pluginUpdateCheckFlow } returns flowOf(false)

    useCase.checkIfUpdateNeeded(NEWER_THAN_MINIMUM)

    coVerify(exactly = 0) { manager.setLastUpdated(any(), false) }
  }

  @Test
  fun `no optional check happens when the last one was recent`() = runTest(testDispatcher) {
    checkedJustNow()
    coEvery { manager.pluginUpdateCheckFlow } returns flowOf(true)

    queue.test {
      useCase.checkIfUpdateNeeded(NEWER_THAN_MINIMUM)

      expectNoEvents()
    }
    coVerify(exactly = 0) { manager.setLastUpdated(any(), false) }
  }
}
