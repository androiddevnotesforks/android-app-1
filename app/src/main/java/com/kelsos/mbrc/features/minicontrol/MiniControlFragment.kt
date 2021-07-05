package com.kelsos.mbrc.features.minicontrol

import android.graphics.Bitmap
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.kelsos.mbrc.R
import com.kelsos.mbrc.common.ui.extensions.getDimens
import com.kelsos.mbrc.content.activestatus.PlayerState
import com.kelsos.mbrc.databinding.FragmentMiniControlBinding
import com.squareup.picasso.Picasso
import org.koin.androidx.viewmodel.ext.android.viewModel
import java.io.File

class MiniControlFragment : Fragment() {

  private val viewModel: MiniControlViewModel by viewModel()
  private var _binding: FragmentMiniControlBinding? = null
  private val binding get() = _binding!!

  private fun onControlClick() {
    findNavController().navigate(R.id.main_navigation_fragment)
  }

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)

    val playPause = binding.miniControlPlayPause
    viewModel.playerStatus.observe(viewLifecycleOwner) { status ->
      when (status.state) {
        PlayerState.PLAYING -> playPause.setImageResource(R.drawable.ic_action_pause)
        else -> playPause.setImageResource(R.drawable.ic_action_play)
      }
    }

    viewModel.playingTrack.observe(viewLifecycleOwner) { track ->
      binding.miniControlArtist.text = getString(
        R.string.mini_control__artist,
        track.artist,
        track.album
      )
      binding.miniControlTitle.text = track.title
      updateCover(track.coverUrl)
    }

    viewModel.trackPosition.observe(viewLifecycleOwner) { position ->
      binding.miniControlProgress.max = position.total.toInt()
      binding.miniControlProgress.progress = position.current.toInt()
    }
    binding.miniControl.setOnClickListener { onControlClick() }
    binding.miniControlPlayNext.setOnClickListener { viewModel.next() }
    playPause.setOnClickListener { viewModel.playPause() }
    binding.miniControlPlayPrevious.setOnClickListener { viewModel.previous() }
  }

  override fun onCreateView(
    inflater: LayoutInflater,
    container: ViewGroup?,
    savedInstanceState: Bundle?
  ): View {
    _binding = FragmentMiniControlBinding.inflate(inflater, container, false)
    return binding.root
  }

  override fun onDestroyView() {
    super.onDestroyView()
    _binding = null
  }

  private fun updateCover(path: String) {
    val file = File(path)

    if (file.exists()) {
      val dimens = requireContext().getDimens()
      Picasso.get()
        .load(file)
        .noFade()
        .config(Bitmap.Config.RGB_565)
        .resize(dimens, dimens)
        .centerCrop()
        .into(binding.miniControlCover)
    } else {
      binding.miniControlCover.setImageResource(R.drawable.ic_image_no_cover)
    }
  }
}
