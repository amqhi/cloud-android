/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.amqhi.cloud

import android.content.Context
import android.content.res.Configuration
import android.graphics.Typeface
import android.widget.TextView

fun Context.isDarkMode(): Boolean =
    (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES

object AppTheme {
    lateinit var attributes: AppThemeAttributes
}

data class AppThemeAttributes(
    val backgroundColor: Int,
    val surfaceColor: Int,       // Background color for components like Cards, Dialogs, Sheets, etc.
    val primaryColor: Int,       // Color for primary CTAs and highlights
    val secondaryColor: Int,     // Color for secondary accents

    val textColorPrimary: Int,   // Primary body text
    val textColorSecondary: Int, // Secondary text for descriptions, subtitles, dates, etc.
    val textColorHint: Int,      // Text color for placeholders and disabled states
    val onPrimaryColor: Int,     // Text/icon color placed on top of the primary color

    val iconColor: Int,
    val iconButtonColor: Int,
    val titleColor: Int,
    val titleBold: Boolean,

    val dividerColor: Int,       // Color for dividers and card borders

    val errorColor: Int,         // Color for error messages, delete buttons, etc.
    val successColor: Int,       // Color for success and completed states

    val folderIconBackgroundColor: Int,
    val folderIconFrontColor: Int,

    val fabBackgroundColor: Int,
    val fabIconColor: Int,
    val fabShadow: Boolean,

    val isDark: Boolean   // Whether dark mode is enabled (used for controlling status bar icon colors)
) {
    companion object {
        fun light(): AppThemeAttributes {
            return AppThemeAttributes(
                backgroundColor = 0xFFFFFFFF.toInt(),
                surfaceColor = 0xFFF8F9FA.toInt(),
                primaryColor = 0xFF2196F3.toInt(),
                secondaryColor = 0xFF03DAC6.toInt(),

                textColorPrimary = 0xFF212121.toInt(),
                textColorSecondary = 0xFF757575.toInt(),
                textColorHint = 0xFFBDBDBD.toInt(),
                onPrimaryColor = 0xFFFFFFFF.toInt(),

                iconColor = 0xFF999999.toInt(),
                iconButtonColor = 0xFF999999.toInt(),
                titleColor = 0xFF212121.toInt(),
                titleBold = false,

                dividerColor = 0xFFEEEEEE.toInt(),

                errorColor = 0xFFB00020.toInt(),
                successColor = 0xFF4CAF50.toInt(),

                folderIconBackgroundColor = 0xFF4AA9FF.toInt(),
                folderIconFrontColor = 0xFF7FC3FE.toInt(),

                fabBackgroundColor = 0xFFFFFFFF.toInt(),
                fabIconColor = 0xFF2196F3.toInt(),
                fabShadow = true,

                isDark = false
            )
        }

        fun dark(): AppThemeAttributes {
            return AppThemeAttributes(
                backgroundColor = 0xFF121212.toInt(),
                surfaceColor = 0xFF1E1E1E.toInt(),
                primaryColor = 0xFF90CAF9.toInt(),
                secondaryColor = 0xFF03DAC6.toInt(),

                textColorPrimary = 0xFFEEEEEE.toInt(),
                textColorSecondary = 0xFFAAAAAA.toInt(),
                textColorHint = 0xFF666666.toInt(),
                onPrimaryColor = 0xFF000000.toInt(),

                iconColor = 0xFF999999.toInt(),
                iconButtonColor = 0xFF999999.toInt(),
                titleColor = 0xFFEEEEEE.toInt(),
                titleBold = false,

                dividerColor = 0xFF2C2C2C.toInt(),

                errorColor = 0xFFCF6679.toInt(),
                successColor = 0xFF81C784.toInt(),

                folderIconBackgroundColor = 0xFF4AA9FF.toInt(),
                folderIconFrontColor = 0xFF7FC3FE.toInt(),

                fabBackgroundColor = 0xFFEFEFEF.toInt(),
                fabIconColor = 0xFF2196F3.toInt(),
                fabShadow = true,

                isDark = true
            )
        }
    }
}

fun TextView.applyAppTitle(theme: AppThemeAttributes) {
    setTextColor(theme.titleColor)
    setTypeface(null, if (theme.titleBold) Typeface.BOLD else Typeface.NORMAL)
}
