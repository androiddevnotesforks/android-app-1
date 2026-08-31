package com.kelsos.mbrc.adapters

import android.app.Application
import android.graphics.Bitmap
import android.util.Base64
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.kelsos.mbrc.core.common.test.testDispatcher
import com.kelsos.mbrc.core.common.test.testDispatchers
import com.kelsos.mbrc.core.networking.api.PlaybackApi
import com.kelsos.mbrc.core.networking.protocol.payloads.CoverPayload
import io.mockk.coEvery
import io.mockk.mockk
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CoverHandlerImplTest {
  private val playbackApi: PlaybackApi = mockk()
  private lateinit var app: Application

  @Before
  fun setUp() {
    app = ApplicationProvider.getApplicationContext()
    app.filesDir.listFiles()?.forEach { it.deleteRecursively() }
    app.cacheDir.listFiles()?.forEach { it.deleteRecursively() }
  }

  private fun handler() = CoverHandlerImpl(app, playbackApi, testDispatchers)

  private fun encodedCover(width: Int = 2, height: Int = 2): String {
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val stream = ByteArrayOutputStream()
    bitmap.compress(Bitmap.CompressFormat.JPEG, 100, stream)
    return Base64.encodeToString(stream.toByteArray(), Base64.DEFAULT)
  }

  private fun storedCovers(): List<File> = app.filesDir.listFiles().orEmpty().filter { it.isFile }

  @Test
  fun `a cover is stored and its uri returned`() = runTest(testDispatcher) {
    coEvery { playbackApi.getCover() } returns CoverPayload(CoverPayload.SUCCESS, encodedCover())

    val uri = handler().fetchAndStoreCover()

    assertThat(uri).startsWith("file://")
    assertThat(storedCovers()).hasSize(1)
    assertThat(uri).endsWith(storedCovers().single().name)
  }

  @Test
  fun `the stored cover is named after its content so the same cover is reused`() =
    runTest(testDispatcher) {
      val cover = encodedCover()
      coEvery { playbackApi.getCover() } returns CoverPayload(CoverPayload.SUCCESS, cover)
      val handler = handler()

      val first = handler.fetchAndStoreCover()
      val second = handler.fetchAndStoreCover()

      assertThat(second).isEqualTo(first)
      assertThat(storedCovers()).hasSize(1)
    }

  @Test
  fun `a payload carrying no cover yields no uri`() = runTest(testDispatcher) {
    coEvery { playbackApi.getCover() } returns CoverPayload(CoverPayload.SUCCESS, null)

    val uri = handler().fetchAndStoreCover()

    assertThat(uri).isEmpty()
    assertThat(storedCovers()).isEmpty()
  }

  @Test
  fun `a failing request yields no uri instead of throwing`() = runTest(testDispatcher) {
    coEvery { playbackApi.getCover() } throws IOException("socket closed")

    val uri = handler().fetchAndStoreCover()

    assertThat(uri).isEmpty()
  }

  @Test
  fun `clearing covers leaves the cover directory empty`() = runTest(testDispatcher) {
    val handler = handler()
    val coverDir = File(app.filesDir, CoverHandlerImpl.COVER_DIR).apply { mkdirs() }
    File(coverDir, "stale-1").writeText("stale")
    File(coverDir, "stale-2").writeText("stale")

    handler.clearCovers()

    assertThat(coverDir.listFiles().orEmpty()).isEmpty()
  }

  /**
   * Pins current behaviour, which looks unintended: a finished cover is written to `filesDir`
   * itself, while `clearPreviousCovers` only ever prunes the `cover/` subdirectory. Nothing
   * removes the finished files, so a new cover for every track played accumulates indefinitely.
   */
  @Test
  fun `every distinct cover is kept forever`() = runTest(testDispatcher) {
    val handler = handler()

    for (size in 2..5) {
      coEvery { playbackApi.getCover() } returns
        CoverPayload(CoverPayload.SUCCESS, encodedCover(size, size))
      handler.fetchAndStoreCover()
    }

    assertThat(storedCovers().size).isAtLeast(2)
  }
}
