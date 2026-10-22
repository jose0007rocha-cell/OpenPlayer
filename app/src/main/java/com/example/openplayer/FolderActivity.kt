package com.example.openplayer

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.provider.Settings
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.openplayer.databinding.ActivityFolderBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class FolderActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_ID = "folder_id"
        const val EXTRA_NAME = "folder_name"
    }

    private lateinit var b: ActivityFolderBinding
    private lateinit var adapter: VideoAdapter
    private var folderId = 0L
    private var videos: List<Video> = emptyList()
    private var videoPendenteExclusao: Video? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeManager.apply(this)
        super.onCreate(savedInstanceState)
        ThemeManager.applyBackground(this)
        b = ActivityFolderBinding.inflate(layoutInflater)
        setContentView(b.root)
        ThemeManager.edgeToEdge(this, b.root)

        folderId = intent.getLongExtra(EXTRA_ID, 0L)
        b.folderTitle.text = intent.getStringExtra(EXTRA_NAME) ?: ""
        b.btnBack.setOnClickListener { finish() }

        adapter = VideoAdapter(
            onClick = { openVideo(it.uri) },
            onLongClick = { },
            onMoreOptionsClick = { video, _ -> mostrarMenuOpcoes(video) }
        )
        b.list.adapter = adapter

        b.btnView.setOnClickListener {
            AppSettings.setGridView(this, !AppSettings.gridView(this))
            applyLayout()
        }
        b.btnSort.setOnClickListener { showSortDialog() }
        applyLayout()
    }

    override fun onResume() {
        super.onResume()
        reloadVideos()
        
        // Verifica se voltamos das configurações e temos uma exclusão pendente
        if (videoPendenteExclusao != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (Environment.isExternalStorageManager()) {
                // Temos a permissão total agora! Apaga o vídeo que ficou pendente
                val videoParaApagar = videoPendenteExclusao!!
                videoPendenteExclusao = null
                doDelete(videoParaApagar)
            }
        }
    }

    private fun reloadVideos() {
        if (!VideoRepository.hasPermission(this)) return
        val ctx = applicationContext
        Thread {
            val list = VideoRepository.load(ctx).filter { it.folderId == folderId }
            runOnUiThread {
                videos = list
                render()
            }
        }.start()
    }

    private fun openVideo(uri: Uri) {
        val intent = Intent(this, PlayerActivity::class.java).apply {
            data = uri
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(intent)
    }

    // ---------- Permissão de Acesso Total ----------

    private fun pedirPermissaoTotal(videoPendente: Video? = null) {
        videoPendenteExclusao = videoPendente
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            MaterialAlertDialogBuilder(this)
                .setTitle("Permissão Necessária")
                .setMessage("Para excluir vídeos do Cartão SD, o app precisa de acesso total aos arquivos. Deseja ativar agora?")
                .setPositiveButton("Ativar") { _, _ ->
                    try {
                        val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                        intent.addCategory("android.intent.category.DEFAULT")
                        intent.data = Uri.parse("package:$packageName")
                        startActivity(intent)
                    } catch (e: Exception) {
                        val intent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                        startActivity(intent)
                    }
                }
                .setNegativeButton("Cancelar", null)
                .show()
        } else if (Build.VERSION.SDK_INT == Build.VERSION_CODES.Q) {
            // Android 10 ainda precisa usar o SAF (tela de escolher pasta)
            pedirPermissaoSDCard(videoPendente)
        } else {
            Toast.makeText(this, "Erro: Sem permissão para excluir", Toast.LENGTH_SHORT).show()
        }
    }

    private val sdCardLauncher = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            val treeUri = result.data?.data
            if (treeUri != null) {
                contentResolver.takePersistableUriPermission(
                    treeUri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
                if (videoPendenteExclusao != null) {
                    doDelete(videoPendenteExclusao!!)
                    videoPendenteExclusao = null
                }
            }
        }
    }

    private fun pedirPermissaoSDCard(video: Video?) {
        videoPendenteExclusao = video
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
            putExtra("android.content.extra.SHOW_ADVANCED", true)
            putExtra("android.content.extra.FANCY", true)
            putExtra("android.content.extra.SHOW_FILESIZE", true)
        }
        sdCardLauncher.launch(intent)
    }

    // ---------- Menu de Opções ----------

    private fun mostrarMenuOpcoes(video: Video) {
        val dialog = android.app.Dialog(this)
        dialog.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE)
        val view = layoutInflater.inflate(R.layout.menu_video_options, null)
        dialog.setContentView(view)

        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        val largura = (resources.displayMetrics.widthPixels * 0.85).toInt()
        dialog.window?.setLayout(largura, android.view.ViewGroup.LayoutParams.WRAP_CONTENT)
        dialog.window?.setGravity(android.view.Gravity.CENTER)
        dialog.window?.setDimAmount(0.85f)

        view.findViewById<TextView>(R.id.tvMenuTitle).text = video.name

        view.findViewById<View>(R.id.optShare).setOnClickListener {
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "video/*"
                putExtra(Intent.EXTRA_STREAM, video.uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(shareIntent, "Compartilhar vídeo"))
            dialog.dismiss()
        }

        view.findViewById<View>(R.id.optProperties).setOnClickListener {
            val tamanho = android.text.format.Formatter.formatShortFileSize(this, video.sizeBytes)
            val duracao = formatDuration(video.durationMs)
            MaterialAlertDialogBuilder(this)
                .setTitle("Propriedades")
                .setMessage("Nome: ${video.name}\nTamanho: $tamanho\nDuração: $duracao\nPasta: ${video.folderName}")
                .setPositiveButton("OK", null)
                .show()
            dialog.dismiss()
        }

        view.findViewById<View>(R.id.optPlaylist).setOnClickListener {
            Toast.makeText(this, "Adicionar à playlist em breve", Toast.LENGTH_SHORT).show()
            dialog.dismiss()
        }
        
        view.findViewById<View>(R.id.optSelectAll).setOnClickListener {
            confirmarExclusaoEmMassa()
            dialog.dismiss()
        }

        view.findViewById<View>(R.id.optDelete).setOnClickListener {
            confirmarExclusao(video)
            dialog.dismiss()
        }

        dialog.show()
    }

    // ---------- Exclusão de Vídeo ----------

    private fun confirmarExclusao(video: Video) {
        MaterialAlertDialogBuilder(this)
            .setTitle("Excluir vídeo")
            .setMessage("Tem certeza que deseja excluir '${video.name}'?")
            .setPositiveButton("Excluir") { _, _ -> doDelete(video) }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun doDelete(video: Video) {
        try {
            val file = getFileFromUri(video.uri)
            if (file != null && file.exists()) {
                if (file.delete()) {
                    contentResolver.delete(video.uri, null, null)
                    Toast.makeText(this, "Vídeo excluído!", Toast.LENGTH_SHORT).show()
                    reloadVideos()
                    return
                }
            }
            
            val deleted = contentResolver.delete(video.uri, null, null)
            if (deleted > 0) {
                Toast.makeText(this, "Vídeo excluído!", Toast.LENGTH_SHORT).show()
                reloadVideos()
                return
            }
            
            // Se falhou, pede a permissão (passando o vídeo para tentar de novo depois)
            pedirPermissaoTotal(video)

        } catch (e: SecurityException) {
            pedirPermissaoTotal(video)
        } catch (e: Exception) {
            Toast.makeText(this, "Erro: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    // ---------- Exclusão em Massa ----------

    private fun confirmarExclusaoEmMassa() {
        if (videos.isEmpty()) {
            Toast.makeText(this, "Não há vídeos nesta pasta", Toast.LENGTH_SHORT).show()
            return
        }

        MaterialAlertDialogBuilder(this)
            .setTitle("Excluir todos")
            .setMessage("Tem certeza que deseja excluir os ${videos.size} vídeos desta pasta?")
            .setPositiveButton("Excluir Tudo") { _, _ -> doDeleteEmMassa() }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun doDeleteEmMassa() {
        var count = 0
        for (video in videos) {
            try {
                val file = getFileFromUri(video.uri)
                if (file != null && file.exists() && file.delete()) {
                    contentResolver.delete(video.uri, null, null)
                    count++
                } else {
                    contentResolver.delete(video.uri, null, null)
                    count++
                }
            } catch (e: Exception) { }
        }
        
        if (count > 0) {
            Toast.makeText(this, "$count vídeos excluídos!", Toast.LENGTH_SHORT).show()
            reloadVideos()
        } else {
            pedirPermissaoTotal(null)
        }
    }

    private fun getFileFromUri(uri: Uri): java.io.File? {
        return try {
            val projection = arrayOf(MediaStore.MediaColumns.DATA)
            val cursor = contentResolver.query(uri, projection, null, null, null)
            cursor?.use {
                if (it.moveToFirst()) {
                    val columnIndex = it.getColumnIndexOrThrow(MediaStore.MediaColumns.DATA)
                    return java.io.File(it.getString(columnIndex))
                }
            }
            null
        } catch (e: Exception) {
            null
        }
    }

    // ---------- Layout e Ordenação ----------

    private fun applyLayout() {
        val grid = AppSettings.gridView(this)
        adapter.grid = grid
        b.list.layoutManager =
            if (grid) GridLayoutManager(this, spanCount()) else LinearLayoutManager(this)
        b.btnView.setImageResource(if (grid) R.drawable.ic_view_list else R.drawable.ic_view_grid)
        render()
    }

    private fun spanCount(): Int {
        val dm = resources.displayMetrics
        return (dm.widthPixels / dm.density / 170f).toInt().coerceAtLeast(2)
    }

    private fun render() {
        val by = AppSettings.sortBy(this)
        val cmp: Comparator<Video> =
            if (by == 1) compareBy<Video> { it.sizeBytes } else compareBy<Video> { it.name.lowercase() }
        val sorted = videos.sortedWith(cmp)
        adapter.submit(if (AppSettings.sortDesc(this)) sorted.reversed() else sorted)
    }

    private fun showSortDialog() {
        PopupDialog.show(
            this,
            getString(R.string.popup_sort),
            listOf(
                PopupDialog.Group(
                    getString(R.string.sort_by),
                    listOf(getString(R.string.sort_name), getString(R.string.sort_size)),
                    AppSettings.sortBy(this)
                ) {
                    AppSettings.setSortBy(this, it)
                    render()
                },
                PopupDialog.Group(
                    getString(R.string.order_title),
                    listOf(getString(R.string.order_asc), getString(R.string.order_desc)),
                    if (AppSettings.sortDesc(this)) 1 else 0
                ) {
                    AppSettings.setSortDesc(this, it == 1)
                    render()
                }
            ),
            false
        )
    }

    private fun formatDuration(ms: Long): String {
        val total = ms / 1000
        val h = total / 3600
        val m = (total % 3600) / 60
        val s = total % 60
        return if (h > 0) String.format(java.util.Locale.getDefault(), "%d:%02d:%02d", h, m, s)
        else String.format(java.util.Locale.getDefault(), "%d:%02d", m, s)
    }
}
