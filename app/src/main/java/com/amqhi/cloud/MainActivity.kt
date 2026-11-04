/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.amqhi.cloud

import android.Manifest
import android.animation.ObjectAnimator
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.OpenableColumns
import android.util.Log
import android.view.Gravity
import android.view.Menu
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.view.animation.DecelerateInterpolator
import android.view.animation.LinearInterpolator
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDialog
import androidx.appcompat.widget.PopupMenu
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.FragmentTransaction
import com.amqhi.cloud.core.AppScope
import com.amqhi.cloud.core.Core
import com.amqhi.cloud.core.EventCode
import com.amqhi.cloud.core.FileProgressListener
import com.amqhi.cloud.core.ItemAttributes
import com.amqhi.cloud.core.ItemStatus
import com.amqhi.cloud.core.NetworkProvider
import com.amqhi.cloud.core.Notifier
import com.amqhi.cloud.core.PlatformUtils
import com.amqhi.cloud.core.SecureStorageProvider
import com.amqhi.cloud.core.SortOption
import com.amqhi.cloud.core.SpecialFolder
import com.amqhi.cloud.core.UpdateListener
import com.amqhi.cloud.core.User
import com.amqhi.cloud.databinding.ActivityMainBinding
import com.amqhi.cloud.databinding.DialogConfirmBinding
import com.amqhi.cloud.databinding.ItemAccountBinding
import com.amqhi.cloud.databinding.ItemAddAccountBinding
import com.amqhi.cloud.databinding.SheetAccountsBinding
import com.google.android.material.bottomsheet.BottomSheetDialog
import org.json.JSONObject
import java.io.File
import java.util.Locale
import java.util.UUID

class MainActivity : AppCompatActivity(), UpdateListener, FolderNavigator, FileProgressListener {
    private lateinit var binding: ActivityMainBinding
    private var syncRotation: ObjectAnimator? = null
    private var fabMenuOpen = false
    private var notificationPermissionRequested = false
    private val uploadProgressPercent = mutableMapOf<UUID, Int>()

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    override var gridSpanCount = 3
        private set

    override val searchQuery: String
        get() = binding.searchBar.text?.toString().orEmpty()

    private var tempFile: File? = null

    private fun getFileName(context: Context, uri: Uri): String? {
        return context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (nameIndex != -1 && cursor.moveToFirst()) cursor.getString(nameIndex) else null
        }
    }

    private fun createTempFileForUpload(uri: Uri): Pair<File, String>? {
        val originalName = getFileName(this, uri) ?: return null

        val dotIndex = originalName.lastIndexOf('.')
        val prefix = if (dotIndex != -1) originalName.substring(0, dotIndex) else originalName
        val suffix = if (dotIndex != -1) originalName.substring(dotIndex) else null

        val safePrefix = if (prefix.length < 3) "${prefix}_tmp" else prefix

        val tempFile = contentResolver.openInputStream(uri)?.use { inputStream ->
            File.createTempFile(safePrefix, suffix, cacheDir).apply {
                deleteOnExit()
                outputStream().use { outputStream ->
                    inputStream.copyTo(outputStream)
                }
            }
        } ?: return null

        return Pair(tempFile, originalName)
    }

    @RequiresApi(Build.VERSION_CODES.KITKAT)
    private val exportFileLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument("*/*")
    ) { uri: Uri? ->
        val file = tempFile
        if (uri == null || file == null) return@registerForActivityResult
        try {
            contentResolver.openOutputStream(uri)?.use { output ->
                file.inputStream().use { input -> input.copyTo(output) }
            }
            tempFile = null
        } catch (_: Exception) {
            runOnUiThread {
                showExportFailure(this)
            }
        }
    }

    private val selectFilesLauncher = registerForActivityResult(
        ActivityResultContracts.GetMultipleContents()
    ) { uris: List<Uri> ->

        if (uris.isNotEmpty()) {
            uris.forEach { uri ->
                createTempFileForUpload(uri)?.let {
                    Core.itemManager.createFile(
                        itemAttributes = ItemAttributes(
                            name = it.second,
                            parentIdHigh = currentFolderId().mostSignificantBits,
                            parentIdLow = currentFolderId().leastSignificantBits
                        ),
                        tmpFilePath = it.first.absolutePath
                    )
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::binding.isInitialized) {
            hideSyncProgress()
            Core.networkProvider.unregisterFileProgressListener(this)
            Core.notifier.unregisterListener(this)
        }
        if (uploadProgressPercent.isNotEmpty()) {
            val manager = getSystemService(NotificationManager::class.java)
            uploadProgressPercent.keys.forEach { manager.cancel(it.hashCode()) }
            uploadProgressPercent.clear()
        }
    }

    override fun onResume() {
        super.onResume()
        applyTheme()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val networkProvider = NetworkProvider()
        val secureStorageProvider = SecureStorageProvider(applicationContext)
        val notifier = Notifier()
        notifier.registerListener(this)
        Core.initialize(
            appSupportPath = filesDir.absolutePath,
            networkProvider = networkProvider,
            secureStorageProvider = secureStorageProvider,
            notifier = notifier,
            platformUtils = PlatformUtils()
        )

        Core.networkProvider.registerFileProgressListener(this)
        if(isDarkMode()) {
            AppTheme.attributes = AppThemeAttributes.dark()
        }
        else {
            AppTheme.attributes = AppThemeAttributes.light()
        }

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        applyTheme()

        if (savedInstanceState == null) {
            showRootFolder(SpecialFolder.HOME, getString(R.string.files_title))
        }
        bindActions()
        setupBackPress()

        binding.fileFabRow.viewTreeObserver.addOnGlobalLayoutListener(
            object : ViewTreeObserver.OnGlobalLayoutListener {
                override fun onGlobalLayout() {
                    binding.fileFabRow.translationX =
                        ((binding.fileFabRow.parent as View).width - binding.fileFabRow.left).toFloat()
                    binding.fileFabRow.visibility = View.VISIBLE
                    binding.fileFabRow.viewTreeObserver.removeOnGlobalLayoutListener(this)
                }
            }
        )

        binding.folderFabRow.viewTreeObserver.addOnGlobalLayoutListener(
            object : ViewTreeObserver.OnGlobalLayoutListener {
                override fun onGlobalLayout() {
                    binding.folderFabRow.translationX =
                        ((binding.folderFabRow.parent as View).width - binding.folderFabRow.left).toFloat()
                    binding.folderFabRow.visibility = View.VISIBLE
                    binding.folderFabRow.viewTreeObserver.removeOnGlobalLayoutListener(this)
                }
            }
        )

        binding.floatingMenu.viewTreeObserver.addOnGlobalLayoutListener(
            object : ViewTreeObserver.OnGlobalLayoutListener {
                override fun onGlobalLayout() {
                    binding.floatingMenu.translationY =
                        ((binding.folderFabRow.parent as View).height - binding.floatingMenu.top).toFloat()
                    binding.floatingMenu.visibility = View.VISIBLE
                    binding.floatingMenu.viewTreeObserver.removeOnGlobalLayoutListener(this)
                }
            }
        )

        automaticCheckForUpdate(this)
    }

    private fun applyTheme() {
        setNavigationBarColor(binding.main)
        val theme = AppTheme.attributes

        binding.main.setBackgroundColor(theme.backgroundColor)
        listOf(
            binding.doneButton,
            binding.reconnectButton,
            binding.searchButton,
            binding.moveButton,
            binding.gridButton,
            binding.selectionMenuButton,
        ).forEach { it.setTextColor(theme.iconButtonColor) }
        binding.title.applyAppTitle(theme)
        binding.itemCountText.setTextColor(theme.textColorSecondary)
        binding.syncProgressIndicator.setTextColor(theme.iconColor)

        binding.searchBar.setTextColor(theme.textColorPrimary)
        binding.searchBar.setHintTextColor(theme.textColorHint)
        (binding.searchBar.background?.mutate() as? GradientDrawable)?.apply {
            setColor(theme.surfaceColor)
            val strokeWidth = resources.displayMetrics.density.toInt().coerceAtLeast(1)
            setStroke(strokeWidth, theme.dividerColor)
        }

        val fabElevation = if (theme.fabShadow) 6f * resources.displayMetrics.density else 0f
        listOf(binding.mainFab, binding.folderFab, binding.fileFab).forEach { fab ->
            fab.setTextColor(theme.fabIconColor)
            (fab.background?.mutate() as? GradientDrawable)?.setColor(theme.fabBackgroundColor)
            fab.elevation = fabElevation
        }
        binding.trashButton.setTextColor(theme.fabIconColor)
        binding.settingsButton.setTextColor(theme.fabIconColor)
        (binding.floatingMenu.background?.mutate() as? GradientDrawable)?.setColor(theme.fabBackgroundColor)
        binding.floatingMenu.elevation = fabElevation
        listOf(binding.folderFabLabel, binding.fileFabLabel).forEach { label ->
            label.setTextColor(theme.textColorPrimary)
            (label.background?.mutate() as? GradientDrawable)?.apply {
                setColor(theme.surfaceColor)
                val strokeWidth = resources.displayMetrics.density.toInt().coerceAtLeast(1)
                setStroke(strokeWidth, theme.dividerColor)
            }
        }

        WindowCompat.getInsetsController(window, binding.root).apply {
            isAppearanceLightStatusBars = !theme.isDark
            isAppearanceLightNavigationBars = !theme.isDark
        }
    }
    private fun bindActions() {
        binding.title.setOnClickListener {
            binding.itemCountText.isVisible = !binding.itemCountText.isVisible
        }
        binding.doneButton.setOnClickListener {
            currentFilesFragment()?.exitSelectionMode()
        }
        binding.trashButton.setOnClickListener {
            if(currentFolderId() == SpecialFolder.HOME) {
                binding.trashButton.text = getString(R.string.icon_home_outlined)
                showRootFolder(SpecialFolder.TRASH, getString(R.string.trash))
            }
            else {
                binding.trashButton.text = getString(R.string.icon_trash)
                showRootFolder(SpecialFolder.HOME, getString(R.string.files_title))
            }
        }
        binding.settingsButton.setOnClickListener {
            val intent = Intent(this, SettingsActivity::class.java)
            startActivity(intent)
        }
        binding.reconnectButton.setOnClickListener {
            Core.onNetworkReconnect()
            binding.reconnectButton.visibility = View.GONE
        }
        binding.searchButton.setOnClickListener {
            if (binding.searchBar.isVisible) {
                closeSearch()
            } else {
                binding.searchBar.visibility = View.VISIBLE
                binding.searchBar.requestFocus()
            }
        }
        binding.moveButton.setOnClickListener { showMoveSheet() }
        binding.gridButton.setOnClickListener { showFilesMenu() }
        binding.selectionMenuButton.setOnClickListener { showSelectionMenu() }
        binding.searchBar.doAfterTextChanged { text ->
            currentFilesFragment()?.applyFilter(text?.toString().orEmpty())
        }
        binding.mainFab.setOnClickListener { toggleFabMenu() }
        binding.folderFab.setOnClickListener {
            if(isViewingDeletedItems()) {
                return@setOnClickListener
            }
            val folderId = currentFolderId()
            showEditItemDialog(this, layoutInflater,
                ItemAttributes(
                    name = "",
                    parentIdHigh = folderId.mostSignificantBits,
                    parentIdLow = folderId.leastSignificantBits,
                    eventAt = null,
                    encrypted = false,
                    appScope = AppScope.ALL
                )) { attributes ->
                Core.itemManager.createFolder(attributes)
            }
        }
        binding.fileFab.setOnClickListener {
            if(isViewingDeletedItems()) {
                return@setOnClickListener
            }
            selectFilesLauncher.launch("*/*")
        }
        binding.accountButton.setOnClickListener { showAccountSheet() }
        bindCurrentAccount()
    }

    private fun bindCurrentAccount() {
        val user = Core.getSelectedUser()
        binding.accountButton.setUser(user)
    }

    private fun showAccountSheet() {
        val dialog = BottomSheetDialog(this, R.style.Theme_Cloud_BottomSheet)
        val content = SheetAccountsBinding.inflate(layoutInflater)
        content.background.backgroundTintList = ColorStateList.valueOf(AppTheme.attributes.surfaceColor)
        val current = Core.getSelectedUser()
        val currentItem = ItemAccountBinding.inflate(layoutInflater, content.accounts, true)
        bindAccountItem(currentItem, current)
        Core.getUnselectedUsers().forEach { user ->
            val item = ItemAccountBinding.inflate(layoutInflater, content.accounts, true)
            bindAccountItem(item, user)
            item.account.setOnClickListener {
                Core.switchUser(user.localId)
                bindCurrentAccount()
                currentFilesFragment()?.reloadItems()
                dialog.dismiss()
            }
        }
        val addAccount = ItemAddAccountBinding.inflate(layoutInflater, content.accounts, true)
        addAccount.addAccountButton.setOnClickListener {
            Core.addUser()
            bindCurrentAccount()
            showRootFolder(SpecialFolder.HOME, getString(R.string.files_title))
            currentFilesFragment()?.reloadItems()
            dialog.dismiss()
        }
        dialog.setContentView(content.root)
        dialog.show()
    }

    fun exitFileSelection() {
        currentFilesFragment()?.exitSelectionMode()
    }

    private fun showMoveSheet() {
        val selected = currentFilesFragment()?.selectedItems().orEmpty()
        if (selected.isEmpty()) return
        MoveItemsSheet.newInstance(currentFolderId(), selected)
            .show(supportFragmentManager, "move-folder")
    }

    private fun bindAccountItem(item: ItemAccountBinding, user: User, showAvatar: Boolean = true) {
        val theme = AppTheme.attributes
        item.email.text = user.email
        item.email.setTextColor(theme.textColorSecondary)
        item.nickname.text = user.name
        item.nickname.setTextColor(theme.textColorPrimary)
        item.avatar.isVisible = showAvatar
        val textStart = if (showAvatar) (52 * resources.displayMetrics.density).toInt() else 0
        listOf(item.email, item.nickname).forEach { label ->
            label.layoutParams = (label.layoutParams as ViewGroup.MarginLayoutParams).apply {
                marginStart = textStart
            }
        }
        if (showAvatar) item.avatar.setUser(user)
    }

    override fun openFolder(folderId: UUID, title: String) {
        if (folderId == SpecialFolder.HOME || folderId == SpecialFolder.TRASH) {
            showRootFolder(folderId, title)
            return
        }
        if (currentFilesFragment()?.folderId == folderId) return
        supportFragmentManager.beginTransaction()
            .setTransition(FragmentTransaction.TRANSIT_FRAGMENT_OPEN)
            .replace(
                R.id.filesContainer,
                FilesFragment.newInstance(folderId, title),
                folderId.toString()
            )
            .addToBackStack(folderId.toString())
            .commit()
    }

    override fun onFolderShown(folderId: UUID, title: String) {
        binding.title.text = title.ifEmpty {
            when (folderId) {
                SpecialFolder.HOME -> getString(R.string.files_title)
                SpecialFolder.TRASH -> getString(R.string.trash)
                else -> getString(R.string.files_title)
            }
        }
        binding.itemCountText.text = currentFilesFragment()?.itemCount?.toString()
    }

    override fun onSelectionModeChanged(selecting: Boolean) {
        binding.doneButton.isVisible = selecting
        binding.title.isVisible = !selecting
        binding.searchButton.isVisible = !selecting
        binding.moveButton.isVisible = selecting && !isViewingDeletedItems()
        binding.gridButton.isVisible = !selecting
        binding.selectionMenuButton.isVisible = selecting
        if (selecting) closeSearch()
    }

    private fun closeSearch() {
        if (!binding.searchBar.isVisible) return
        binding.searchBar.visibility = View.GONE
        binding.searchBar.text = null
        val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(binding.searchBar.windowToken, 0)
        binding.searchBar.clearFocus()
    }
    private fun showRootFolder(folderId: UUID, title: String) {
        if (supportFragmentManager.backStackEntryCount > 0) {
            supportFragmentManager.popBackStackImmediate(null, FragmentManager.POP_BACK_STACK_INCLUSIVE)
        }
        val current = currentFilesFragment()
        if (current?.folderId == folderId) {
            onFolderShown(folderId, title)
            return
        }
        supportFragmentManager.beginTransaction()
            .replace(
                R.id.filesContainer,
                FilesFragment.newInstance(folderId, title),
                folderId.toString()
            )
            .commit()
    }

    private fun currentFilesFragment(): FilesFragment? =
        supportFragmentManager.findFragmentById(R.id.filesContainer) as? FilesFragment

    private fun currentFolderId(): UUID =
        currentFilesFragment()?.folderId ?: SpecialFolder.HOME

    private fun toggleFabMenu() {
        if (fabMenuOpen) collapseFabMenu() else expandFabMenu()
    }

    private fun expandFabMenu() {
        fabMenuOpen = true
        showFabs()
        binding.floatingMenu.animate().cancel()
        binding.floatingMenu.animate()
            .translationY(0f)
            .setInterpolator(DecelerateInterpolator(2f))
            .setDuration(1000)
            .start()
        binding.mainFab.animate().setInterpolator(DecelerateInterpolator(2f)).rotation(-45f).setDuration(750).start()
        binding.itemCountText.isVisible = true
    }

    private fun collapseFabMenu() {
        if (!fabMenuOpen) return
        fabMenuOpen = false
        hideFabs()
        binding.floatingMenu.animate().cancel()
        val hiddenY = ((binding.floatingMenu.parent as View).height - binding.floatingMenu.top).toFloat()
        binding.floatingMenu.animate()
            .translationY(hiddenY)
            .setInterpolator(DecelerateInterpolator(2f))
            .setDuration(1000)
            .start()
        binding.mainFab.animate().setInterpolator(DecelerateInterpolator(2f)).rotation(0f).setDuration(750).start()
        binding.itemCountText.isVisible = false
    }

    private fun showFabs() {
        showFab(binding.folderFabRow, 1000)
        showFab(binding.fileFabRow,  750)
    }

    private fun hideFabs() {
        hideFab(binding.folderFabRow, 750)
        hideFab(binding.fileFabRow, 1000)
    }

    private fun showFab(row: View, duration: Long) {
        row.animate().cancel()
        row.animate()
            .translationX(0f)
            .setDuration(duration)
            .setInterpolator(DecelerateInterpolator(2f))
            .start()
    }

    private fun hideFab(row: View, duration: Long) {
        row.animate().cancel()
        val hiddenX = ((row.parent as View).width - row.left).toFloat()
        row.animate()
            .translationX(hiddenX)
            .setDuration(duration)
            .setInterpolator(DecelerateInterpolator(2f))
            .start()
    }

    private fun setupBackPress() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                when {
                    fabMenuOpen -> collapseFabMenu()
                    binding.searchBar.isVisible -> closeSearch()
                    currentFilesFragment()?.isInSelectionMode() == true -> {
                        currentFilesFragment()?.exitSelectionMode()
                    }
                    supportFragmentManager.backStackEntryCount > 0 -> {
                        supportFragmentManager.popBackStack()
                    }
                    currentFolderId() == SpecialFolder.TRASH -> {
                        binding.trashButton.text = getString(R.string.icon_trash)
                        showRootFolder(SpecialFolder.HOME, getString(R.string.files_title))
                    }
                    else -> {
                        isEnabled = false
                        onBackPressedDispatcher.onBackPressed()
                    }
                }
            }
        })
    }

    private fun showDeleteItemsDialog() {
        val dialog = AppCompatDialog(this, R.style.Theme_Cloud_Dialog)
        val content = DialogConfirmBinding.inflate(layoutInflater)
        val message = if (isViewingDeletedItems()) {
            R.string.delete_permanently_message
        } else {
            R.string.move_to_trash_message
        }
        content.message.setText(message)
        content.message.setTextColor(AppTheme.attributes.textColorPrimary)
        content.background.backgroundTintList = ColorStateList.valueOf(AppTheme.attributes.surfaceColor)
        content.cancel.setTextColor(AppTheme.attributes.iconButtonColor)
        content.confirm.setTextColor(AppTheme.attributes.iconButtonColor)
        content.cancel.setOnClickListener { dialog.dismiss() }
        content.confirm.setOnClickListener {
            currentFilesFragment()?.selectedItems()?.let { selectedItems ->
                if(selectedItems.isEmpty()) {
                    return@setOnClickListener
                }

                if(isViewingDeletedItems()) {
                    if(selectedItems.size == 1) {
                        Core.itemManager.deleteItem(selectedItems.first().id)
                    }
                    else {
                        Core.itemManager.deleteItems(selectedItems.map { it.id })
                    }
                }
                else {
                    if(selectedItems.size == 1) {
                        Core.itemManager.softDeleteItem(selectedItems.first().id)
                    }
                    else {
                        Core.itemManager.softDeleteItems(selectedItems.map { it.id }, currentFolderId())
                    }
                }
            }
            dialog.cancel()
            currentFilesFragment()?.exitSelectionMode()
        }
        dialog.setContentView(content.root)
        dialog.window?.setLayout(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        dialog.show()
    }

    private fun isViewingDeletedItems(): Boolean {
        val manager = supportFragmentManager
        val backStackTags = buildSet {
            for (index in 0 until manager.backStackEntryCount) {
                manager.getBackStackEntryAt(index).name?.let(::add)
            }
        }
        val root = manager.fragments
            .filterIsInstance<FilesFragment>()
            .firstOrNull { it.tag !in backStackTags }
        return root?.folderId == SpecialFolder.TRASH
    }

    private fun showSelectionMenu() {
        val inTrash = isViewingDeletedItems()
        val popup = PopupMenu(this, binding.selectionMenuButton, Gravity.END)
        popup.menuInflater.inflate(R.menu.menu_selection, popup.menu)
        popup.menu.findItem(R.id.action_move_to_trash).isVisible = !inTrash
        popup.menu.findItem(R.id.action_restore).isVisible = inTrash
        popup.menu.findItem(R.id.action_delete_permanently).isVisible = inTrash
        popup.menu.findItem(R.id.action_availability).setTitle(
            if (currentFilesFragment()?.selectedItems()?.firstOrNull()?.status == ItemStatus.AVAILABLE_OFFLINE) R.string.menu_make_online_only
            else R.string.menu_make_available_offline
        )
        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.action_select_all -> {
                    currentFilesFragment()?.selectAll()
                    true
                }
                R.id.action_availability -> {
                    if (currentFilesFragment()?.selectedItems()?.firstOrNull()?.status == ItemStatus.AVAILABLE_OFFLINE) {
                        currentFilesFragment()?.selectedItems()?.forEach {
                            Core.itemManager.makeItemOnlineOnly(it.id)
                        }
                        currentFilesFragment()?.exitSelectionMode()
                    }
                    else {
                        currentFilesFragment()?.selectedItems()?.forEach {
                            Core.itemManager.makeItemAvailableOffline(it.id)
                        }
                        currentFilesFragment()?.exitSelectionMode()
                    }
                    true
                }
                R.id.action_move_to_trash, R.id.action_delete_permanently -> {
                    showDeleteItemsDialog()
                    true
                }
                R.id.action_restore -> {
                    restoreSelectedItems()
                    true
                }
                else -> true
            }
        }
        popup.show()
    }

    private fun restoreSelectedItems() {
        val selectedItems = currentFilesFragment()?.selectedItems().orEmpty()
        if (selectedItems.isEmpty()) return
        if (selectedItems.size == 1) {
            Core.itemManager.restoreItem(selectedItems.first().id)
        } else {
            Core.itemManager.restoreItems(selectedItems.map { it.id }, currentFolderId())
        }
    }

    private fun showFilesMenu() {
        val folderId = currentFolderId()
        val option = Core.itemManager.getFolderSortOptionById(folderId)
        val popup = PopupMenu(this, binding.gridButton, Gravity.END)
        popup.menuInflater.inflate(R.menu.menu_files, popup.menu)
        popup.menu.findItem(R.id.action_sort).subMenu?.let { applySortIndicators(it, option) }
        popup.setOnMenuItemClickListener { item ->
            when(item.itemId) {
                R.id.action_select -> {
                    currentFilesFragment()?.enterSelectionMode()
                    true
                }
                R.id.sort_name, R.id.sort_size, R.id.sort_created, R.id.sort_modified, R.id.sort_type -> {
                    val ascending = ascendingSortOption(item.itemId)
                    val next = toggledSortOption(option, ascending)
                    Core.itemManager.sortItems(next, folderId)
                    currentFilesFragment()?.reloadItems()
                    true
                }
                else -> {
                    true
                }
            }
        }
        popup.show()
    }

    private fun applySortIndicators(menu: Menu, option: Char) {
        fun indicate(itemId: Int, titleRes: Int, ascending: Char, descending: Char) {
            val arrow = when (option) {
                ascending -> getString(R.string.icon_arrow_down)
                descending -> getString(R.string.icon_arrow_up)
                else -> ""
            }
            val title = getString(titleRes)
            menu.findItem(itemId)?.title = if (arrow.isEmpty()) title else "$title $arrow"
        }
        indicate(R.id.sort_name, R.string.sort_name, SortOption.NAME_ASC, SortOption.NAME_DESC)
        indicate(R.id.sort_created, R.string.sort_created, SortOption.CREATED_AT_ASC, SortOption.CREATED_AT_DESC)
        indicate(R.id.sort_modified, R.string.sort_modified, SortOption.UPDATED_AT_ASC, SortOption.UPDATED_AT_DESC)
        indicate(R.id.sort_size, R.string.sort_size, SortOption.SIZE_ASC, SortOption.SIZE_DESC)
        indicate(R.id.sort_type, R.string.sort_type, SortOption.TYPE_ASC, SortOption.TYPE_DESC)
    }

    private fun ascendingSortOption(itemId: Int): Char = when (itemId) {
        R.id.sort_name -> SortOption.NAME_ASC
        R.id.sort_created -> SortOption.CREATED_AT_ASC
        R.id.sort_modified -> SortOption.UPDATED_AT_ASC
        R.id.sort_size -> SortOption.SIZE_ASC
        else -> SortOption.TYPE_ASC
    }

    private fun toggledSortOption(current: Char, ascending: Char): Char {
        val descending = ascending.uppercaseChar()
        return when (current) {
            ascending -> descending
            descending -> ascending
            else -> ascending
        }
    }

    override fun onNotify(code: Short) {
        when(code) {
            EventCode.ITEMS_MOVE_SUCCESS, EventCode.ITEMS_DELETE_SUCCESS, EventCode.ITEMS_SOFT_DELETE_SUCCESS, EventCode.ITEMS_RESTORE_SUCCESS -> {
                hideSyncProgress()
            }
            EventCode.ITEMS_DELETE_STARTED -> {
                showSyncProgress()
                currentFilesFragment()?.reloadItems()
                binding.itemCountText.text = currentFilesFragment()?.itemCount?.toString()
            }
            EventCode.RE_LOGIN_REQUIRED -> {
                val intent = Intent(this, AuthActivity::class.java)
                intent.putExtra(AuthActivity.EXTRA_LAUNCH_TYPE, AuthActivity.LAUNCH_RELOGIN)
                startActivity(intent)
            }
            EventCode.LOGOUT_SUCCESS -> {
                showRootFolder(SpecialFolder.HOME, getString(R.string.files_title))
                currentFilesFragment()?.reloadItems()
                binding.itemCountText.text = currentFilesFragment()?.itemCount?.toString()
            }
            EventCode.USER_SWITCHED -> {
                showRootFolder(SpecialFolder.HOME, getString(R.string.files_title))
                currentFilesFragment()?.reloadItems()
                binding.itemCountText.text = currentFilesFragment()?.itemCount?.toString()
            }
            EventCode.SYNC_STARTED -> showSyncProgress()
            EventCode.SYNC_SUCCESS -> {
                hideSyncProgress()
                currentFilesFragment()?.reloadItems()
                binding.itemCountText.text = currentFilesFragment()?.itemCount?.toString()
            }

            EventCode.LOGIN_REQUIRED -> {
                val intent = Intent(this, AuthActivity::class.java)
                val launchType = if (Core.getUsers().isEmpty()) {
                    AuthActivity.LAUNCH_FIRST
                } else {
                    AuthActivity.LAUNCH_ADD_USER
                }
                intent.putExtra(AuthActivity.EXTRA_LAUNCH_TYPE, launchType)
                startActivity(intent)
            }
            EventCode.REFRESH_SUCCESS -> {
                currentFilesFragment()?.let { fragment ->
                    fragment.reloadItems()
                    binding.itemCountText.text = currentFilesFragment()?.itemCount?.toString()
                    fragment.setRefreshing(false)
                }
            }
            else -> {
                Log.w("event is not handled", "code: $code")
            }
        }
    }
    override fun onNotify(code: Short, data: String) {
        when(code) {
            EventCode.ITEMS_MOVE_STARTED, EventCode.ITEMS_DELETE_STARTED, EventCode.ITEMS_RESTORE_STARTED, EventCode.ITEMS_SOFT_DELETE_STARTED -> {
                val folderId = UUID.fromString(data)
                showSyncProgress()
                if(currentFolderId() == folderId) {
                    currentFilesFragment()?.reloadItems()
                    binding.itemCountText.text = currentFilesFragment()?.itemCount?.toString()
                }
            }
            EventCode.NETWORK_CONNECTION_FAILURE -> {
                showRefreshFailure(R.string.network_connection_failure)
                hideSyncProgress()
                binding.reconnectButton.visibility = View.VISIBLE
            }
            else -> {
                Log.w("event is not handled", "code: $code, string: $data")
            }
        }
    }
    override fun onNotify(code: Short, data: JSONObject) {
        when(code) {
            EventCode.ITEM_MAKE_ONLINE_ONLY_SUCCESS -> {
                currentFilesFragment()?.reloadItems()
            }
            EventCode.FILE_UPLOAD_STARTED, EventCode.FILES_UPLOAD_STARTED, EventCode.FILE_DOWNLOAD_STARTED, EventCode.FILES_DOWNLOAD_STARTED -> {
                currentFilesFragment()?.reloadItems()
                binding.itemCountText.text = currentFilesFragment()?.itemCount?.toString()
            }
            EventCode.ITEM_CREATE_SUCCESS, EventCode.ITEM_UPDATE_SUCCESS, EventCode.ITEM_MOVE_SUCCESS, EventCode.ITEM_SOFT_DELETE_SUCCESS, EventCode.ITEM_DELETE_SUCCESS -> {
                currentFilesFragment()?.reloadItems()
                binding.itemCountText.text = currentFilesFragment()?.itemCount?.toString()
            }
            EventCode.ITEM_MAKE_AVAILABLE_OFFLINE_SUCCESS -> {
                currentFilesFragment()?.reloadItems()
            }
            EventCode.SYNC_EVENTS_FETCH_FAILURE -> {
                hideSyncProgress()
                Toast.makeText(this, R.string.sync_events_fetch_failure, Toast.LENGTH_SHORT).show()
            }
            EventCode.SYNC_EVENTS_ACKNOWLEDGE_FAILURE -> {
                hideSyncProgress()
                Toast.makeText(this, R.string.acknowledge_sync_events_fetch_failure, Toast.LENGTH_SHORT).show()
            }
            EventCode.FILE_DOWNLOAD_SUCCESS -> {
                if(tempFile == null) {
                    return
                }
                val id = UUID.fromString(data.getString("id"))
                val item = Core.itemManager.getItemById(id)
                exportFileLauncher.launch(item.name)
            }
            EventCode.REFRESH_FAILURE -> showRefreshFailure(R.string.refresh_failure)
            EventCode.FETCH_FILES_FAILURE -> showRefreshFailure(R.string.refresh_files_failure)
            EventCode.FETCH_FOLDERS_FAILURE -> showRefreshFailure(R.string.refresh_folders_failure)
            EventCode.ITEM_CREATE_FAILURE -> {
                // TODO: Localize and improve error message on item create failure
                Toast.makeText(this, "Failed to create item ($data)", Toast.LENGTH_SHORT).show()
            }
            else -> {
                Log.w("event is not handled", "code: $code, json: $data")
            }
        }
    }

    override fun onUploadFinished(itemId: UUID, parentId: UUID) {
        uploadProgressPercent.remove(itemId)
        val manager = uploadNotificationManager()
        val notificationId = itemId.hashCode()
        manager.cancel(notificationId)
    }

    override fun onUploadProgress(
        itemId: UUID,
        parentId: UUID,
        name: String,
        bytesWritten: Long,
        totalBytes: Long
    ) {
        ensureNotificationPermission()
        val manager = uploadNotificationManager()
        val notificationId = itemId.hashCode()

        val percent = if (totalBytes > 0) ((bytesWritten * 100) / totalBytes).toInt() else -1
        if (uploadProgressPercent[itemId] == percent) return
        uploadProgressPercent[itemId] = percent

        val indeterminate = totalBytes <= 0
        val scaledMax = 1000
        val scaledProgress = if (indeterminate) {
            0
        } else {
            ((bytesWritten.coerceAtLeast(0).toDouble() / totalBytes) * scaledMax).toInt()
                .coerceIn(0, scaledMax)
        }
        val openApp = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(this, UPLOAD_NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_upload)
            .setContentTitle(name)
            .setContentText(
                getString(
                    R.string.upload_progress_bytes,
                    formatByteCount(bytesWritten),
                    formatByteCount(totalBytes)
                )
            )
            .setContentIntent(openApp)
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .setProgress(if (indeterminate) 0 else scaledMax, scaledProgress, indeterminate)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .build()
        manager.notify(notificationId, notification)
    }

    private fun formatByteCount(bytes: Long): String {
        if (bytes < 1024) return "$bytes B"
        val units = arrayOf("KB", "MB", "GB", "TB")
        var value = bytes.toDouble()
        var unitIndex = -1
        while (value >= 1024 && unitIndex < units.lastIndex) {
            value /= 1024
            unitIndex++
        }
        return String.format(Locale.getDefault(), "%.1f %s", value, units[unitIndex])
    }

    private fun ensureNotificationPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        if (notificationPermissionRequested) return
        notificationPermissionRequested = true
        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    private fun uploadNotificationManager(): NotificationManager {
        val manager = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                UPLOAD_NOTIFICATION_CHANNEL_ID,
                getString(R.string.upload_notification_channel),
                NotificationManager.IMPORTANCE_LOW
            )
            manager.createNotificationChannel(channel)
        }
        return manager
    }

    private fun showSyncProgress() {
        val indicator = binding.syncProgressIndicator
        indicator.isVisible = true
        if (syncRotation?.isRunning == true) return
        syncRotation = ObjectAnimator.ofFloat(indicator, View.ROTATION, 0f, 360f).apply {
            duration = 800
            repeatCount = ObjectAnimator.INFINITE
            interpolator = LinearInterpolator()
            start()
        }
    }

    private fun hideSyncProgress() {
        syncRotation?.cancel()
        syncRotation = null
        if (::binding.isInitialized) {
            binding.syncProgressIndicator.rotation = 0f
            binding.syncProgressIndicator.isVisible = false
        }
    }

    private fun showRefreshFailure(messageRes: Int) {
        currentFilesFragment()?.setRefreshing(false)
        Toast.makeText(this, messageRes, Toast.LENGTH_SHORT).show()
    }

    companion object {
        private const val UPLOAD_NOTIFICATION_CHANNEL_ID = "upload_progress"
    }
}
