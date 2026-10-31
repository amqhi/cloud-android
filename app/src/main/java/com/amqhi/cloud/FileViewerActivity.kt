/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.amqhi.cloud

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.graphics.drawable.RotateDrawable
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.text.method.LinkMovementMethod
import android.view.Gravity
import android.view.View
import androidx.appcompat.widget.PopupMenu
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.MediaController
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import android.widget.VideoView
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.text.HtmlCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import com.amqhi.cloud.core.Core
import com.amqhi.cloud.core.EventCode
import com.amqhi.cloud.core.FileProgressListener
import com.amqhi.cloud.core.IconType
import com.amqhi.cloud.core.Item
import com.amqhi.cloud.core.ItemAttributes
import com.amqhi.cloud.core.ItemStatus
import com.amqhi.cloud.core.ItemType
import com.amqhi.cloud.core.UpdateListener
import com.amqhi.cloud.databinding.ActivityFileViewerBinding
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID
import java.util.concurrent.Executors
import androidx.core.graphics.createBitmap
import androidx.core.net.toUri

class FileViewerActivity : AppCompatActivity(), UpdateListener, FileProgressListener {

    enum class FileViewerState {
        LOADING, LOADED
    }

    private lateinit var binding: ActivityFileViewerBinding

    private var fileDownloadUrl: String? = null
    private var state: FileViewerState = FileViewerState.LOADING
    private lateinit var itemId: UUID
    private var iconType: Char = IconType.FILE
    private var itemName: String = ""
    private var fileSize: Long = 0L
    private var tempFile: File? = null
    private val tempLock = Any()
    private var closed = false
    private val executor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "file-viewer").apply { isDaemon = true }
    }

    private val exportFileLauncher = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
        registerForActivityResult(ActivityResultContracts.CreateDocument("*/*")
        ) { uri: Uri? ->
            val file = tempFile
            if (uri == null || file == null) return@registerForActivityResult
            executor.execute {
                try {
                    contentResolver.openOutputStream(uri)?.use { output ->
                        file.inputStream().use { input -> input.copyTo(output) }
                    }
                } catch (_: Exception) {
                    runOnUiThread {
                        if (!isClosed()) showExportFailure(this)
                    }
                }
            }
        }
    } else {
        null
    }

    override fun onDestroy() {
        synchronized(tempLock) {
            closed = true
            tempFile?.delete()
            tempFile = null
        }
        executor.shutdownNow()
        super.onDestroy()
        if (::binding.isInitialized) {
            Core.networkProvider.unregisterFileProgressListener(this)
            Core.notifier.unregisterListener(this)
        }
    }

    override fun onResume() {
        super.onResume()
        applyTheme()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AppTheme.attributes = if (isDarkMode()) AppThemeAttributes.dark() else AppThemeAttributes.light()
        enableEdgeToEdge()
        binding = ActivityFileViewerBinding.inflate(layoutInflater)
        setContentView(binding.root)
        applyTheme()
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        val itemIdHigh = intent.getLongExtra(EXTRA_ITEM_ID_HIGH, -1)
        val itemIdLow = intent.getLongExtra(EXTRA_ITEM_ID_LOW, -1)
        iconType = intent.getCharExtra(EXTRA_ICON_TYPE, IconType.FILE)
        itemId = UUID(itemIdHigh, itemIdLow)
        val item = Core.itemManager.getItemById(itemId)
        itemName = item.name

        binding.title.text = item.name
        binding.backButton.setOnClickListener { finish() }
        binding.moreButton.setOnClickListener { showFileMenu() }

        Core.notifier.registerListener(this)
        Core.networkProvider.registerFileProgressListener(this)
        showStatus(getString(R.string.file_viewer_loading))
        openItem(item, itemId)
    }

    private fun showFileMenu() {
        val item = Core.itemManager.getItemById(itemId)
        val popup = PopupMenu(this, binding.moreButton, Gravity.END)
        popup.menuInflater.inflate(R.menu.menu_file_viewer, popup.menu)
        popup.menu.findItem(R.id.action_availability).setTitle(
            if (item.status == ItemStatus.AVAILABLE_OFFLINE) R.string.menu_make_online_only
            else R.string.menu_make_available_offline
        )
        UUID.randomUUID()
        popup.setOnMenuItemClickListener {
            when(it.itemId) {
                R.id.action_details -> {
                    showItemDetailDialog(this, layoutInflater, item)
                }
                R.id.action_edit -> {
                    showEditItemDialog(this, layoutInflater,
                        ItemAttributes(
                            name = item.name,
                            parentIdHigh = item.parentId.mostSignificantBits,
                            parentIdLow = item.parentId.leastSignificantBits,
                            eventAt = item.eventAt,
                            encrypted = item.encrypted,
                            appScope = item.appScope
                        )) { attributes ->
                        Core.itemManager.updateItem(item.id, attributes)
                    }
                }
                R.id.action_export -> {
                    val file = synchronized(tempLock) {
                        tempFile ?: File(cacheDir, File(item.name).name).also { tempFile = it }
                    }
                    showExportProgress(0L, 0L)
                    Core.itemManager.downloadItem(item.id, file.absolutePath)
                }
                R.id.action_availability -> {
                    if (item.status == ItemStatus.AVAILABLE_OFFLINE) {
                        Core.itemManager.makeItemOnlineOnly(itemId)
                    }
                    else {
                        Core.itemManager.makeItemAvailableOffline(itemId)
                    }
                }
            }
            true
        }
        popup.show()
    }

    private fun showExportProgress(received: Long, total: Long) {
        val progressBar = binding.progressBar
        progressBar.visibility = View.VISIBLE
        if (total <= 0L) {
            progressBar.isIndeterminate = true
            return
        }
        progressBar.isIndeterminate = false
        progressBar.max = 1000
        progressBar.progress = ((received.coerceAtLeast(0) * 1000) / total).toInt().coerceIn(0, 1000)
    }

    private fun hideExportProgress() {
        binding.progressBar.visibility = View.GONE
    }

    private fun openItem(item: Item, itemId: UUID) {
        when (item.type) {
            ItemType.FILE -> {
                if (item.encrypted) {
                    // TODO: decrypt end-to-end encrypted files before preview
                }
                val fileMetadata = Core.itemManager.getFileMetadataById(itemId)
                fileSize = fileMetadata.size
                when (iconType) {
                    IconType.PDF, IconType.PDF_ALIAS,
                    IconType.IMAGE, IconType.IMAGE_ALIAS,
                    IconType.TEXT_FILE, IconType.TEXT_FILE_ALIAS,
                    IconType.VIDEO, IconType.VIDEO_ALIAS,
                    IconType.MARKDOWN, IconType.MARKDOWN_ALIAS -> {
                        if (item.status == ItemStatus.AVAILABLE_OFFLINE) {
                            openOfflineFile(Core.itemManager.getItemOfflineFilePathById(itemId))
                        } else {
                            Core.itemManager.fetchFileDownloadUrl(itemId)
                        }
                    }

                    else -> {
                        // TODO: preview unsupported icon types
                        showStatus(getString(R.string.file_viewer_unsupported))
                    }
                }
            }

            else -> {
                // TODO: preview unsupported item types
                showStatus(getString(R.string.file_viewer_unsupported))
            }
        }
    }

    private fun openOfflineFile(path: String) {
        val file = File(path)
        if (path.isEmpty() || !file.isFile) {
            showStatus(getString(R.string.file_viewer_open_failure))
            return
        }
        when (iconType) {
            IconType.IMAGE, IconType.IMAGE_ALIAS -> showLocalImage(file)
            IconType.VIDEO, IconType.VIDEO_ALIAS -> showVideo(file.toUri().toString())
            IconType.TEXT_FILE, IconType.TEXT_FILE_ALIAS,
            IconType.MARKDOWN, IconType.MARKDOWN_ALIAS,
            IconType.PDF, IconType.PDF_ALIAS -> showLocalDocument(file)
        }
    }

    private fun showLocalDocument(file: File) {
        if (exceedsPreviewLimit(fileSize)) {
            showStatus(getString(R.string.file_viewer_too_large))
            return
        }
        val pdfWidth = resources.displayMetrics.widthPixels.coerceAtLeast(1)
        executor.execute {
            if (isClosed()) return@execute
            try {
                when (iconType) {
                    IconType.TEXT_FILE, IconType.TEXT_FILE_ALIAS -> showText(file.readText())
                    IconType.MARKDOWN, IconType.MARKDOWN_ALIAS -> showMarkdown(file.readText())
                    IconType.PDF, IconType.PDF_ALIAS -> showPdf(file, pdfWidth)
                }
            } catch (_: Exception) {
                runOnUiThread { if (!isClosed()) showStatus(getString(R.string.file_viewer_open_failure)) }
            }
        }
    }

    private fun showLocalImage(file: File) {
        if (exceedsPreviewLimit(fileSize)) {
            showStatus(getString(R.string.file_viewer_too_large))
            return
        }
        val targetPx = resources.displayMetrics.widthPixels.coerceAtLeast(1)
        executor.execute {
            val bitmap = try {
                decodeImageFile(file, targetPx)
            } catch (_: Exception) {
                null
            }
            runOnUiThread {
                if (isClosed()) {
                    bitmap?.recycle()
                    return@runOnUiThread
                }
                if (bitmap == null) {
                    showStatus(getString(R.string.file_viewer_open_failure))
                    return@runOnUiThread
                }
                showContent(ZoomableImageView(this).apply {
                    setImageBitmap(bitmap)
                })
                state = FileViewerState.LOADED
            }
        }
    }

    private fun showDownloadedFile(url: String) {
        if (exceedsPreviewLimit(fileSize)) {
            showStatus(getString(R.string.file_viewer_too_large))
            return
        }
        val pdfWidth = resources.displayMetrics.widthPixels.coerceAtLeast(1)
        executor.execute {
            val file = try {
                downloadToCache(url, itemName)
            } catch (_: Exception) {
                null
            }
            if (file == null) {
                runOnUiThread { if (!isClosed()) showStatus(getString(R.string.file_viewer_open_failure)) }
                return@execute
            }
            if (!keepTempFile(file)) return@execute
            when (iconType) {
                IconType.TEXT_FILE, IconType.TEXT_FILE_ALIAS -> showText(file.readText())
                IconType.MARKDOWN, IconType.MARKDOWN_ALIAS -> showMarkdown(file.readText())
                IconType.PDF, IconType.PDF_ALIAS -> showPdf(file, pdfWidth)
                else -> file.delete()
            }
        }
    }

    private fun isClosed(): Boolean = synchronized(tempLock) { closed }

    private fun keepTempFile(file: File): Boolean {
        synchronized(tempLock) {
            if (closed) {
                file.delete()
                return false
            }
            tempFile = file
            return true
        }
    }

    private fun showText(text: String) {
        runOnUiThread {
            if (isClosed()) return@runOnUiThread
            val textView = bodyTextView()
            textView.text = text
            showContent(scrollOf(textView))
            state = FileViewerState.LOADED
        }
    }

    private fun showMarkdown(markdown: String) {
        runOnUiThread {
            if (isClosed()) return@runOnUiThread
            val textView = bodyTextView()
            textView.text = HtmlCompat.fromHtml(markdownToHtml(markdown), HtmlCompat.FROM_HTML_MODE_COMPACT)
            textView.movementMethod = LinkMovementMethod.getInstance()
            showContent(scrollOf(textView))
            state = FileViewerState.LOADED
        }
    }

    private fun showPdf(file: File, width: Int) {
        val bitmaps = ArrayList<Bitmap>()
        try {
            ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
                PdfRenderer(descriptor).use { renderer ->
                    for (index in 0 until renderer.pageCount) {
                        if (isClosed()) {
                            bitmaps.forEach { it.recycle() }
                            return
                        }
                        renderer.openPage(index).use { page ->
                            val pageWidth = page.width.coerceAtLeast(1)
                            val height = (width.toFloat() / pageWidth * page.height).toInt().coerceAtLeast(1)
                            val bitmap = createBitmap(width, height)
                            bitmap.eraseColor(Color.WHITE)
                            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                            bitmaps.add(bitmap)
                        }
                    }
                }
            }
        } catch (_: Exception) {
            bitmaps.forEach { it.recycle() }
            runOnUiThread { if (!isClosed()) showStatus(getString(R.string.file_viewer_open_failure)) }
            return
        }
        runOnUiThread {
            if (isClosed()) {
                bitmaps.forEach { it.recycle() }
                return@runOnUiThread
            }
            val column = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
            }
            bitmaps.forEach { bitmap ->
                column.addView(ImageView(this).apply {
                    setImageBitmap(bitmap)
                    adjustViewBounds = true
                    scaleType = ImageView.ScaleType.FIT_CENTER
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    )
                })
            }
            showContent(scrollOf(column))
            state = FileViewerState.LOADED
        }
    }

    private fun showImage(url: String) {
        if (exceedsPreviewLimit(fileSize)) {
            showStatus(getString(R.string.file_viewer_too_large))
            return
        }
        val targetPx = resources.displayMetrics.widthPixels.coerceAtLeast(1)
        executor.execute {
            val bitmap = try {
                decodeImage(url, targetPx)
            } catch (_: Exception) {
                null
            }
            runOnUiThread {
                if (isClosed()) {
                    bitmap?.recycle()
                    return@runOnUiThread
                }
                if (bitmap == null) {
                    showStatus(getString(R.string.file_viewer_open_failure))
                    return@runOnUiThread
                }
                showContent(ZoomableImageView(this).apply {
                    setImageBitmap(bitmap)
                })
                state = FileViewerState.LOADED
            }
        }
    }

    private fun showVideo(url: String) {
        // TODO: streamed video is not covered by the rough preview size cap
        val videoView = VideoView(this)
        val controller = MediaController(this)
        controller.setAnchorView(videoView)
        videoView.setMediaController(controller)
        videoView.setVideoURI(url.toUri())
        videoView.setOnErrorListener { _, _, _ ->
            showStatus(getString(R.string.file_viewer_open_failure))
            true
        }
        videoView.setOnPreparedListener { it.start() }
        showContent(videoView, Gravity.CENTER)
        state = FileViewerState.LOADED
    }

    private fun downloadToCache(url: String, name: String): File {
        val file = File(cacheDir, File(name).name)
        val connection = openConnection(url)
        try {
            if (connection.responseCode !in 200..299) {
                throw IllegalStateException("download failed: ${connection.responseCode}")
            }
            connection.inputStream.use { input ->
                file.outputStream().use { output -> input.copyTo(output) }
            }
        } catch (e: Exception) {
            file.delete()
            throw e
        } finally {
            connection.disconnect()
        }
        return file
    }

    private fun decodeImageFile(file: File, targetPx: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        val options = BitmapFactory.Options().apply {
            inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight, targetPx, targetPx)
        }
        return BitmapFactory.decodeFile(file.path, options)
    }

    private fun decodeImage(url: String, targetPx: Int): Bitmap? {
        val connection = openConnection(url)
        val bytes = try {
            if (connection.responseCode !in 200..299) return null
            connection.inputStream.use { it.readBytes() }
        } finally {
            connection.disconnect()
        }
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        val options = BitmapFactory.Options().apply {
            inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight, targetPx, targetPx)
        }
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
    }

    private fun openConnection(url: String): HttpURLConnection {
        return (URL(url).openConnection() as HttpURLConnection).apply {
            instanceFollowRedirects = true
            connectTimeout = 15_000
            readTimeout = 30_000
        }
    }

    private fun sampleSize(width: Int, height: Int, reqWidth: Int, reqHeight: Int): Int {
        var sample = 1
        if (height > reqHeight || width > reqWidth) {
            val halfHeight = height / 2
            val halfWidth = width / 2
            while (halfHeight / sample >= reqHeight && halfWidth / sample >= reqWidth) {
                sample *= 2
            }
        }
        return sample
    }

    private fun exceedsPreviewLimit(size: Long): Boolean {
        // TODO: replace this rough preview size cap
        return size > MAX_PREVIEW_BYTES
    }

    private fun bodyTextView(): TextView {
        val padding = (16 * resources.displayMetrics.density).toInt()
        return TextView(this).apply {
            setTextColor(AppTheme.attributes.textColorPrimary)
            textSize = 16f
            setTextIsSelectable(true)
            setPadding(padding, padding, padding, padding)
        }
    }

    private fun scrollOf(child: View): ScrollView {
        return ScrollView(this).apply {
            addView(
                child,
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT
                )
            )
        }
    }

    private fun showStatus(message: String) {
        val textView = TextView(this).apply {
            text = message
            gravity = Gravity.CENTER
            setTextColor(AppTheme.attributes.textColorSecondary)
            // TODO: replace loading, failure, and too-large text with a proper viewer state UI
        }
        showContent(textView)
    }

    private fun showContent(view: View, gravity: Int = Gravity.NO_GRAVITY) {
        binding.fileDetailContainer.removeAllViews()
        binding.fileDetailContainer.addView(
            view,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT,
                gravity
            )
        )
    }

    private fun applyTheme() {
        setNavigationBarColor(binding.main)
        val theme = AppTheme.attributes
        binding.main.setBackgroundColor(theme.backgroundColor)
        binding.appBar.setBackgroundColor(theme.backgroundColor)
        binding.backButton.setTextColor(theme.iconButtonColor)
        binding.title.applyAppTitle(theme)
        binding.title.textSize = 22f
        binding.moreButton.setTextColor(theme.iconButtonColor)
        applyProgressTheme(theme)
        WindowCompat.getInsetsController(window, binding.root).apply {
            isAppearanceLightStatusBars = !theme.isDark
            isAppearanceLightNavigationBars = !theme.isDark
        }
    }

    private fun applyProgressTheme(theme: AppThemeAttributes) {
        val bar = binding.progressBar
        val determinate = bar.progressDrawable.mutate() as LayerDrawable
        (determinate.findDrawableByLayerId(android.R.id.background) as GradientDrawable)
            .setColor(theme.dividerColor)
        val progress = determinate.findDrawableByLayerId(android.R.id.progress) as RotateDrawable
        (progress.drawable as GradientDrawable).setColor(theme.primaryColor)

        val spinning = bar.indeterminateDrawable.mutate() as RotateDrawable
        val primary = theme.primaryColor
        (spinning.drawable as GradientDrawable).colors = intArrayOf(
            primary and 0x00FFFFFF,
            primary,
            primary and 0x00FFFFFF
        )
    }

    private fun markdownToHtml(markdown: String): String {
        val html = StringBuilder()
        var inCode = false
        var listOpen = false
        val code = StringBuilder()
        fun closeList() {
            if (listOpen) {
                html.append("</ul>")
                listOpen = false
            }
        }
        for (line in markdown.replace("\r\n", "\n").split('\n')) {
            if (line.startsWith("```")) {
                if (inCode) {
                    html.append("<pre><code>")
                        .append(escapeHtml(code.toString()))
                        .append("</code></pre>")
                    code.clear()
                    inCode = false
                } else {
                    closeList()
                    inCode = true
                }
                continue
            }
            if (inCode) {
                if (code.isNotEmpty()) code.append('\n')
                code.append(line)
                continue
            }
            val listItem = LIST_ITEM.find(line)
            if (listItem != null) {
                if (!listOpen) {
                    html.append("<ul>")
                    listOpen = true
                }
                html.append("<li>").append(inlineMarkdown(listItem.groupValues[1])).append("</li>")
                continue
            }
            closeList()
            if (line.isBlank()) continue
            val heading = HEADING.find(line)
            if (heading != null) {
                val level = heading.groupValues[1].length
                html.append("<h").append(level).append(">")
                    .append(inlineMarkdown(heading.groupValues[2]))
                    .append("</h").append(level).append(">")
                continue
            }
            html.append("<p>").append(inlineMarkdown(line)).append("</p>")
        }
        closeList()
        if (inCode) {
            html.append("<pre><code>").append(escapeHtml(code.toString())).append("</code></pre>")
        }
        return html.toString()
    }

    private fun inlineMarkdown(text: String): String {
        var rendered = escapeHtml(text)
        rendered = INLINE_CODE.replace(rendered) { "<code>${it.groupValues[1]}</code>" }
        rendered = BOLD.replace(rendered) { "<b>${it.groupValues[1]}</b>" }
        rendered = ITALIC.replace(rendered) { "<i>${it.groupValues[1]}</i>" }
        rendered = LINK.replace(rendered) { match ->
            val label = match.groupValues[1]
            val href = escapeHtml(match.groupValues[2]).replace("\"", "&quot;")
            """<a href="$href">$label</a>"""
        }
        return rendered
    }

    private fun escapeHtml(text: String): String {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
    }

    companion object {
        const val EXTRA_ITEM_ID_HIGH = "id_high"
        const val EXTRA_ITEM_ID_LOW = "id_low"
        const val EXTRA_ICON_TYPE = "icon_type"

        private const val MAX_PREVIEW_BYTES = 20L * 1024 * 1024

        private val LIST_ITEM = Regex("""^\s*[-*]\s+(.*)$""")
        private val HEADING = Regex("""^(#{1,6})\s+(.*)$""")
        private val INLINE_CODE = Regex("""`([^`]+)`""")
        private val BOLD = Regex("""\*\*([^*]+)\*\*""")
        private val ITALIC = Regex("""\*([^*]+)\*""")
        private val LINK = Regex("""\[([^]]+)]\(([^)]+)\)""")
    }

    override fun onNotify(code: Short) {
    }

    override fun onNotify(code: Short, data: String) {
        when(code) {

        }
    }

    override fun onNotify(code: Short, data: JSONObject) {
        when (code) {
            EventCode.ITEM_UPDATE_SUCCESS -> {
                val id = UUID.fromString(data.getString("id"))
                if(id == itemId) {
                    val item = Core.itemManager.getItemById(id)
                    itemName = item.name
                    binding.title.text = item.name
                }
            }
            EventCode.ITEM_UPDATE_FAILURE -> {
                // TODO: show error message on item update failure
            }
            EventCode.FILE_DOWNLOAD_SUCCESS -> {
                val id = UUID.fromString(data.getString("id"))
                if(id == itemId) {
                    hideExportProgress()
                    exportFileLauncher?.launch(itemName)
                }
            }
            EventCode.FILE_DOWNLOAD_FAILURE -> {
                hideExportProgress()
                showExportFailure(this)
            }
            EventCode.FETCH_FILE_DOWNLOAD_URL_SUCCESS -> {
                val url = data.getString("url")
                if (url.isEmpty()) {
                    showStatus(getString(R.string.file_viewer_open_failure))
                    return
                }
                fileDownloadUrl = url
                when (iconType) {
                    IconType.IMAGE, IconType.IMAGE_ALIAS -> showImage(url)
                    IconType.VIDEO, IconType.VIDEO_ALIAS -> showVideo(url)
                    IconType.TEXT_FILE, IconType.TEXT_FILE_ALIAS,
                    IconType.MARKDOWN, IconType.MARKDOWN_ALIAS,
                    IconType.PDF, IconType.PDF_ALIAS -> showDownloadedFile(url)
                }
            }

            EventCode.FETCH_FILE_DOWNLOAD_URL_FAILURE -> {
                // TODO: replace this failure text with a proper error state
                showStatus(getString(R.string.file_viewer_open_failure))
            }
        }
    }

    override fun onDownloadProgress(
        itemId: UUID,
        parentId: UUID,
        bytesReceived: Long,
        totalBytes: Long
    ) {
        showExportProgress(bytesReceived, totalBytes)
    }
}
