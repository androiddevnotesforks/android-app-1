package com.kelsos.mbrc.feature.library.data

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.kelsos.mbrc.core.common.test.testDispatcher
import com.kelsos.mbrc.core.common.test.testDispatchers
import com.kelsos.mbrc.core.data.library.album.AlbumCover
import com.kelsos.mbrc.core.data.library.album.AlbumRepository
import com.kelsos.mbrc.core.networking.ApiStatus
import com.kelsos.mbrc.core.networking.api.LibraryApi
import com.kelsos.mbrc.core.networking.client.ResponseWithPayload
import com.kelsos.mbrc.core.networking.dto.AlbumCoverDto
import com.kelsos.mbrc.core.networking.dto.CoverDto
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import java.io.File
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import okio.ByteString.Companion.encodeUtf8
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CoverCacheTest {
  private val albumRepository: AlbumRepository = mockk(relaxed = true)
  private val libraryApi: LibraryApi = mockk()
  private lateinit var app: Application
  private lateinit var cacheDir: File

  private val cover = AlbumCover("Tool", "Lateralus", "hash-1")

  @Before
  fun setUp() {
    app = ApplicationProvider.getApplicationContext()
    cacheDir = File(app.cacheDir, "covers")
    cacheDir.deleteRecursively()
  }

  private fun coverCache() = CoverCache(albumRepository, libraryApi, testDispatchers, app)

  private fun apiReturns(
    vararg responses: Pair<AlbumCover, CoverDto>
  ): Flow<ResponseWithPayload<AlbumCoverDto, CoverDto>> {
    val flow = flowOf(
      *responses
        .map { (c, r) -> ResponseWithPayload(AlbumCoverDto(c.artist, c.album, c.hash), r) }
        .toTypedArray()
    )
    every { libraryApi.getCovers(any(), any()) } returns flow
    return flow
  }

  @Test
  fun `the cache directory is created on construction`() {
    coverCache()

    assertThat(cacheDir.isDirectory).isTrue()
  }

  @Test
  fun `a cover whose file is missing is requested again with its hash cleared`() =
    runTest(testDispatcher) {
      coEvery { albumRepository.getCovers() } returns listOf(cover)
      val sent = slot<List<AlbumCoverDto>>()
      every { libraryApi.getCovers(capture(sent), any()) } returns flowOf()

      coverCache().cache()

      assertThat(sent.captured).hasSize(1)
      assertThat(sent.captured.single().hash).isNull()
    }

  @Test
  fun `a cover already on disk keeps its hash so the plugin can skip it`() =
    runTest(testDispatcher) {
      coEvery { albumRepository.getCovers() } returns listOf(cover)
      val cache = coverCache()
      File(cacheDir, cover.key()).writeText("cached")
      val sent = slot<List<AlbumCoverDto>>()
      every { libraryApi.getCovers(capture(sent), any()) } returns flowOf()

      cache.cache()

      assertThat(sent.captured.single().hash).isEqualTo("hash-1")
    }

  @Test
  fun `a successful response writes the decoded cover to disk and records the hash`() =
    runTest(testDispatcher) {
      coEvery { albumRepository.getCovers() } returns listOf(cover)
      val encoded = "cover-bytes".encodeUtf8().base64()
      apiReturns(cover to CoverDto(ApiStatus.SUCCESS, encoded, "hash-2"))
      val updated = slot<List<AlbumCover>>()
      coEvery { albumRepository.updateCovers(capture(updated)) } returns Unit

      coverCache().cache()

      assertThat(File(cacheDir, cover.key()).readText()).isEqualTo("cover-bytes")
      assertThat(updated.captured.single().hash).isEqualTo("hash-2")
    }

  @Test
  fun `a not-modified response writes nothing and records nothing`() = runTest(testDispatcher) {
    coEvery { albumRepository.getCovers() } returns listOf(cover)
    apiReturns(cover to CoverDto(ApiStatus.NOT_MODIFIED, null, null))
    val updated = slot<List<AlbumCover>>()
    coEvery { albumRepository.updateCovers(capture(updated)) } returns Unit

    coverCache().cache()

    assertThat(File(cacheDir, cover.key()).exists()).isFalse()
    assertThat(updated.captured).isEmpty()
  }

  @Test
  fun `a success carrying no cover data is ignored`() = runTest(testDispatcher) {
    coEvery { albumRepository.getCovers() } returns listOf(cover)
    apiReturns(cover to CoverDto(ApiStatus.SUCCESS, null, "hash-2"))
    val updated = slot<List<AlbumCover>>()
    coEvery { albumRepository.updateCovers(capture(updated)) } returns Unit

    coverCache().cache()

    assertThat(File(cacheDir, cover.key()).exists()).isFalse()
    assertThat(updated.captured).isEmpty()
  }

  /**
   * Undecodable base64 still marks the cover as updated, because the hash is recorded outside the
   * decode check. Pinning current behaviour: a retry would otherwise loop forever on a bad cover.
   */
  @Test
  fun `an undecodable cover records the hash without writing a file`() = runTest(testDispatcher) {
    coEvery { albumRepository.getCovers() } returns listOf(cover)
    apiReturns(cover to CoverDto(ApiStatus.SUCCESS, "!!not-base64!!", "hash-2"))
    val updated = slot<List<AlbumCover>>()
    coEvery { albumRepository.updateCovers(capture(updated)) } returns Unit

    coverCache().cache()

    assertThat(File(cacheDir, cover.key()).exists()).isFalse()
    assertThat(updated.captured.single().hash).isEqualTo("hash-2")
  }

  @Test
  fun `covers on disk that are no longer in the database are deleted`() = runTest(testDispatcher) {
    coEvery { albumRepository.getCovers() } returns listOf(cover)
    val cache = coverCache()
    val orphan = File(cacheDir, "ORPHANED_COVER").apply { writeText("stale") }
    val kept = File(cacheDir, cover.key()).apply { writeText("kept") }
    every { libraryApi.getCovers(any(), any()) } returns flowOf()

    cache.cache()

    assertThat(orphan.exists()).isFalse()
    assertThat(kept.exists()).isTrue()
  }

  @Test
  fun `the database is updated once even when nothing changed`() = runTest(testDispatcher) {
    coEvery { albumRepository.getCovers() } returns emptyList()
    every { libraryApi.getCovers(any(), any()) } returns flowOf()

    coverCache().cache()

    coVerify(exactly = 1) { albumRepository.updateCovers(emptyList()) }
  }
}
