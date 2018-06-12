package com.kelsos.mbrc.content.library.covers

import android.app.Application
import com.kelsos.mbrc.content.library.albums.AlbumRepository
import com.kelsos.mbrc.networking.ApiBase
import com.kelsos.mbrc.networking.protocol.Protocol
import com.kelsos.mbrc.utilities.AppCoroutineDispatchers
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.withContext
import okio.ByteString.Companion.decodeBase64
import okio.buffer
import okio.sink
import timber.log.Timber
import java.io.File
import javax.inject.Inject

class CoverCache
@Inject
constructor(
  private val albumRepository: AlbumRepository,
  private val api: ApiBase,
  private val dispatchers: AppCoroutineDispatchers,
  app: Application
) {

  private val cache = File(app.cacheDir, "covers")
  init {
    if (!cache.exists()) {
      cache.mkdir()
    }
  }

  suspend fun cache() {
    val covers = withContext(dispatchers.database) {
      val albumCovers = mutableListOf<AlbumCover>()
      val covers = albumRepository.getCovers()
      withContext(dispatchers.network) {
        val files = cache.listFiles()?.map { it.nameWithoutExtension } ?: emptyList()

        for (cover in covers) {
          if (cover.hash.isNullOrBlank() || files.contains(cover.key())) {
            albumCovers.add(cover)
          } else {
            albumCovers.add(cover.copy(hash = ""))
          }
        }
      }
      albumCovers
    }
    withContext(dispatchers.network) {
      val updated = mutableListOf<AlbumCover>()
      api.getAll(Protocol.LibraryCover, covers, Cover::class).onCompletion {
        Timber.v("Updated covers for ${updated.size} albums")
        withContext(dispatchers.database) {
          albumRepository.updateCovers(updated)
        }
        val storedCovers = albumRepository.getCovers().map { it.key() }
        val coverFiles = cache.listFiles()
        if (coverFiles != null) {
          val notInDb = coverFiles.filter { !storedCovers.contains(it.nameWithoutExtension) }
          Timber.v("deleting ${notInDb.size} covers no longer in db")
          for (file in notInDb) {
            runCatching { file.delete() }
          }
        }
      }.collect { (payload, response) ->
        val status = response.status
        if (status == 304) {
          Timber.v("cover for $payload did not change")
          return@collect
        }

        val cover = response.cover
        val hash = response.hash

        if (status == 200 && !cover.isNullOrEmpty() && !hash.isNullOrEmpty()) {
          val result = runCatching {
            val file = File(cache, payload.key())
            val decodeBase64 = cover.decodeBase64()
            if (decodeBase64 != null) {
              file.sink().buffer().use { sink -> sink.write(decodeBase64) }
            }
            updated.add(payload.copy(hash = hash))
          }

          if (result.isFailure) {
            Timber.e(result.exceptionOrNull(), "Could not save cover for $payload")
          }
        }
      }
    }
  }
}
