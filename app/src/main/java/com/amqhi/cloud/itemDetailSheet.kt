/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.amqhi.cloud

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.View
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.amqhi.cloud.core.AppScope
import com.amqhi.cloud.core.Item
import com.amqhi.cloud.core.ItemStatus
import com.amqhi.cloud.databinding.SheetItemDetailBinding
import java.text.SimpleDateFormat
import java.util.Locale

@SuppressLint("SetTextI18n")
fun showItemDetailDialog(context: Context, layoutInflater: LayoutInflater, item: Item) {
    val dialog = BottomSheetDialog(context, R.style.Theme_Cloud_BottomSheet)
    val content = SheetItemDetailBinding.inflate(layoutInflater)

    val theme = AppTheme.attributes
    content.background.backgroundTintList = ColorStateList.valueOf(theme.surfaceColor)
    content.nameText.setTextColor(theme.textColorPrimary)
    listOf(
        content.createdAtLabel,
        content.updatedAtLabel,
        content.eventAtLabel,
        content.deletedAtLabel,
        content.commentLabel,
        content.encryptedLabel,
        content.statusLabel,
        content.appScopeLabel,
    ).forEach { it.setTextColor(theme.textColorSecondary) }
    listOf(
        content.createdAtText,
        content.updatedAtText,
        content.eventAtText,
        content.deletedAtText,
        content.commentText,
        content.encryptedText,
        content.statusText,
        content.appScopeText,
    ).forEach { it.setTextColor(theme.textColorPrimary) }

    content.nameText.text = item.name
    content.createdAtText.text = item.createdAt.formatted()
    content.updatedAtText.text = item.updatedAt.formatted()
    if(item.eventAt != null) {
        content.eventAtLabel.visibility = View.VISIBLE
        content.eventAtText.text = item.eventAt!!.formatted()
    }

    if(item.deletedAt != null) {
        content.deletedAtLabel.visibility = View.VISIBLE
        content.deletedAtText.visibility = View.VISIBLE
        content.deletedAtText.text = item.deletedAt!!.formatted()
    }

    if(item.comment?.isNotEmpty() == true) {
        content.commentLabel.visibility = View.VISIBLE
        content.commentText.visibility = View.VISIBLE
        content.commentText.text = item.comment
    }

    if(item.encrypted) {
        content.encryptedText.text = context.getString(R.string.e2ee_encrypted)
    }

    if(item.status == ItemStatus.AVAILABLE_OFFLINE) {
        content.statusText.text = context.getString(R.string.status_available_on_offline)
    }
    else {
        content.statusText.text = context.getString(R.string.status_online_only)
    }

    val appScopeInt = item.appScope.toInt()
    var appScopeString = ""
    if((appScopeInt and AppScope.CLOUD_INT) != 0) {
        appScopeString += context.getString(R.string.app_name)
    }
    if((appScopeInt and AppScope.NOTES_INT) != 0) {
        if(appScopeString.isNotEmpty()) {
            appScopeString += ", "
        }
        appScopeString += context.getString(R.string.app_name_notes)
    }
    if((appScopeInt and AppScope.MUSIC_INT) != 0) {
        if(appScopeString.isNotEmpty()) {
            appScopeString += ", "
        }
        appScopeString += context.getString(R.string.app_name_music)
    }
    if((appScopeInt and AppScope.PHOTOS_INT) != 0) {
        if(appScopeString.isNotEmpty()) {
            appScopeString += ", "
        }
        appScopeString += context.getString(R.string.app_name_photos)
    }
    if((appScopeInt and AppScope.WEB_INT) != 0) {
        if(appScopeString.isNotEmpty()) {
            appScopeString += ", "
        }
        appScopeString += context.getString(R.string.app_name_web)
    }
    if((appScopeInt and AppScope.AI_INT) != 0) {
        if(appScopeString.isNotEmpty()) {
            appScopeString += ", "
        }
        appScopeString += context.getString(R.string.app_name_ai)
    }

    content.appScopeText.text = appScopeString

    dialog.setContentView(content.root)
    dialog.show()
}