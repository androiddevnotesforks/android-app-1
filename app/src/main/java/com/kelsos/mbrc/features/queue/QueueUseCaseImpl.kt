package com.kelsos.mbrc.features.queue

import com.kelsos.mbrc.common.Meta.ALBUM
import com.kelsos.mbrc.common.Meta.ARTIST
import com.kelsos.mbrc.common.Meta.GENRE
import com.kelsos.mbrc.common.Meta.TRACK
import com.kelsos.mbrc.common.Meta.Type
import com.kelsos.mbrc.common.utilities.AppCoroutineDispatchers
import com.kelsos.mbrc.features.library.repositories.AlbumRepository
import com.kelsos.mbrc.features.library.repositories.ArtistRepository
import com.kelsos.mbrc.features.library.repositories.GenreRepository
import com.kelsos.mbrc.features.library.repositories.TrackRepository
import com.kelsos.mbrc.features.player.cover.CoverPayload
import com.kelsos.mbrc.features.queue.Queue.Action
import com.kelsos.mbrc.features.queue.Queue.DEFAULT
import com.kelsos.mbrc.networking.ApiBase
import com.kelsos.mbrc.networking.protocol.Protocol
import com.kelsos.mbrc.preferences.DefaultActionPreferenceStore
import kotlinx.coroutines.withContext
import timber.log.Timber

class QueueUseCaseImpl(
  private val trackRepository: TrackRepository,
  private val genreRepository: GenreRepository,
  private val artistRepository: ArtistRepository,
  private val albumRepository: AlbumRepository,
  private val settings: DefaultActionPreferenceStore,
  private val dispatchers: AppCoroutineDispatchers,
  private val api: ApiBase
) : QueueUseCase {
  override suspend fun queue(
    id: Long,
    @Type meta: Int,
    @Action action: String
  ) = withContext(dispatchers.network) {
    val selectedAction = if (action == DEFAULT) settings.defaultAction else action
    val (sendAction, paths, path) = when (meta) {
      GENRE -> QueueData(selectedAction, tracksForGenre(id))
      ARTIST -> QueueData(selectedAction, tracksForArtist(id))
      ALBUM -> QueueData(selectedAction, tracksForAlbum(id))
      TRACK -> tracks(id, action)
      else -> throw IllegalArgumentException("Invalid value $meta")
    }

    val success = queueRequest(sendAction, paths, path)
    return@withContext QueueResult(success, paths.size)
  }

  override suspend fun queuePath(path: String): QueueResult {
    var success = false
    try {
      success = queueRequest(Queue.NOW, listOf(path), path)
    } catch (e: Exception) {
      Timber.e(e)
    }
    return QueueResult(success, 1)
  }

  private suspend fun tracksForGenre(id: Long): List<String> =
    withContext(dispatchers.database) {
      val genre = genreRepository.getById(id)
      if (genre != null) {
        trackRepository.getGenreTrackPaths(genre = genre.genre)
      } else {
        emptyList()
      }
    }

  private suspend fun tracksForArtist(id: Long): List<String> = withContext(dispatchers.database) {
    val artist = artistRepository.getById(id)
    if (artist != null) {
      trackRepository.getArtistTrackPaths(artist = artist.artist)
    } else {
      emptyList()
    }
  }

  private suspend fun tracksForAlbum(id: Long): List<String> = withContext(dispatchers.database) {
    val album = albumRepository.getById(id)
    if (album != null) {
      trackRepository.getAlbumTrackPaths(album.album, album.artist)
    } else {
      emptyList()
    }
  }

  private suspend fun tracks(
    id: Long,
    @Action action: String
  ): QueueData = withContext(dispatchers.database) {
    val track = trackRepository.getById(id)
      ?: throw IllegalArgumentException("$action is not supported")

    when (action) {
      Queue.ADD_ALL -> QueueData(action, trackRepository.getAllTrackPaths(), track.src)
      Queue.PLAY_ALBUM -> QueueData(
        Queue.ADD_ALL,
        trackRepository.getAlbumTrackPaths(track.album, track.albumArtist),
        track.src
      )
      Queue.PLAY_ARTIST -> QueueData(
        action = Queue.ADD_ALL,
        trackRepository.getArtistTrackPaths(track.artist),
        track.src

      )
      else -> QueueData(action, listOf(track.src))
    }
  }

  private suspend fun queueRequest(
    @Queue.Action type: String,
    tracks: List<String>,
    play: String? = null
  ): Boolean {
    return withContext(dispatchers.network) {
      Timber.v("Queueing ${tracks.size} $type")
      try {
        val response = api.getItem(
          Protocol.NowPlayingQueue,
          QueueResponse::class,
          QueuePayload(type, tracks, play)
        )

        return@withContext response.code == CoverPayload.SUCCESS
      } catch (e: Exception) {
        Timber.e(e)
        return@withContext false
      }
    }
  }
}
