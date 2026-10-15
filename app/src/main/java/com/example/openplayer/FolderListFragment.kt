package com.example.openplayer

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.openplayer.databinding.FragmentListBinding

class FolderListFragment : Fragment() {

    private var _b: FragmentListBinding? = null
    private lateinit var adapter: FolderAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _b = FragmentListBinding.inflate(inflater, container, false)
        return _b!!.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val b = _b ?: return

        adapter = FolderAdapter(
            onOpen = { folder ->
                startActivity(
                    Intent(requireContext(), FolderActivity::class.java)
                        .putExtra(FolderActivity.EXTRA_ID, folder.id)
                        .putExtra(FolderActivity.EXTRA_NAME, folder.name)
                )
            },
            onLongPress = { folder ->
                adapter.startSelecting(folder.id)
            },
            onSelectionChanged = { }
        )

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
        Thread {
            val folders = VideoRepository.folders(VideoRepository.load(ctx))
            activity?.runOnUiThread {
                val b = _b ?: return@runOnUiThread
                if (folders.isEmpty()) {
                    showEmpty(getString(R.string.no_videos), false)
                } else {
                    b.emptyBox.visibility = View.GONE
                    b.list.visibility = View.VISIBLE
                    adapter.submit(folders, FolderPrefs.hidden(ctx))
                }
            }
        }.start()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _b = null
    }
}
