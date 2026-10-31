/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.amqhi.cloud

import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.widget.PopupMenu
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.GridLayoutManager
import com.amqhi.cloud.core.Core
import com.amqhi.cloud.core.FileProgressListener
import com.amqhi.cloud.core.ItemAttributes
import com.amqhi.cloud.core.ItemStatus
import com.amqhi.cloud.core.ItemSummary
import com.amqhi.cloud.core.ItemType
import com.amqhi.cloud.databinding.FragmentFilesBinding
import java.io.File
import java.nio.file.Path
import java.util.UUID

interface FolderNavigator {
    val gridSpanCount: Int
    val searchQuery: String
    fun openFolder(folderId: UUID, title: String)
    fun onFolderShown(folderId: UUID, title: String)
    fun onSelectionModeChanged(selecting: Boolean)
}

interface FolderBrowseHost {
    fun browseFolder(folderId: UUID, title: String)
    fun onBrowseFolderShown(folderId: UUID, title: String)
}

class FilesFragment : Fragment(), FileProgressListener {
    private var _binding: FragmentFilesBinding? = null
    private val binding get() = _binding!!

    private lateinit var adapter: FilesAdapter
    private var allItems: List<ItemSummary> = emptyList()
    private var selectionMode = false
    private val selectedItems = linkedMapOf<UUID, ItemSummary>()
    private val transfers = linkedMapOf<UUID, TransferState>()

    val folderId: UUID
        get() = UUID.fromString(requireNotNull(requireArguments().getString(ARG_FOLDER_ID)))

    val folderTitle: String
        get() = requireArguments().getString(ARG_TITLE).orEmpty()

    private val browseFolders: Boolean
        get() = requireArguments().getBoolean(ARG_BROWSE_FOLDERS)

    private val hiddenFolderIds: Set<UUID>
        get() = requireArguments().getStringArrayList(ARG_HIDDEN_IDS)
            .orEmpty()
            .map(UUID::fromString)
            .toSet()

    val itemCount get() = allItems.size

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentFilesBinding.inflate(inflater, container, false)
        Core.networkProvider.registerFileProgressListener(this)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val host = activity as? FolderNavigator
        adapter = FilesAdapter(
            onItemClick = { item ->
                if(item.status == ItemStatus.UPLOADING || item.status == ItemStatus.PENDING_UPLOAD) {
                    return@FilesAdapter
                }
                when(item.type) {
                    ItemType.FOLDER -> {
                        if (browseFolders) {
                            (parentFragment as? FolderBrowseHost)?.browseFolder(item.id, item.name)
                        } else {
                            host?.openFolder(item.id, item.name)
                        }
                    }

                    ItemType.FILE -> {
                        if (!browseFolders) {
                            startActivity(Intent(requireContext(), FileViewerActivity::class.java).apply {
                                putExtra(FileViewerActivity.EXTRA_ITEM_ID_HIGH, item.id.mostSignificantBits)
                                putExtra(FileViewerActivity.EXTRA_ITEM_ID_LOW, item.id.leastSignificantBits)
                                putExtra(FileViewerActivity.EXTRA_ICON_TYPE, item.iconType)
                            })
                        }
                    }
                }
            },
            onItemLongClick = { anchor, item ->
                if (!browseFolders && item.status != ItemStatus.PENDING_UPLOAD && item.status != ItemStatus.UPLOADING) showItemActions(anchor, item)
            },
            onItemToggle = { item -> toggleSelection(item) },
        )
        adapter.selectionMode = selectionMode
        binding.fileGrid.layoutManager = GridLayoutManager(requireContext(), host?.gridSpanCount ?: 3)
        binding.fileGrid.adapter = adapter
        binding.fileRefresh.isEnabled = !browseFolders
        if (browseFolders) {
            val padding = (8 * resources.displayMetrics.density).toInt()
            binding.fileGrid.setPadding(
                binding.fileGrid.paddingLeft,
                binding.fileGrid.paddingTop,
                binding.fileGrid.paddingRight,
                padding
            )
        }
        binding.fileRefresh.setOnRefreshListener {
            Core.itemManager.refresh()
        }
        allItems = loadItems()
        applyFilter(if (browseFolders) "" else host?.searchQuery.orEmpty())
    }

    override fun onResume() {
        super.onResume()
        if (browseFolders) {
            (parentFragment as? FolderBrowseHost)?.onBrowseFolderShown(folderId, folderTitle)
            return
        }
        val host = activity as? FolderNavigator
        host?.onFolderShown(folderId, folderTitle)
        host?.onSelectionModeChanged(selectionMode)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        Core.networkProvider.unregisterFileProgressListener(this)
        _binding = null
    }

    fun setSpanCount(span: Int) {
        val grid = _binding?.fileGrid ?: return
        (grid.layoutManager as GridLayoutManager).spanCount = span
        adapter.notifyDataSetChanged()
    }

    fun isInSelectionMode(): Boolean = selectionMode

    fun selectedItems(): List<ItemSummary> = selectedItems.values.toList()

    fun selectAll() {
        if (_binding == null || !selectionMode) return
        adapter.currentList.forEachIndexed { index, it ->
            selectedItems[it.id] = it
            adapter.notifyItemChanged(index, FilesAdapter.SELECTED)
        }
    }

    fun enterSelectionMode() {
        if (_binding == null || selectionMode) return
        selectionMode = true
        syncSelection()
        (activity as? FolderNavigator)?.onSelectionModeChanged(true)
    }

    fun exitSelectionMode() {
        if (!selectionMode) return
        selectionMode = false
        selectedItems.clear()
        syncSelection()
        (activity as? FolderNavigator)?.onSelectionModeChanged(false)
    }

    fun reloadItems() {
        if (_binding == null) return
        allItems = loadItems()
        if (selectionMode) {
            val current = allItems.associateBy { it.id }
            val retained = linkedMapOf<UUID, ItemSummary>()
            for (id in selectedItems.keys) {
                val fresh = current[id] ?: continue
                retained[id] = fresh
            }
            selectedItems.clear()
            selectedItems.putAll(retained)
        }
        syncSelection()
        applyFilter(if (browseFolders) "" else (activity as? FolderNavigator)?.searchQuery.orEmpty())
    }
    fun setRefreshing(refreshing: Boolean) {
        _binding?.fileRefresh?.isRefreshing = refreshing
    }

    fun applyFilter(query: String) {
        val trimmed = query.trim()
        adapter.submitList(
            if (trimmed.isEmpty()) allItems
            else allItems.filter { it.name.contains(trimmed, ignoreCase = true) }
        )
    }

    private fun showItemActions(anchor: View, itemSummary: ItemSummary) {
        val popup = PopupMenu(anchor.context, anchor, Gravity.START)
        popup.menuInflater.inflate(R.menu.menu_item_actions, popup.menu)
        popup.menu.findItem(R.id.action_availability).setTitle(
            if (itemSummary.status == ItemStatus.AVAILABLE_OFFLINE) R.string.menu_make_online_only
            else R.string.menu_make_available_offline
        )
        if(itemSummary.type == ItemType.FOLDER) {
            popup.menu.findItem(R.id.action_export).isVisible = false
            popup.menu.findItem(R.id.action_availability).isVisible = false
        }
        popup.setOnMenuItemClickListener { menuItem ->
            when(menuItem.itemId) {
                R.id.action_select -> {
                    enterSelectionMode()
                    true
                }
                R.id.action_details -> {
                    val item = Core.itemManager.getItemById(itemSummary.id)
                    showItemDetailDialog(anchor.context, layoutInflater, item)
                    true
                }
                R.id.action_edit -> {
                    val item = Core.itemManager.getItemById(itemSummary.id)
                    showEditItemDialog(
                        context = layoutInflater.context,
                        layoutInflater = layoutInflater,
                        itemAttributes = ItemAttributes(
                            name = item.name,
                            eventAt = item.eventAt,
                            parentIdHigh = item.parentId.mostSignificantBits,
                            parentIdLow = item.parentId.leastSignificantBits,
                            encrypted = item.encrypted,
                            comment = item.comment,
                            appScope = item.appScope
                        ),
                        onConfirm = { itemAttributes ->
                            Core.itemManager.updateItem(itemSummary.id, itemAttributes)
                        }
                    )
                    true
                }
                R.id.action_export -> {
                    val file = File(binding.root.context.cacheDir, File(itemSummary.name).name)
                    Core.itemManager.downloadItem(itemSummary.id, file.absolutePath)
                    true
                }
                R.id.action_availability -> {
                    if (itemSummary.status == ItemStatus.AVAILABLE_OFFLINE) {
                        Core.itemManager.makeItemOnlineOnly(itemSummary.id)
                    }
                    else {
                        Core.itemManager.makeItemAvailableOffline(itemSummary.id)
                    }
                    true
                }
                else -> {
                    true
                }
            }
        }
        popup.show()
    }

    private fun toggleSelection(item: ItemSummary) {
        if (!selectionMode) return
        if (selectedItems.containsKey(item.id)) selectedItems.remove(item.id)
        else selectedItems[item.id] = item
        syncSelection(item.id)
    }

    private fun syncSelection(changedId: UUID? = null) {
        if (_binding == null || !::adapter.isInitialized) return
        adapter.selectionMode = selectionMode
        if (changedId == null) {
            adapter.notifyDataSetChanged()
            return
        }
        val index = adapter.currentList.indexOfFirst { it.id == changedId }
        if (index >= 0) {
            if(selectedItems.containsKey(changedId)) {
                adapter.notifyItemChanged(index, FilesAdapter.SELECTED)
            }
            else {
                adapter.notifyItemChanged(index, FilesAdapter.UNSELECTED)
            }
        }
    }

    override fun onUploadStarted(itemId: UUID, parentId: UUID) {
        if(parentId != folderId) {
            return
        }
        val position = adapter.currentList.indexOfFirst { it.id == itemId }
        if(position < 0) {
            return
        }
        adapter.notifyItemChanged(
            position,
            FilesAdapter.TRANSFER_STARTED)
    }

    override fun onUploadFinished(itemId: UUID, parentId: UUID) {
        if(parentId != folderId) {
            return
        }
        val position = adapter.currentList.indexOfFirst { it.id == itemId }
        if(position < 0) {
            return
        }
        adapter.notifyItemChanged(
            position,
            FilesAdapter.TRANSFER_FINISHED)
    }

    override fun onDownloadStarted(itemId: UUID, parentId: UUID) {
        onUploadStarted(itemId, parentId)
    }

    override fun onDownloadFinished(itemId: UUID, parentId: UUID) {
        onUploadFinished(itemId, parentId)
    }

    override fun onUploadProgress(
        itemId: UUID,
        parentId: UUID,
        name: String,
        bytesWritten: Long,
        totalBytes: Long
    ) {
        val position = adapter.currentList.indexOfFirst { it.id == itemId }
        adapter.transfers[position] = TransferState(
            totalBytes = totalBytes,
            transferredBytes = bytesWritten
        )
        adapter.notifyItemChanged(
            position,
            FilesAdapter.PAYLOAD_PROGRESS)
    }

    override fun onDownloadProgress(
        itemId: UUID,
        parentId: UUID,
        bytesReceived: Long,
        totalBytes: Long
    ) {
        val position = adapter.currentList.indexOfFirst { it.id == itemId }
        adapter.transfers[position] = TransferState(
            totalBytes = totalBytes,
            transferredBytes = bytesReceived
        )
        adapter.notifyItemChanged(position,
            FilesAdapter.PAYLOAD_PROGRESS)
    }

    private fun loadItems(): List<ItemSummary> {
        val remote = runCatching { Core.itemManager.getItemSummaryListById(folderId) }
            .getOrDefault(emptyList())
        if (!browseFolders) return remote
        return remote.filter { it.type == ItemType.FOLDER && it.id !in hiddenFolderIds }
    }

    companion object {
        private const val ARG_FOLDER_ID = "folder_id"
        private const val ARG_TITLE = "title"
        private const val ARG_BROWSE_FOLDERS = "browse_folders"
        private const val ARG_HIDDEN_IDS = "hidden_ids"

        fun newInstance(folderId: UUID, title: String) = FilesFragment().apply {
            arguments = Bundle().apply {
                putString(ARG_FOLDER_ID, folderId.toString())
                putString(ARG_TITLE, title)
            }
        }

        fun newPickerInstance(
            folderId: UUID,
            title: String,
            hiddenIds: Collection<UUID>
        ) = FilesFragment().apply {
            arguments = Bundle().apply {
                putString(ARG_FOLDER_ID, folderId.toString())
                putString(ARG_TITLE, title)
                putBoolean(ARG_BROWSE_FOLDERS, true)
                putStringArrayList(ARG_HIDDEN_IDS, ArrayList(hiddenIds.map(UUID::toString)))
            }
        }
    }
}
