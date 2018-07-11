package com.kelsos.mbrc.ui.navigation.lyrics

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isGone
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.kelsos.mbrc.databinding.FragmentLyricsBinding
import org.koin.androidx.viewmodel.ext.android.viewModel

class LyricsFragment : Fragment() {

  private val viewModel: LyricsViewModel by viewModel()
  private val lyricsAdapter: LyricsAdapter by lazy { LyricsAdapter() }
  private var _binding: FragmentLyricsBinding? = null
  private val binding get() = _binding!!

  override fun onCreateView(
    inflater: LayoutInflater,
    container: ViewGroup?,
    savedInstanceState: Bundle?
  ): View {
    _binding = FragmentLyricsBinding.inflate(inflater, container, false)
    return binding.root
  }

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)
    val lyricsRecycler = binding.lyricsLyricsList
    val layoutManager = LinearLayoutManager(requireContext())
    val adapter = LyricsAdapter()
    lyricsRecycler.setHasFixedSize(true)
    lyricsRecycler.layoutManager = layoutManager
    lyricsRecycler.adapter = adapter

    viewModel.lyricsLiveDataProvider.observe(this) { lyrics ->
      binding.lyricsEmptyGroup.isGone = lyrics.isNotEmpty()
      lyricsAdapter.submitList(lyrics)
    }
  }

  override fun onDestroyView() {
    super.onDestroyView()
    _binding = null
  }
}
