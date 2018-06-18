package com.kelsos.mbrc.ui.navigation.player

import android.app.Dialog
import android.os.Bundle
import androidx.fragment.app.DialogFragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.kelsos.mbrc.R
import com.kelsos.mbrc.content.activestatus.PlayerStatusModel
import com.kelsos.mbrc.databinding.DialogVolumeBinding
import com.kelsos.mbrc.di.inject
import com.kelsos.mbrc.extensions.setIcon
import com.kelsos.mbrc.extensions.setStatusColor
import toothpick.Toothpick
import javax.inject.Inject

class VolumeDialog : DialogFragment(), VolumeView {

  @Inject
  lateinit var presenter: VolumeDialogPresenter

  private var _binding: DialogVolumeBinding? = null
  private val binding get() = _binding!!

  override fun onCreate(savedInstanceState: Bundle?) {
    val scope = Toothpick.openScopes(requireActivity().application, this)
    scope.installModules(volumeDialogModule)
    super.onCreate(savedInstanceState)
    scope.inject(this)
  }

  override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
    _binding = DialogVolumeBinding.inflate(requireActivity().layoutInflater)
    binding.volumeDialogVolume.setOnSeekBarChangeListener { volume ->
      presenter.changeVolume(volume)
    }
    binding.volumeDialogMute.setOnClickListener { presenter.mute() }

    val dialog = MaterialAlertDialogBuilder(requireContext())
      .setView(binding.root)
      .setOnDismissListener {
        presenter.detach()
      }.show()

    presenter.attach(this)

    return dialog
  }

  override fun onDestroyView() {
    super.onDestroyView()
    _binding = null
  }

  override fun onDestroy() {
    super.onDestroy()
    Toothpick.closeScope(this)
  }

  override fun update(playerStatus: PlayerStatusModel) {
    binding.volumeDialogMute.setIcon(
      enabled = playerStatus.mute,
      onRes = R.drawable.ic_volume_up_black_24dp,
      offRes = R.drawable.ic_volume_off_black_24dp
    )
    binding.volumeDialogMute.setStatusColor(playerStatus.mute)
    binding.volumeDialogVolume.progress = if (playerStatus.mute) 0 else playerStatus.volume
  }
}
