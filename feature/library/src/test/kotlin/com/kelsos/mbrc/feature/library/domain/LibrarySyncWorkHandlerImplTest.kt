package com.kelsos.mbrc.feature.library.domain

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.workDataOf
import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.kelsos.mbrc.core.common.test.testDispatcher
import com.kelsos.mbrc.core.common.utilities.AppError
import com.kelsos.mbrc.core.common.utilities.Outcome
import com.kelsos.mbrc.feature.library.ui.LibraryMediaType
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LibrarySyncWorkHandlerImplTest {
  private val workManager: WorkManager = mockk(relaxed = true)
  private val infos = MutableStateFlow<List<WorkInfo>>(emptyList())

  private fun handler(): LibrarySyncWorkHandlerImpl {
    every {
      workManager.getWorkInfosForUniqueWorkFlow(LibrarySyncWorker.SYNC_WORK_TAG)
    } returns infos
    return LibrarySyncWorkHandlerImpl(workManager)
  }

  private fun workInfo(
    state: WorkInfo.State,
    output: Data = Data.EMPTY,
    progress: Data = Data.EMPTY
  ) = WorkInfo(
    UUID.randomUUID(),
    state,
    setOf(LibrarySyncWorker.SYNC_WORK_TAG),
    output,
    progress,
    0,
    0
  )

  /**
   * The worker the request carries is not asserted: [OneTimeWorkRequest] exposes it only through
   * `workSpec`, which is restricted to the WorkManager library group.
   */
  @Test
  fun `a sync replaces any sync already queued`() {
    handler().sync()

    verify {
      workManager.enqueueUniqueWork(
        LibrarySyncWorker.SYNC_WORK_TAG,
        ExistingWorkPolicy.REPLACE,
        any<OneTimeWorkRequest>()
      )
    }
  }

  /**
   * A finished sync from a previous app launch is still sitting in WorkManager, so emitting it
   * would pop a "sync complete" message every time the screen is opened.
   */
  @Test
  fun `a sync that succeeded before this session is not reported`() = runTest(testDispatcher) {
    val handler = handler()
    infos.value = listOf(workInfo(WorkInfo.State.SUCCEEDED, workDataOf("genres" to 3)))

    handler.syncResults().test {
      expectNoEvents()
    }
  }

  @Test
  fun `a sync that ran in this session reports its results`() = runTest(testDispatcher) {
    val handler = handler()

    handler.syncResults().test {
      infos.value = listOf(workInfo(WorkInfo.State.RUNNING))
      expectNoEvents()

      infos.value = listOf(
        workInfo(WorkInfo.State.SUCCEEDED, workDataOf("genres" to 3, "artists" to 7))
      )

      assertThat(awaitItem()).isInstanceOf(Outcome.Success::class.java)
    }
  }

  @Test
  fun `a success carrying no counts is treated as nothing to do`() = runTest(testDispatcher) {
    val handler = handler()

    handler.syncResults().test {
      infos.value = listOf(workInfo(WorkInfo.State.RUNNING))
      infos.value = listOf(workInfo(WorkInfo.State.SUCCEEDED, workDataOf("unrelated" to 1)))

      assertThat(awaitItem()).isEqualTo(Outcome.Failure(AppError.NoOp))
    }
  }

  @Test
  fun `a failure reports the message the worker recorded`() = runTest(testDispatcher) {
    val handler = handler()

    handler.syncResults().test {
      infos.value = listOf(workInfo(WorkInfo.State.RUNNING))
      infos.value = listOf(workInfo(WorkInfo.State.FAILED, workDataOf("error" to "no connection")))

      assertThat(awaitItem()).isEqualTo(Outcome.Failure(AppError.Message("no connection")))
    }
  }

  @Test
  fun `a failure without a message still reports something`() = runTest(testDispatcher) {
    val handler = handler()

    handler.syncResults().test {
      infos.value = listOf(workInfo(WorkInfo.State.RUNNING))
      infos.value = listOf(workInfo(WorkInfo.State.FAILED))

      assertThat(awaitItem()).isEqualTo(Outcome.Failure(AppError.Message("Unknown failure")))
    }
  }

  @Test
  fun `a cancelled sync is reported as cancelled`() = runTest(testDispatcher) {
    val handler = handler()

    handler.syncResults().test {
      infos.value = listOf(workInfo(WorkInfo.State.RUNNING))
      infos.value = listOf(workInfo(WorkInfo.State.CANCELLED))

      assertThat(awaitItem()).isEqualTo(Outcome.Failure(AppError.Message("Sync was cancelled")))
    }
  }

  @Test
  fun `no work at all reports idle progress`() = runTest(testDispatcher) {
    val handler = handler()

    handler.syncProgress().test {
      assertThat(awaitItem()).isEqualTo(LibrarySyncProgress.Idle)
    }
  }

  @Test
  fun `progress reports the category and counts the worker published`() = runTest(testDispatcher) {
    val handler = handler()
    val progress = workDataOf(
      LibrarySyncWorker.CATEGORY to LibraryMediaType.Albums.code,
      LibrarySyncWorker.CURRENT to 40,
      LibrarySyncWorker.TOTAL to 100
    )

    handler.syncProgress().test {
      awaitItem()
      infos.value = listOf(workInfo(WorkInfo.State.RUNNING, progress = progress))

      val item = awaitItem()
      assertThat(item.category).isEqualTo(LibraryMediaType.Albums)
      assertThat(item.current).isEqualTo(40)
      assertThat(item.total).isEqualTo(100)
      assertThat(item.running).isTrue()
    }
  }

  @Test
  fun `progress stops being running once the work finishes`() = runTest(testDispatcher) {
    val handler = handler()

    handler.syncProgress().test {
      awaitItem()
      infos.value = listOf(workInfo(WorkInfo.State.SUCCEEDED))

      assertThat(awaitItem().running).isFalse()
    }
  }
}
