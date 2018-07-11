package com.kelsos.mbrc.content.library.artists

import androidx.paging.PagingData
import com.kelsos.mbrc.networking.ApiBase
import com.kelsos.mbrc.networking.protocol.Protocol
import com.kelsos.mbrc.utilities.AppCoroutineDispatchers
import com.kelsos.mbrc.utilities.epoch
import com.kelsos.mbrc.utilities.paged
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.withContext

class ArtistRepositoryImpl(
  private val dao: ArtistDao,
  private val api: ApiBase,
  private val dispatchers: AppCoroutineDispatchers
) : ArtistRepository {

  private val mapper = ArtistDtoMapper()

  override suspend fun count(): Long = withContext(dispatchers.database) {
    dao.count()
  }

  override suspend fun getArtistByGenre(genre: String): Flow<PagingData<Artist>> =
    withContext(dispatchers.database) {
      dao.getArtistByGenre(genre).paged()
    }

  override fun getAll(): Flow<PagingData<Artist>> = dao.getAll().paged()

  override suspend fun getRemote() {
    withContext(dispatchers.network) {
      val added = epoch()
      api.getAllPages(Protocol.LibraryBrowseArtists, ArtistDto::class)
        .onCompletion {
          dao.removePreviousEntries(added)
        }
        .collect { artists ->
          val data = artists.map { mapper.map(it).apply { dateAdded = added } }
          dao.insertAll(data)
        }
    }
  }

  override fun search(term: String): Flow<PagingData<Artist>> =
    dao.search(term).paged()

  override suspend fun getAlbumArtistsOnly(): Flow<PagingData<Artist>> =
    dao.getAlbumArtists().paged()

  override suspend fun cacheIsEmpty(): Boolean = withContext(dispatchers.database) {
    dao.count() == 0L
  }
}
