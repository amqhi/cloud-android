/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.amqhi.cloud

import android.content.res.ColorStateList
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.amqhi.cloud.core.Core
import com.amqhi.cloud.core.ItemSummary
import com.amqhi.cloud.core.ItemType
import com.amqhi.cloud.core.SpecialFolder
import com.amqhi.cloud.databinding.SheetMoveBinding
import java.util.UUID

class MoveItemsSheet : BottomSheetDialogFragment(), FolderBrowseHost {
    private var _binding: SheetMoveBinding? = null
    private val binding get() = _binding!!

    private val sourceId: UUID
        get() = UUID.fromString(requireArguments().getString(ARG_SOURCE_ID))

    private val selectedIds: List<UUID>
        get() = requireArguments().getStringArrayList(ARG_SELECTED_IDS)
            .orEmpty()
            .map(UUID::fromString)

    override fun getTheme(): Int = R.style.Theme_Cloud_BottomSheet

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = SheetMoveBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val theme = AppTheme.attributes
        binding.background.backgroundTintList = ColorStateList.valueOf(theme.surfaceColor)
        binding.backButton.setTextColor(theme.iconButtonColor)
        binding.title.setTextColor(theme.textColorPrimary)
        binding.moveHere.setTextColor(theme.onPrimaryColor)
        (binding.moveHere.background?.mutate() as? GradientDrawable)?.setColor(theme.primaryColor)
        binding.backButton.setOnClickListener {
            if (childFragmentManager.backStackEntryCount > 0) {
                childFragmentManager.popBackStack()
            }
        }
        binding.moveHere.setOnClickListener { moveHere() }
        if (savedInstanceState == null) {
            childFragmentManager.beginTransaction()
                .replace(
                    R.id.moveFolderContainer,
                    FilesFragment.newPickerInstance(
                        SpecialFolder.HOME,
                        getString(R.string.files_title),
                        hiddenFolderIds()
                    )
                )
                .commit()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    override fun browseFolder(folderId: UUID, title: String) {
        childFragmentManager.beginTransaction()
            .replace(
                R.id.moveFolderContainer,
                FilesFragment.newPickerInstance(folderId, title, hiddenFolderIds())
            )
            .addToBackStack(folderId.toString())
            .commit()
    }

    override fun onBrowseFolderShown(folderId: UUID, title: String) {
        val content = _binding ?: return
        content.title.text = title
        content.backButton.isVisible = childFragmentManager.backStackEntryCount > 0
        content.moveHere.isVisible = folderId != sourceId
    }

    private fun hiddenFolderIds(): Set<UUID> {
        return requireArguments().getStringArrayList(ARG_HIDDEN_IDS)
            .orEmpty()
            .map(UUID::fromString)
            .toSet()
    }

    private fun moveHere() {
        val destination = (childFragmentManager.findFragmentById(R.id.moveFolderContainer) as? FilesFragment)
            ?.folderId
            ?: return
        val ids = selectedIds
        if (ids.size == 1) {
            Core.itemManager.moveItem(ids.first(), destination)
        } else {
            Core.itemManager.moveItems(ids, sourceId, destination)
        }
        (activity as? MainActivity)?.exitFileSelection()
        dismiss()
    }

    companion object {
        private const val ARG_SOURCE_ID = "source_id"
        private const val ARG_SELECTED_IDS = "selected_ids"
        private const val ARG_HIDDEN_IDS = "hidden_ids"

        fun newInstance(sourceId: UUID, selected: List<ItemSummary>) = MoveItemsSheet().apply {
            val hiddenIds = buildList {
                add(sourceId.toString())
                add(SpecialFolder.TRASH.toString())
                selected.filter { it.type == ItemType.FOLDER }.forEach { add(it.id.toString()) }
            }
            arguments = Bundle().apply {
                putString(ARG_SOURCE_ID, sourceId.toString())
                putStringArrayList(ARG_SELECTED_IDS, ArrayList(selected.map { it.id.toString() }))
                putStringArrayList(ARG_HIDDEN_IDS, ArrayList(hiddenIds))
            }
        }
    }
}
