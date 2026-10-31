/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.amqhi.cloud

import android.content.Context
import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatDialog
import com.amqhi.cloud.core.AppScope
import com.amqhi.cloud.core.ItemAttributes
import com.amqhi.cloud.databinding.DialogEditItemBinding

fun showEditItemDialog(context: Context, layoutInflater: LayoutInflater, itemAttributes: ItemAttributes, onConfirm: (itemAttributes: ItemAttributes) -> Unit) {
    val dialog = AppCompatDialog(context, R.style.Theme_Cloud_Dialog)
    val content = DialogEditItemBinding.inflate(layoutInflater)

    content.cancel.setOnClickListener { dialog.dismiss() }
    content.confirm.setOnClickListener {
        val name = content.input.text?.toString()?.trim().orEmpty()
        if (name.isEmpty()) return@setOnClickListener
        var appScopeInt = 0
        if(content.cloudCheckbox.isChecked) appScopeInt = appScopeInt or AppScope.CLOUD_INT
        if (content.notesCheckbox.isChecked) appScopeInt = appScopeInt or AppScope.NOTES_INT
        if (content.musicCheckbox.isChecked) appScopeInt = appScopeInt or AppScope.MUSIC_INT
        if (content.photosCheckbox.isChecked) appScopeInt = appScopeInt or AppScope.PHOTOS_INT
        if (content.webCheckbox.isChecked) appScopeInt = appScopeInt or AppScope.WEB_INT
        if (content.aiCheckbox.isChecked) appScopeInt = appScopeInt or AppScope.AI_INT
        dialog.dismiss()

        onConfirm(ItemAttributes(
            name = name,
            parentIdHigh = itemAttributes.parentIdHigh,
            parentIdLow = itemAttributes.parentIdLow,
            eventAt = null,
            encrypted = content.encryptCheckbox.isChecked,
            appScope = appScopeInt.toShort()
        ))
    }
    content.input.setOnEditorActionListener { _, _, _ ->
        content.confirm.performClick()
        true
    }
    content.background.backgroundTintList = ColorStateList.valueOf(AppTheme.attributes.surfaceColor)
    content.cancel.setTextColor(AppTheme.attributes.iconButtonColor)
    content.confirm.setTextColor(AppTheme.attributes.iconButtonColor)
    content.input.setTextColor(AppTheme.attributes.textColorPrimary)
    content.input.setHintTextColor(AppTheme.attributes.textColorHint)


    content.input.setText(itemAttributes.name)
    
    val appScopeInt = itemAttributes.appScope.toInt()

    content.cloudCheckbox.isChecked = ((appScopeInt and AppScope.CLOUD_INT) != 0)
    content.notesCheckbox.isChecked = ((appScopeInt and AppScope.NOTES_INT) != 0)
    content.musicCheckbox.isChecked = ((appScopeInt and AppScope.MUSIC_INT) != 0)
    content.photosCheckbox.isChecked = ((appScopeInt and AppScope.PHOTOS_INT) != 0)
    content.webCheckbox.isChecked = ((appScopeInt and AppScope.WEB_INT) != 0)
    content.aiCheckbox.isChecked = ((appScopeInt and AppScope.AI_INT) != 0)

    content.encryptCheckbox.isChecked = itemAttributes.encrypted

    // TODO: remove this when E2EE implemented
    content.encryptText.visibility = View.GONE
    content.encryptCheckbox.visibility = View.GONE

    dialog.setContentView(content.root)
    dialog.window?.setLayout(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.WRAP_CONTENT
    )
    dialog.show()
    content.input.requestFocus()
}