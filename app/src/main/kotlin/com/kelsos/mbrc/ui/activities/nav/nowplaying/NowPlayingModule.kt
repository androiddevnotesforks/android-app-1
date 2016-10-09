package com.kelsos.mbrc.ui.activities.nav.nowplaying

import toothpick.config.Module

class NowPlayingModule private constructor() : Module() {
  init {
    bind(NowPlayingPresenter::class.java).to(NowPlayingPresenterImpl::class.java).singletonInScope()
  }

  companion object {

    fun create(): NowPlayingModule {
      return NowPlayingModule()
    }
  }
}
