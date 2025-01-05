package com.kelsos.mbrc.features.playlists

import com.kelsos.mbrc.common.data.Mapper

object PlaylistDtoMapper :
  Mapper<PlaylistDto, PlaylistEntity> {
  override fun map(from: PlaylistDto): PlaylistEntity =
    PlaylistEntity(
      name = from.name,
      url = from.url,
    )
}

fun PlaylistDto.toEntity(): PlaylistEntity = PlaylistDtoMapper.map(this)
