package com.kelsos.mbrc.content.library.genres

import androidx.paging.PagingData
import arrow.core.Try
import com.kelsos.mbrc.networking.ApiBase
import com.kelsos.mbrc.networking.protocol.Protocol
import com.kelsos.mbrc.utilities.AppCoroutineDispatchers
import com.kelsos.mbrc.utilities.epoch
import com.kelsos.mbrc.utilities.paged
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.withContext

class GenreRepositoryImpl(
  private val api: ApiBase,
  private val dao: GenreDao,
  private val dispatchers: AppCoroutineDispatchers
) : GenreRepository {

  override suspend fun count(): Long = withContext(dispatchers.database) { dao.count() }

  override fun getAll(): Flow<PagingData<Genre>> = dao.getAll().paged {
    it.toGenre()
  }

  override suspend fun getRemote(): Try<Unit> = Try {
    val stored = dao.genres().associate { it.genre to it.id }
    withContext(dispatchers.network) {
      val added = epoch()
      api.getAllPages(Protocol.LibraryBrowseGenres, GenreDto::class)
        .onCompletion {
          withContext(dispatchers.database) {
            dao.removePreviousEntries(added)
          }
        }
        .collect { genres ->
          val items = genres.map {
            it.toEntity().apply {
              dateAdded = added

              val id = stored[it.genre]
              if (id != null) {
                this.id = id
              }
            }
          }
          withContext(dispatchers.database) {
            dao.insertAll(items)
          }
        }
    }
  }

  override fun search(term: String): Flow<PagingData<Genre>> = dao.search(term).paged {
    it.toGenre()
  }

  override suspend fun cacheIsEmpty(): Boolean =
    withContext(dispatchers.database) { dao.count() == 0L }
}
