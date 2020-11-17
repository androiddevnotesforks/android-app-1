package com.kelsos.mbrc.platform.mediasession

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.media.VolumeProviderCompat
import com.kelsos.mbrc.content.activestatus.livedata.PlayerStatusState
import com.kelsos.mbrc.networking.client.UserActionUseCase
import com.kelsos.mbrc.networking.client.performUserAction
import com.kelsos.mbrc.networking.protocol.Protocol

class RemoteVolumeProvider(
  private val statusLiveDataProvider: PlayerStatusState,
  private val userActionUseCase: UserActionUseCase
) : VolumeProviderCompat(VOLUME_CONTROL_ABSOLUTE, 100, 0), LifecycleOwner {

  private val lifecycleRegistry = LifecycleRegistry(this)
  override fun getLifecycle(): Lifecycle = lifecycleRegistry

  init {
    val volume = statusLiveDataProvider.getValue()?.volume ?: 0
    super.setCurrentVolume(volume)
    statusLiveDataProvider.observe(this) {
      if (super.getCurrentVolume() != it.volume) {
        super.setCurrentVolume(it.volume)
      }
    }
    lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
  }

  override fun onSetVolumeTo(volume: Int) {
    userActionUseCase.performUserAction(Protocol.PlayerVolume, volume)
    currentVolume = volume
  }

  override fun onAdjustVolume(direction: Int) {
    if (direction == 0) {
      return
    }
    val oldVolume = statusLiveDataProvider.getValue()?.volume ?: 0
    val volume = oldVolume.plus(direction)
      .coerceAtLeast(0)
      .coerceAtMost(100)
    userActionUseCase.performUserAction(Protocol.PlayerVolume, volume)
    currentVolume = volume
  }
}
