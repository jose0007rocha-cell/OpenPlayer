package com.josercha.openplayer

import android.content.Intent
import android.graphics.RenderEffect
import android.graphics.Shader
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.FrameLayout
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.josercha.openplayer.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var b: ActivityMainBinding
    private lateinit var adapter: FolderAdapter
    private var themeSig = ""
    private var allVideos: List<Video> = emptyList()
    private var menuOpen = false

    private var permDeniedForever = false

    private val permLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            // Negou e o Android não vai mais perguntar: só resta liberar nas configurações do app
            permDeniedForever = !granted && !shouldShowRequestPermissionRationale(VideoRepository.permission)
            load()
        }

    private val deleter = DeleteHelper(this) { deleted, failed, cancelled ->
        when {
            deleted > 0 -> {
                Toast.makeText(this, R.string.deleted, Toast.LENGTH_SHORT).show()
                if (failed > 0) Toast.makeText(this, R.string.delete_fail, Toast.LENGTH_LONG).show()
            }
            cancelled -> {}
            else -> Toast.makeText(this, R.string.delete_fail, Toast.LENGTH_LONG).show()
        }
        if (!cancelled) exitSelection()
        load()
    }

    private val backCallback = object : OnBackPressedCallback(false) {
        override fun handleOnBackPressed() {
            when {
                menuOpen -> closeMenu()
                adapter.selecting -> exitSelection()
            }
        }
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    fun requestPermission() {
        if (permDeniedForever) {
            startActivity(
                Intent(
                    android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    android.net.Uri.fromParts("package", packageName, null)
                )
            )
        } else {
            permLauncher.launch(VideoRepository.permission)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeManager.apply(this)
        super.onCreate(savedInstanceState)
        ThemeManager.applyBackground(this)
        themeSig = ThemeManager.signature(this)
        b = ActivityMainBinding.inflate(layoutInflater)
        setContentView(b.root)

        // Imagem do tema vai até atrás da barra de status.
        ThemeManager.setupWindow(this, b.root)
        ViewCompat.setOnApplyWindowInsetsListener(b.content) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            (b.menuPanel.layoutParams as FrameLayout.LayoutParams).topMargin = bars.top + dp(52)
            b.menuPanel.requestLayout()
            insets
        }

        adapter = FolderAdapter(
            onOpen = { folder ->
                startActivity(
                    Intent(this, FolderActivity::class.java)
                        .putExtra(FolderActivity.EXTRA_ID, folder.id)
                        .putExtra(FolderActivity.EXTRA_NAME, folder.name)
                )
            },
            onLongPress = { folder -> enterSelection(folder.id) },
            onSelectionChanged = { updateSelectionUi() }
        )
        b.list.layoutManager = LinearLayoutManager(this)
        b.list.adapter = adapter
        onBackPressedDispatcher.addCallback(this, backCallback)

        b.btnGrant.setOnClickListener { requestPermission() }
        b.btnMore.setOnClickListener { openMenu() }
        b.menuOverlay.setOnClickListener { closeMenu() }
        b.menuSelect.setOnClickListener {
            closeMenu()
            enterSelection(null)
        }
        b.menuThemes.setOnClickListener {
            closeMenu()
            startActivity(Intent(this, ThemeActivity::class.java))
        }
        b.menuSettings.setOnClickListener {
            closeMenu()
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        b.btnCloseSel.setOnClickListener { exitSelection() }
        b.btnHide.setOnClickListener { setSelectedHidden(true) }
        b.btnShow.setOnClickListener { setSelectedHidden(false) }
        b.btnDelete.setOnClickListener { confirmDelete() }
        updateSelectionUi()

        if (!VideoRepository.hasPermission(this)) requestPermission()
    }

    override fun onResume() {
        super.onResume()
        if (themeSig != ThemeManager.signature(this)) {
            recreate()
            return
        }
        load()
    }

    // ---------- Menu lateral (⋮) ----------

    private fun openMenu() {
        menuOpen = true
        b.menuOverlay.alpha = 0f
        b.menuOverlay.visibility = View.VISIBLE
        b.menuOverlay.animate().alpha(1f).setDuration(150).start()
        if (Build.VERSION.SDK_INT >= 31) {
            b.content.setRenderEffect(
                RenderEffect.createBlurEffect(18f, 18f, Shader.TileMode.CLAMP)
            )
        }
        updateBack()
    }

    private fun closeMenu() {
        menuOpen = false
        b.menuOverlay.animate().alpha(0f).setDuration(120).withEndAction {
            if (!menuOpen) b.menuOverlay.visibility = View.GONE
        }.start()
        if (Build.VERSION.SDK_INT >= 31) b.content.setRenderEffect(null)
        updateBack()
    }

    private fun updateBack() {
        backCallback.isEnabled = menuOpen || adapter.selecting
    }

    // ---------- Seleção de pastas ----------

    private fun enterSelection(initial: Long?) {
        adapter.startSelecting(initial)
        render()
    }

    private fun exitSelection() {
        adapter.stopSelecting()
        render()
    }

    private fun updateSelectionUi() {
        val sel = adapter.selecting
        b.normalBar.visibility = if (sel) View.GONE else View.VISIBLE
        b.selectionBar.visibility = if (sel) View.VISIBLE else View.GONE
        val n = adapter.selected.size
        b.selTitle.text = resources.getQuantityString(R.plurals.selected_count, n, n)
        for (v in listOf(b.btnHide, b.btnShow, b.btnDelete)) {
            v.isEnabled = n > 0
            v.alpha = if (n > 0) 1f else 0.4f
        }
        updateBack()
    }

    private fun setSelectedHidden(hide: Boolean) {
        FolderPrefs.setHidden(this, adapter.selected.toSet(), hide)
        exitSelection()
    }

    private fun confirmDelete() {
        val n = adapter.selected.size
        if (n == 0) return
        AlertDialog.Builder(this)
            .setTitle(R.string.delete_title)
            .setMessage(resources.getQuantityString(R.plurals.delete_message, n, n))
            .setPositiveButton(R.string.delete) { _, _ -> doDelete() }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun doDelete() {
        val ids = adapter.selected.toSet()
        val uris = allVideos.filter { it.folderId in ids }.map { it.uri }
        if (uris.isEmpty()) {
            exitSelection()
            return
        }
        deleter.delete(uris)
    }

    // ---------- Lista de pastas ----------

    private fun showEmpty(text: String, showButton: Boolean) {
        b.emptyText.text = text
        b.btnGrant.visibility = if (showButton) View.VISIBLE else View.GONE
        b.emptyBox.visibility = View.VISIBLE
        b.list.visibility = View.GONE
    }

    private fun load() {
        if (!VideoRepository.hasPermission(this)) {
            showEmpty(getString(R.string.need_permission), true)
            return
        }
        val ctx = applicationContext
        Thread {
            val videos = VideoRepository.load(ctx)
            runOnUiThread {
                if (isDestroyed) return@runOnUiThread
                allVideos = videos
                render()
            }
        }.start()
    }

    private fun render() {
        val hidden = FolderPrefs.hidden(this)
        val all = VideoRepository.folders(allVideos)
        val shown = if (adapter.selecting) all else all.filter { it.id !in hidden }
        if (shown.isEmpty()) {
            showEmpty(getString(if (all.isEmpty()) R.string.no_videos else R.string.all_hidden), false)
        } else {
            b.emptyBox.visibility = View.GONE
            b.list.visibility = View.VISIBLE
            adapter.submit(shown, hidden)
        }
    }
}
