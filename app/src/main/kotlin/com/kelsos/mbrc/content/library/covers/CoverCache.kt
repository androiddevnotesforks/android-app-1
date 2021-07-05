package com.kelsos.mbrc.content.library.covers

import android.app.Application
import com.kelsos.mbrc.content.library.albums.AlbumRepository
import com.kelsos.mbrc.di.modules.AppDispatchers
import com.kelsos.mbrc.networking.ApiBase
import com.kelsos.mbrc.networking.protocol.Protocol
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
  private val dispatchers: AppDispatchers,
  app: Application
) {

  private val cache = File(app.cacheDir, "covers")
  init {
    if (!cache.exists()) {
      cache.mkdir()
    }
  }

  suspend fun cache() {
    val map = withContext(dispatchers.db) { albumRepository.getCovers() }
    withContext(dispatchers.io) {
      val updated = mutableListOf<AlbumCover>()
      api.getAll(Protocol.LibraryCover, map, Cover::class).onCompletion {
        Timber.v("Updated ${updated.size} albums")
        albumRepository.updateCovers(updated)
      }.collect { (payload, response) ->
        val coverExists = !response.cover.isNullOrEmpty()
        if (response.status == 200 && coverExists && !response.hash.isNullOrEmpty()) {
          try {
            val file = File(cache, payload.key())
            val decodeBase64 = response.cover?.decodeBase64()
            if (decodeBase64 != null) {
              file.sink().buffer().use { sink -> sink.write(decodeBase64) }
            }
            updated.add(payload.copy(hash = response.hash))
          } catch (e: Exception) {
            Timber.e(e)
          }
        }
      }
    }
  }
}
