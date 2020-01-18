package com.kelsos.mbrc.features.library.repositories

import android.app.Application
import com.kelsos.mbrc.common.utilities.AppCoroutineDispatchers
import com.kelsos.mbrc.features.library.data.AlbumCover
import com.kelsos.mbrc.features.library.data.Cover
import com.kelsos.mbrc.features.library.data.key
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

class CoverCache(
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
    val map = withContext(dispatchers.database) { albumRepository.getCovers() }
    withContext(dispatchers.network) {
      val updated = mutableListOf<AlbumCover>()
      api.getAll(Protocol.LibraryCover, map, Cover::class).onCompletion {
        Timber.v("Updated ${updated.size} albums")
        albumRepository.updateCovers(updated)
      }.collect { (payload, response) ->
        val status = response.status
        val cover = response.cover
        val hash = response.hash
        if (status == 200 && !cover.isNullOrEmpty() && !hash.isNullOrEmpty()) {
          try {
            val file = File(cache, payload.key())
            val decodeBase64 = cover.decodeBase64()
            if (decodeBase64 != null) {
              file.sink().buffer().use { sink -> sink.write(decodeBase64) }
            }
            updated.add(payload.copy(hash = hash))
          } catch (e: Exception) {
            Timber.e(e)
          }
        }
      }
    }
  }
}
