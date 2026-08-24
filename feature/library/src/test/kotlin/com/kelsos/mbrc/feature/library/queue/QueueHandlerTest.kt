package com.kelsos.mbrc.feature.library.queue

import com.google.common.truth.Truth.assertThat
import com.kelsos.mbrc.core.common.settings.LibrarySettings
import com.kelsos.mbrc.core.common.settings.TrackAction
import com.kelsos.mbrc.core.common.test.testDispatcher
import com.kelsos.mbrc.core.common.test.testDispatchers
import com.kelsos.mbrc.core.common.utilities.AppError
import com.kelsos.mbrc.core.common.utilities.Outcome
import com.kelsos.mbrc.core.data.library.track.Track
import com.kelsos.mbrc.core.data.library.track.TrackQuery
import com.kelsos.mbrc.core.data.library.track.TrackRepository
import com.kelsos.mbrc.core.networking.api.QueueApi
import com.kelsos.mbrc.core.networking.dto.QueuePayload
import com.kelsos.mbrc.core.networking.dto.QueueResponse
import com.kelsos.mbrc.core.queue.Queue
import io.mockk.CapturingSlot
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import java.io.IOException
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Test

private const val OK = 200
private const val FAILED = 500

class QueueHandlerTest {
  private val settings: LibrarySettings = mockk()
  private val trackRepository: TrackRepository = mockk()
  private val queueApi: QueueApi = mockk()

  private val handler = QueueHandler(settings, trackRepository, queueApi, testDispatchers)

  private val track = Track(
    artist = "Tool",
    title = "Lateralus",
    src = "C:\\music\\tool\\lateralus.mp3",
    trackno = 9,
    disc = 1,
    albumArtist = "Tool",
    album = "Lateralus",
    genre = "Rock",
    year = "2001",
    id = 1
  )

  private fun respondWith(code: Int): CapturingSlot<QueuePayload> {
    val payload = slot<QueuePayload>()
    coEvery { queueApi.queue(capture(payload)) } returns QueueResponse(code)
    return payload
  }

  @Test
  fun `queueAlbum sends the album's paths and reports how many were queued`() =
    runTest(testDispatcher) {
      val paths = listOf("a.mp3", "b.mp3", "c.mp3")
      every { trackRepository.getTrackPaths(TrackQuery.Album("Lateralus", "Tool")) } returns paths
      val payload = respondWith(OK)

      val result = handler.queueAlbum(Queue.Next, "Lateralus", "Tool")

      assertThat(result).isEqualTo(Outcome.Success(3))
      assertThat(payload.captured.type).isEqualTo(Queue.NEXT)
      assertThat(payload.captured.data).isEqualTo(paths)
      assertThat(payload.captured.play).isNull()
    }

  @Test
  fun `a non-success code from the plugin is an operation failure`() = runTest(testDispatcher) {
    every { trackRepository.getTrackPaths(any()) } returns listOf("a.mp3")
    respondWith(FAILED)

    val result = handler.queueAlbum(Queue.Next, "Lateralus", "Tool")

    assertThat(result).isEqualTo(Outcome.Failure(AppError.OperationFailed))
  }

  /**
   * The IOException is swallowed by the inner queue(), which reports false, so this surfaces as
   * OperationFailed rather than NetworkUnavailable. Pinning it so the distinction is not lost.
   */
  @Test
  fun `a network failure while queueing is an operation failure`() = runTest(testDispatcher) {
    every { trackRepository.getTrackPaths(any()) } returns listOf("a.mp3")
    coEvery { queueApi.queue(any()) } throws IOException("socket closed")

    val result = handler.queueAlbum(Queue.Next, "Lateralus", "Tool")

    assertThat(result).isEqualTo(Outcome.Failure(AppError.OperationFailed))
  }

  @Test
  fun `a failure reading paths from the database is a network failure`() = runTest(testDispatcher) {
    every { trackRepository.getTrackPaths(any()) } throws IOException("db gone")

    val result = handler.queueAlbum(Queue.Next, "Lateralus", "Tool")

    assertThat(result).isEqualTo(Outcome.Failure(AppError.NetworkUnavailable))
  }

  @Test
  fun `queueArtist queries by artist`() = runTest(testDispatcher) {
    every { trackRepository.getTrackPaths(TrackQuery.Artist("Tool")) } returns listOf("a", "b")
    val payload = respondWith(OK)

    val result = handler.queueArtist(Queue.Last, "Tool")

    assertThat(result).isEqualTo(Outcome.Success(2))
    assertThat(payload.captured.type).isEqualTo(Queue.LAST)
  }

  @Test
  fun `queueGenre queries by genre`() = runTest(testDispatcher) {
    every { trackRepository.getTrackPaths(TrackQuery.Genre("Rock")) } returns listOf("a")
    val payload = respondWith(OK)

    val result = handler.queueGenre(Queue.Last, "Rock")

    assertThat(result).isEqualTo(Outcome.Success(1))
    assertThat(payload.captured.type).isEqualTo(Queue.LAST)
  }

  @Test
  fun `queuePath plays a single path now`() = runTest(testDispatcher) {
    val payload = respondWith(OK)

    val result = handler.queuePath("C:\\music\\a.mp3")

    assertThat(result).isEqualTo(Outcome.Success(1))
    assertThat(payload.captured.type).isEqualTo(Queue.NOW)
    assertThat(payload.captured.data).containsExactly("C:\\music\\a.mp3")
  }

  @Test
  fun `queueTrack with AddAll and queueAlbum queues the album and plays the track`() =
    runTest(testDispatcher) {
      val paths = listOf("a.mp3", track.src)
      every { trackRepository.getTrackPaths(TrackQuery.Album("Lateralus", "Tool")) } returns paths
      val payload = respondWith(OK)

      val result = handler.queueTrack(track, Queue.AddAll, queueAlbum = true)

      assertThat(result).isEqualTo(Outcome.Success(2))
      assertThat(payload.captured.type).isEqualTo(Queue.ADD_ALL)
      assertThat(payload.captured.play).isEqualTo(track.src)
    }

  @Test
  fun `queueTrack with AddAll and no album queues the whole library`() = runTest(testDispatcher) {
    every { trackRepository.getTrackPaths(TrackQuery.All) } returns listOf("a", "b", "c")
    val payload = respondWith(OK)

    val result = handler.queueTrack(track, Queue.AddAll, queueAlbum = false)

    assertThat(result).isEqualTo(Outcome.Success(3))
    assertThat(payload.captured.play).isEqualTo(track.src)
  }

  @Test
  fun `PlayAlbum is sent as add-all with the track as the starting point`() =
    runTest(testDispatcher) {
      every {
        trackRepository.getTrackPaths(TrackQuery.Album("Lateralus", "Tool"))
      } returns listOf("a", "b")
      val payload = respondWith(OK)

      handler.queueTrack(track, Queue.PlayAlbum)

      assertThat(payload.captured.type).isEqualTo(Queue.ADD_ALL)
      assertThat(payload.captured.play).isEqualTo(track.src)
    }

  @Test
  fun `PlayArtist is sent as add-all scoped to the artist`() = runTest(testDispatcher) {
    every { trackRepository.getTrackPaths(TrackQuery.Artist("Tool")) } returns listOf("a", "b")
    val payload = respondWith(OK)

    handler.queueTrack(track, Queue.PlayArtist)

    assertThat(payload.captured.type).isEqualTo(Queue.ADD_ALL)
    assertThat(payload.captured.play).isEqualTo(track.src)
  }

  @Test
  fun `any other action queues just the track and never touches the database`() =
    runTest(testDispatcher) {
      val payload = respondWith(OK)

      val result = handler.queueTrack(track, Queue.Next)

      assertThat(result).isEqualTo(Outcome.Success(1))
      assertThat(payload.captured.type).isEqualTo(Queue.NEXT)
      assertThat(payload.captured.data).containsExactly(track.src)
      assertThat(payload.captured.play).isNull()
    }

  @Test
  fun `queueTrack without an action falls back to the configured default`() =
    runTest(testDispatcher) {
      every { settings.libraryTrackDefaultActionFlow } returns flowOf(TrackAction.QueueLast)
      val payload = respondWith(OK)

      handler.queueTrack(track)

      assertThat(payload.captured.type).isEqualTo(Queue.LAST)
    }
}
