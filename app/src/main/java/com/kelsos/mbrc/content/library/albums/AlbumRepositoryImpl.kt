package com.kelsos.mbrc.content.library.albums

import androidx.paging.PagingData
import arrow.core.Try
import com.kelsos.mbrc.covers.AlbumCover
import com.kelsos.mbrc.covers.CachedAlbumCover
import com.kelsos.mbrc.networking.ApiBase
import com.kelsos.mbrc.networking.protocol.Protocol
import com.kelsos.mbrc.utilities.AppCoroutineDispatchers
import com.kelsos.mbrc.utilities.epoch
import com.kelsos.mbrc.utilities.paged
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.withContext

class AlbumRepositoryImpl(
  private val dao: AlbumDao,
  private val api: ApiBase,
  private val dispatchers: AppCoroutineDispatchers
) : AlbumRepository {
  override suspend fun count(): Long = withContext(dispatchers.database) { dao.count() }

  override fun getAlbumsByArtist(artist: String): Flow<PagingData<Album>> =
    dao.getAlbumsByArtist(artist).paged { it.toAlbum() }

  override fun getAll(): Flow<PagingData<Album>> = dao.getAll().paged { it.toAlbum() }

  override suspend fun getRemote(): Try<Unit> = Try {
    val added = epoch()
    val default = CachedAlbumCover(0, null)
    val cached = dao.all().associate { entry ->
      entry.album + entry.artist to CachedAlbumCover(entry.id, entry.cover)
    }
    withContext(dispatchers.network) {
      api.getAllPages(Protocol.LibraryBrowseAlbums, AlbumDto::class)
        .onCompletion {
          withContext(dispatchers.database) {
            dao.removePreviousEntries(added)
          }
        }
        .collect { albums ->
          val list = albums.map { dto ->
            dto.toEntity().apply {
              dateAdded = added
              val key = dto.album + dto.artist

              if (cached.containsKey(key)) {
                val cachedAlbum = cached.getOrDefault(key, default)
                id = cachedAlbum.id
                cover = cachedAlbum.cover
              }
            }
          }
          withContext(dispatchers.database) {
            dao.insert(list)
          }
        }
    }
  }

  override fun search(term: String): Flow<PagingData<Album>> =
    dao.search(term).paged { it.toAlbum() }

  override suspend fun cacheIsEmpty(): Boolean =
    withContext(dispatchers.database) { dao.count() == 0L }

  override suspend fun updateCovers(updated: List<AlbumCover>) {
    dao.updateCovers(updated)
  }

  override suspend fun getCovers(): List<AlbumCover> {
    return dao.getCovers()
  }
}
