package com.kelsos.mbrc.ui.navigation.main

import com.kelsos.mbrc.annotations.PlayerState.State
import com.kelsos.mbrc.annotations.Repeat.Mode
import com.kelsos.mbrc.domain.TrackInfo
import com.kelsos.mbrc.enums.LfmStatus
import com.kelsos.mbrc.events.ui.ShuffleChange.ShuffleState
import com.kelsos.mbrc.events.ui.UpdatePosition
import com.kelsos.mbrc.mvp.BaseView
import com.kelsos.mbrc.mvp.Presenter

interface MainView : BaseView {

  fun updateShuffleState(@ShuffleState shuffleState: String)

  fun updateRepeat(@Mode mode: String)

  fun updateVolume(volume: Int, mute: Boolean)

  fun updatePlayState(@State state: String)

  fun updateTrackInfo(info: TrackInfo)

  fun updateConnection(status: Int)

  fun updateScrobbleStatus(active: Boolean)

  fun updateLfmStatus(status: LfmStatus)

  fun updateCover()

  fun updateProgress(position: UpdatePosition)
}


interface MainViewPresenter : Presenter<MainView> {
  fun load()
  fun requestNowPlayingPosition()
  fun toggleScrobbling()
  fun seek(position: Int)
  fun play()
  fun previous()
  fun next()
  fun stop(): Boolean
  fun mute()
  fun shuffle()
  fun repeat()
  fun changeVolume(value: Int)
  fun lfmLove(): Boolean
}