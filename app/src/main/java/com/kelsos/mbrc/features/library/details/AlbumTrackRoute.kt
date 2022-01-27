package com.kelsos.mbrc.features.library.details

import androidx.compose.runtime.Composable
import androidx.paging.compose.collectAsLazyPagingItems
import com.kelsos.mbrc.features.library.TracksScreen
import org.koin.androidx.compose.koinViewModel

@Composable
fun AlbumTrackRoute() {
  val vm = koinViewModel<AlbumTrackViewModel>()
  TracksScreen(tracks = vm.tracks.collectAsLazyPagingItems(), sync = { /*TODO*/ }, action = {
      _,
      _,
    ->
  })
}