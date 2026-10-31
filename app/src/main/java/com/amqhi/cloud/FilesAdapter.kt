/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.amqhi.cloud

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Handler
import android.os.Looper
import android.util.LruCache
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.amqhi.cloud.core.IconType
import com.amqhi.cloud.core.ItemStatus
import com.amqhi.cloud.core.ItemSummary
import com.amqhi.cloud.databinding.ItemFileBinding
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicReference

class FilesAdapter(
    private val onItemClick: (ItemSummary) -> Unit,
    private val onItemLongClick: (View, ItemSummary) -> Unit,
    private val onItemToggle: (ItemSummary) -> Unit,
) : ListAdapter<ItemSummary, FilesAdapter.ViewHolder>(Diff) {

    companion object {

        const val TRANSFER_STARTED : Short = 1000
        const val PAYLOAD_PROGRESS : Short = 1001

        const val TRANSFER_FINISHED : Short = 1002
        const val SELECTED : Short = 1003
        const val UNSELECTED : Short = 1004
    }

    var selectionMode: Boolean = false
    var transfers: MutableMap<Int, TransferState> = mutableMapOf()

    private val thumbnails = ThumbnailLoader()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return ViewHolder(
            ItemFileBinding.inflate(inflater, parent, false),
            onItemClick,
            onItemLongClick,
            onItemToggle,
            thumbnails,
        )
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int, payloads: List<Any?>) {
        if(payloads.size == 1) {
            when(payloads[0]) {
                TRANSFER_STARTED -> {
                    holder.binding.checkbox.visibility = View.GONE
                    holder.binding.progressBar.visibility = View.VISIBLE
                    holder.binding.onlineOnlyBadge.visibility = View.GONE
                }
                TRANSFER_FINISHED -> {
                    transfers.remove(position)
                    holder.binding.checkbox.visibility = View.VISIBLE
                    holder.binding.progressBar.visibility = View.GONE
                    holder.binding.onlineOnlyBadge.visibility = View.VISIBLE
                }
                PAYLOAD_PROGRESS -> {
                    transfers[position]?.let {
                        if (it.totalBytes <= 0L) {
                            holder.binding.progressBar.isIndeterminate = true
                            return
                        }
                        holder.binding.progressBar.max = 1000
                        holder.binding.progressBar.isIndeterminate = false
                        holder.binding.progressBar.progress = ((it.transferredBytes.coerceAtLeast(0) * 1000) / it.totalBytes).toInt().coerceIn(0, 1000)
                    }
                }
                SELECTED -> {
                    holder.binding.checkbox.isChecked = true
                }
                UNSELECTED -> {
                    holder.binding.checkbox.isChecked = false
                }
            }
        }
        else {
            super.onBindViewHolder(holder, position, payloads)
        }
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = getItem(position)
        holder.bind(item, selectionMode)
    }

    override fun onViewRecycled(holder: ViewHolder) {
        holder.clearThumbnailRequest()
    }

    class ViewHolder(
        val binding: ItemFileBinding,
        private val onItemClick: (ItemSummary) -> Unit,
        private val onItemLongClick: (View, ItemSummary) -> Unit,
        private val onItemToggle: (ItemSummary) -> Unit,
        private val thumbnails: ThumbnailLoader,
    ) : RecyclerView.ViewHolder(binding.root) {
        private val boundThumbnail = AtomicReference<String?>(null)

        fun clearThumbnailRequest() {
            boundThumbnail.set(null)
        }

        // TODO: Display arrow overlay/badge icon if the item is an alias
        fun bind(item: ItemSummary, selectionMode: Boolean) {
            boundThumbnail.set(null)
            binding.folderIconFront.visibility = View.GONE
            binding.fileIcon.scaleType = ImageView.ScaleType.FIT_CENTER
            binding.fileName.text = item.name
            binding.fileIcon.colorFilter = null
            when(item.status) {
                ItemStatus.AVAILABLE_OFFLINE -> {
                    binding.onlineOnlyBadge.visibility = View.INVISIBLE
                    binding.progressBar.visibility = View.GONE
                }

                ItemStatus.ONLINE_ONLY -> {
                    binding.onlineOnlyBadge.visibility = View.VISIBLE
                    binding.progressBar.visibility = View.GONE
                }

                ItemStatus.PENDING_UPLOAD -> {

                }

                ItemStatus.UPLOADING -> {
                    binding.onlineOnlyBadge.visibility = View.INVISIBLE
                    binding.progressBar.visibility = View.VISIBLE
                }
            }
            when(item.iconType) {
                IconType.FILE-> {
                    binding.fileIcon.setImageResource(R.drawable.img_file)
                } IconType.FILE_ALIAS -> {
                    binding.fileIcon.setImageResource(R.drawable.img_file)
                }
                IconType.FOLDER-> {
                    binding.onlineOnlyBadge.visibility = View.INVISIBLE
                    binding.fileIcon.setImageResource(R.drawable.ic_folder_back)
                    binding.folderIconFront.visibility = View.VISIBLE
                    binding.fileIcon.setColorFilter(AppTheme.attributes.folderIconBackgroundColor)
                    binding.folderIconFront.setColorFilter(AppTheme.attributes.folderIconFrontColor)
                }
                IconType.FOLDER_ALIAS -> {
                    binding.onlineOnlyBadge.visibility = View.INVISIBLE
                    binding.fileIcon.setImageResource(R.drawable.ic_folder_back)
                    binding.folderIconFront.visibility = View.VISIBLE
                    binding.fileIcon.setColorFilter(AppTheme.attributes.folderIconBackgroundColor)
                    binding.folderIconFront.setColorFilter(AppTheme.attributes.folderIconFrontColor)
                }
                // TODO: Implement styled folder
                IconType.STYLED_FOLDER-> {
                    binding.fileIcon.setImageResource(R.drawable.ic_folder_back)
                    binding.folderIconFront.visibility = View.VISIBLE
                    binding.fileIcon.setColorFilter(AppTheme.attributes.folderIconBackgroundColor)
                    binding.folderIconFront.setColorFilter(AppTheme.attributes.folderIconFrontColor)
                }
                IconType.STYLED_FOLDER_ALIAS -> {
                    binding.fileIcon.setImageResource(R.drawable.ic_folder_back)
                    binding.folderIconFront.visibility = View.VISIBLE
                    binding.fileIcon.setColorFilter(AppTheme.attributes.folderIconBackgroundColor)
                    binding.folderIconFront.setColorFilter(AppTheme.attributes.folderIconFrontColor)
                }
                IconType.TEXT_FILE-> {
                    binding.fileIcon.setImageResource(R.drawable.img_text_file)
                }
                IconType.TEXT_FILE_ALIAS -> {
                    binding.fileIcon.setImageResource(R.drawable.img_text_file)
                }
                IconType.IMAGE-> {
                    boundThumbnail.set(item.thumbnailPath)
                    val targetPx = binding.fileIcon.layoutParams?.width?.takeIf { it > 0 }
                        ?: (72f * binding.fileIcon.resources.displayMetrics.density).toInt()
                    thumbnails.load(binding.fileIcon, item.thumbnailPath, targetPx, boundThumbnail)
                }
                IconType.IMAGE_ALIAS -> {
                    boundThumbnail.set(item.thumbnailPath)
                    val targetPx = binding.fileIcon.layoutParams?.width?.takeIf { it > 0 }
                        ?: (72f * binding.fileIcon.resources.displayMetrics.density).toInt()
                    thumbnails.load(binding.fileIcon, item.thumbnailPath, targetPx, boundThumbnail)
                }
                IconType.VIDEO-> {
                    boundThumbnail.set(item.thumbnailPath)
                    val targetPx = binding.fileIcon.layoutParams?.width?.takeIf { it > 0 }
                        ?: (72f * binding.fileIcon.resources.displayMetrics.density).toInt()
                    thumbnails.load(binding.fileIcon, item.thumbnailPath, targetPx, boundThumbnail)
                }
                IconType.VIDEO_ALIAS -> {
                    boundThumbnail.set(item.thumbnailPath)
                    val targetPx = binding.fileIcon.layoutParams?.width?.takeIf { it > 0 }
                        ?: (72f * binding.fileIcon.resources.displayMetrics.density).toInt()
                    thumbnails.load(binding.fileIcon, item.thumbnailPath, targetPx, boundThumbnail)
                }
                IconType.AUDIO-> {
                    // TODO: Replace with dedicated audio icon resource
                    binding.fileIcon.setImageResource(R.drawable.img_file)
                }
                IconType.AUDIO_ALIAS -> {
                    // TODO: Replace with dedicated audio icon resource
                    binding.fileIcon.setImageResource(R.drawable.img_file)
                }
                IconType.MARKDOWN-> {
                    // TODO: Replace with dedicated markdown icon resource
                    binding.fileIcon.setImageResource(R.drawable.img_text_file)
                }
                IconType.MARKDOWN_ALIAS -> {
                    // TODO: Replace with dedicated markdown icon resource
                    binding.fileIcon.setImageResource(R.drawable.img_text_file)
                }
                IconType.PDF-> {
                    binding.fileIcon.setImageResource(R.drawable.img_pdf)
                }
                IconType.PDF_ALIAS -> {
                    binding.fileIcon.setImageResource(R.drawable.img_pdf)
                }
                IconType.DOCUMENT-> {
                    binding.fileIcon.setImageResource(R.drawable.img_word)
                }
                IconType.DOCUMENT_ALIAS -> {
                binding.fileIcon.setImageResource(R.drawable.img_word)
                }
                IconType.ARTIST-> {
                    // TODO: Replace with dedicated artist icon resource
                    binding.fileIcon.setImageResource(R.drawable.img_file)
                }
                IconType.ARTIST_ALIAS -> {
                    // TODO: Replace with dedicated artist icon resource
                    binding.fileIcon.setImageResource(R.drawable.img_file)
                }
                IconType.ALBUM-> {
                    // TODO: Replace with dedicated album icon resource
                    binding.fileIcon.setImageResource(R.drawable.img_file)
                }
                IconType.ALBUM_ALIAS -> {
                    // TODO: Replace with dedicated album icon resource
                    binding.fileIcon.setImageResource(R.drawable.img_file)
                }
                IconType.ARCHIVE-> {
                    // TODO: Replace with dedicated archive icon resource
                    binding.fileIcon.setImageResource(R.drawable.img_file)
                }
                IconType.ARCHIVE_ALIAS -> {
                    // TODO: Replace with dedicated archive icon resource
                    binding.fileIcon.setImageResource(R.drawable.img_file)
                }
                IconType.CODE -> {
                    // TODO: Replace with dedicated code icon resource
                    binding.fileIcon.setImageResource(R.drawable.img_file)
                }
                IconType.CODE_ALIAS -> {
                    // TODO: Replace with dedicated code icon resource
                    binding.fileIcon.setImageResource(R.drawable.img_file)
                }
                IconType.WEB -> {
                    // TODO: Replace with dedicated web icon resource
                    binding.fileIcon.setImageResource(R.drawable.img_file)
                }
                IconType.WEB_ALIAS -> {
                    // TODO: Replace with dedicated web icon resource
                    binding.fileIcon.setImageResource(R.drawable.img_file)
                }
                IconType.PLAYLIST -> {
                    // TODO: Replace with dedicated playlist icon resource
                    binding.fileIcon.setImageResource(R.drawable.img_file)
                }
                IconType.PLAYLIST_ALIAS -> {
                    // TODO: Replace with dedicated playlist icon resource
                    binding.fileIcon.setImageResource(R.drawable.img_file)
                }
                IconType.EXECUTABLE -> {
                    // TODO: Replace with dedicated executable icon resource
                    binding.fileIcon.setImageResource(R.drawable.img_file)
                }
                IconType.EXECUTABLE_ALIAS -> {
                    // TODO: Replace with dedicated executable icon resource
                    binding.fileIcon.setImageResource(R.drawable.img_file)
                }
                IconType.DATABASE -> {
                    // TODO: Replace with dedicated database icon resource
                    binding.fileIcon.setImageResource(R.drawable.img_file)
                }
                IconType.DATABASE_ALIAS -> {
                    // TODO: Replace with dedicated database icon resource
                    binding.fileIcon.setImageResource(R.drawable.img_file)
                }
                IconType.BOOK -> {
                    // TODO: Replace with dedicated book icon resource
                    binding.fileIcon.setImageResource(R.drawable.img_file)
                }
                IconType.BOOK_ALIAS -> {
                    // TODO: Replace with dedicated book icon resource
                    binding.fileIcon.setImageResource(R.drawable.img_file)
                }
                IconType.SPREADSHEET -> {
                    // TODO: Replace with dedicated spreadsheet icon resource
                    binding.fileIcon.setImageResource(R.drawable.img_file)
                }
                IconType.SPREADSHEET_ALIAS -> {
                    // TODO: Replace with dedicated spreadsheet icon resource
                    binding.fileIcon.setImageResource(R.drawable.img_file)
                }
                IconType.PRESENTATION -> {
                    // TODO: Replace with dedicated presentation icon resource
                    binding.fileIcon.setImageResource(R.drawable.img_file)
                }
                IconType.PRESENTATION_ALIAS -> {
                    // TODO: Replace with dedicated presentation icon resource
                    binding.fileIcon.setImageResource(R.drawable.img_file)
                }
                IconType.FONT -> {
                    // TODO: Replace with dedicated font icon resource
                    binding.fileIcon.setImageResource(R.drawable.img_file)
                }
                IconType.FONT_ALIAS -> {
                    // TODO: Replace with dedicated font icon resource
                    binding.fileIcon.setImageResource(R.drawable.img_file)
                }
                IconType.SUBTITLE -> {
                    // TODO: Replace with dedicated subtitle icon resource
                    binding.fileIcon.setImageResource(R.drawable.img_file)
                }
                IconType.SUBTITLE_ALIAS -> {
                    // TODO: Replace with dedicated subtitle icon resource
                    binding.fileIcon.setImageResource(R.drawable.img_file)
                }
                else -> {
                    binding.fileIcon.setImageResource(R.drawable.img_file)
                }
            }
            if (selectionMode) {
                binding.onlineOnlyBadge.visibility = View.INVISIBLE
                binding.progressBar.visibility = View.GONE
                binding.checkbox.visibility = View.VISIBLE
                binding.checkbox.isChecked = false
            }
            else {
                binding.checkbox.visibility = View.GONE
            }
            binding.root.setOnClickListener {
                if (selectionMode) onItemToggle(item) else onItemClick(item)
            }
            binding.root.setOnLongClickListener {
                onItemLongClick(binding.root, item)
                true
            }
        }
    }

    private object Diff : DiffUtil.ItemCallback<ItemSummary>() {
        override fun areItemsTheSame(oldItem: ItemSummary, newItem: ItemSummary) = oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: ItemSummary, newItem: ItemSummary) = oldItem == newItem
    }

     class ThumbnailLoader {
        private val executor = Executors.newFixedThreadPool(2) { runnable ->
            Thread(runnable, "file-thumbnail").apply { isDaemon = true }
        }
        private val mainHandler = Handler(Looper.getMainLooper())
        private val cache = object : LruCache<String, Bitmap>(cacheSizeKb()) {
            override fun sizeOf(key: String, value: Bitmap): Int =
                (value.byteCount / 1024).coerceAtLeast(1)
        }

        fun load(
            imageView: ImageView,
            path: String,
            targetPx: Int,
            boundPath: AtomicReference<String?>,
        ) {
            val cached = synchronized(cache) { cache.get(path) }
            if (cached != null) {
                imageView.setImageBitmap(cached)
                imageView.scaleType = ImageView.ScaleType.CENTER_CROP
                return
            }
            imageView.setImageResource(R.drawable.img_file)
            executor.execute {
                if (boundPath.get() != path) return@execute
                val bitmap = decode(path, targetPx) ?: return@execute
                if (boundPath.get() != path) return@execute
                synchronized(cache) { cache.put(path, bitmap) }
                mainHandler.post {
                    if (boundPath.get() == path) {
                        imageView.scaleType = ImageView.ScaleType.CENTER_CROP
                        imageView.setImageBitmap(bitmap)
                    }
                }
            }
        }

        private fun decode(path: String, targetPx: Int): Bitmap? {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(path, bounds)
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
            val options = BitmapFactory.Options().apply {
                inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight, targetPx, targetPx)
                inPreferredConfig = if (bounds.outMimeType == "image/jpeg") {
                    Bitmap.Config.RGB_565
                } else {
                    Bitmap.Config.ARGB_8888
                }
            }
            return BitmapFactory.decodeFile(path, options)
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

        private fun cacheSizeKb(): Int {
            val maxKb = (Runtime.getRuntime().maxMemory() / 1024).toInt()
            return maxKb / 8
        }
    }
}
