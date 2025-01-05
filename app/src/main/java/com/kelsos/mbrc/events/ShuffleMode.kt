package com.kelsos.mbrc.events

sealed class ShuffleMode(
  val mode: String,
) {
  data object Off : ShuffleMode(OFF)

  data object AutoDJ : ShuffleMode(AUTO_DJ)

  data object Shuffle : ShuffleMode(SHUFFLE)

  companion object {
    const val OFF = "off"
    const val AUTO_DJ = "autodj"
    const val SHUFFLE = "shuffle"

    fun fromString(string: String): ShuffleMode =
      when (string) {
        AUTO_DJ -> AutoDJ
        SHUFFLE -> Shuffle
        else -> Off
      }
  }
}
