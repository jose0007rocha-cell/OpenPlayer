package com.example.openplayer

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
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

    // Launcher para exclusão nativa (Android 11+)
    private val deleteLauncher = registerForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            Toast.makeText(this, "Vídeo excluído com sucesso!", Toast.LENGTH_SHORT).show()
            reloadVideos()
        } else {
            Toast.makeText(this, "Ação cancelada pelo sistema (Código: ${result.resultCode})", Toast.LENGTH_SHORT).show()
        }
    }

    // Launcher para pedir acesso à pasta raiz do SD Card (Estilo XPlayer)
    private val sdCardLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            val treeUri = result.data?.data
            if (treeUri != null) {
                // Salva a permissão permanente para não precisar pedir de novo
                contentResolver.takePersistableUriPermission(
                    treeUri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
                Toast.makeText(this, "Acesso ao Cartão SD concedido!", Toast.LENGTH_SHORT).show()
                
                // Tenta executar a exclusão pendente agora que temos a permissão
                if (videoPendenteExclusao != null) {
                    doDelete(videoPendenteExclusao!!)
                    videoPendenteExclusao = null
                } else {
                    // Se era uma exclusão em massa, tenta de novo
                    doDeleteEmMassa()
                }
            }
        }
    }

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

        // Inicializando o Adapter
        adapter = VideoAdapter(
            onClick = { openVideo(it.uri) },
            onLongClick = { video -> 
                // Se quiser ativar a barra de seleção no futuro, coloque aqui.
            },
            onMoreOptionsClick = { video, _ -> 
                mostrarMenuOpcoes(video) 
            }
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

    private fun openVideo(uri: android.net.Uri) {
    // Abre o vídeo direto no player do seu app (substitua PlayerActivity pelo nome da sua tela de player)
    val intent = Intent(this, PlayerActivity::class.java).apply {
        data = uri
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    startActivity(intent)
}

    // ---------- Menu de Opções ----------

    private fun mostrarMenuOpcoes(video: Video) {
        val dialog = android.app.Dialog(this)
        dialog.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE)
        val view = layoutInflater.inflate(R.layout.menu_video_options, null)
        dialog.setContentView(view)

        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        
        // Define a largura do menu (85% da tela) e centraliza
        val largura = (resources.displayMetrics.widthPixels * 0.85).toInt()
        dialog.window?.setLayout(largura, android.view.ViewGroup.LayoutParams.WRAP_CONTENT)
        dialog.window?.setGravity(android.view.Gravity.CENTER)
        
        // Fundo escurecido
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

    // ---------- Exclusão de Vídeo (Único) ----------

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
        // Se for um arquivo do sistema (content://), usamos o método seguro
        if (video.uri.scheme == "content") {
            // Tenta apagar o documento (funciona se já tivermos a permissão do SD)
            val deleted = android.provider.DocumentsContract.deleteDocument(contentResolver, video.uri)
            if (deleted) {
                Toast.makeText(this, "Vídeo excluído!", Toast.LENGTH_SHORT).show()
                reloadVideos()
                return
            }
        } else {
            // Se for um arquivo normal, tenta apagar pelo caminho
            val file = getFileFromUri(video.uri)
            if (file != null && file.exists() && file.delete()) {
                Toast.makeText(this, "Vídeo excluído!", Toast.LENGTH_SHORT).show()
                reloadVideos()
                return
            }
        }
    } catch (e: SecurityException) {
        // Caiu aqui porque não tem permissão no SD. Vamos pedir!
        pedirPermissaoSDCard(video)
        return
    } catch (e: Exception) {
        // Outros erros, tenta pedir a permissão também
        pedirPermissaoSDCard(video)
        return
    }

    // Se chegou aqui, não conseguiu apagar por algum motivo
    pedirPermissaoSDCard(video)
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

    // ---------- Exclusão em Massa (Selecionar Tudo) ----------

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
    var precisouPermissao = false
    var videoQueFalhou: Video? = null
    
    for (video in videos) {
        try {
            var apagou = false
            if (video.uri.scheme == "content") {
                apagou = android.provider.DocumentsContract.deleteDocument(contentResolver, video.uri)
            } else {
                val file = getFileFromUri(video.uri)
                if (file != null && file.exists()) {
                    apagou = file.delete()
                }
            }
            
            if (apagou) {
                count++
            } else {
                precisouPermissao = true
                videoQueFalhou = video
                break // Para o loop para pedir a permissão
            }
        } catch (e: Exception) {
            precisouPermissao = true
            videoQueFalhou = video
            break
        }
    }
    
    if (count > 0) {
        Toast.makeText(this, "$count vídeos excluídos!", Toast.LENGTH_SHORT).show()
        reloadVideos()
    }
    
    if (precisouPermissao) {
        pedirPermissaoSDCard(videoQueFalhou)
    } else if (count == 0) {
        Toast.makeText(this, "Nenhum vídeo pôde ser excluído", Toast.LENGTH_SHORT).show()
    }
}

    // Função auxiliar para converter a URI em File real
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
