package com.example.openplayer

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.openplayer.databinding.FragmentListBinding

class VideoListFragment : Fragment() {

    companion object {
        private const val ARG_DOWNLOADS = "downloads"

        fun newInstance(downloads: Boolean) = VideoListFragment().apply {
            arguments = bundleOf(ARG_DOWNLOADS to downloads)
        }
    }

    private var _b: FragmentListBinding? = null
    private val adapter = VideoAdapter { requireContext().openVideo(it.uri) }
    private val onlyDownloads: Boolean
        get() = arguments?.getBoolean(ARG_DOWNLOADS, false) ?: false

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _b = FragmentListBinding.inflate(inflater, container, false)
        return _b!!.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val b = _b ?: return
        b.list.layoutManager = LinearLayoutManager(requireContext())
        b.list.adapter = adapter
        b.btnGrant.setOnClickListener { (activity as? MainActivity)?.requestPermission() }
    }

    override fun onResume() {
        super.onResume()
        load()
    }

    private fun showEmpty(text: String, showButton: Boolean) {
        val b = _b ?: return
        b.emptyText.text = text
        b.btnGrant.visibility = if (showButton) View.VISIBLE else View.GONE
        b.emptyBox.visibility = View.VISIBLE
        b.list.visibility = View.GONE
    }

    private fun load() {
        val ctx = context?.applicationContext ?: return
        if (!VideoRepository.hasPermission(ctx)) {
            showEmpty(getString(R.string.need_permission), true)
            return
        }
        val downloads = onlyDownloads
        Thread {
            val all = VideoRepository.load(ctx)
            val list = if (downloads) all.filter { it.isDownload() } else all
            activity?.runOnUiThread {
                val b = _b ?: return@runOnUiThread
                if (list.isEmpty()) {
                    showEmpty(
                        getString(if (downloads) R.string.no_downloads else R.string.no_videos),
                        false
                    )
                } else {
                    b.emptyBox.visibility = View.GONE
                    b.list.visibility = View.VISIBLE
                    adapter.submit(list)
                }
            }
        }.start()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _b = null
    }
}
