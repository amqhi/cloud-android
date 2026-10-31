/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.amqhi.cloud

import android.graphics.Color
import android.os.Build
import android.view.View

import android.view.WindowManager
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import com.amqhi.cloud.core.Core
import com.amqhi.cloud.core.NavigationBarStyle

fun AppCompatActivity.setNavigationBarColor(rootComponent: View) {
    if(Core.settings.getNavigationBarStyle() == NavigationBarStyle.TRANSPARENT) {
//        if(Build.VERSION.SDK_INT >= 30) {
//            val controller = window.insetsController
//            controller?.setSystemBarsAppearance(0, WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS)
//            window.setDecorFitsSystemWindows(false)
//            window.navigationBarColor = android.graphics.Color.TRANSPARENT
//            window.statusBarColor = android.graphics.Color.TRANSPARENT
//        }
//        else if(Build.VERSION.SDK_INT >= 29) {
//            window.decorView.systemUiVisibility = (
//                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE
//                            or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
//                            or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
//                    )
//            window.navigationBarColor = android.graphics.Color.TRANSPARENT
//            window.statusBarColor = android.graphics.Color.TRANSPARENT
//        }
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        if(Build.VERSION.SDK_INT >= 30) {
            window.isNavigationBarContrastEnforced = false
        }
        ViewCompat.setOnApplyWindowInsetsListener(rootComponent) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, 0)
            insets
        }
    }
    else {
        window.clearFlags(WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS)
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = !AppTheme.attributes.isDark
            isAppearanceLightNavigationBars = !AppTheme.attributes.isDark
        }
        val color = AppTheme.attributes.backgroundColor
        val style = if (AppTheme.attributes.isDark) {
            SystemBarStyle.dark(color)
        } else {
            SystemBarStyle.light(color, color)
        }
        enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
        ViewCompat.setOnApplyWindowInsetsListener(rootComponent) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
//        if(Build.VERSION.SDK_INT >= 30) {
//            val controller = window.insetsController
//            window.setDecorFitsSystemWindows(true)
//            window.navigationBarColor = AppTheme.attributes.backgroundColor
//            window.statusBarColor = AppTheme.attributes.backgroundColor
//            controller?.setSystemBarsAppearance(
//                WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS,
//                WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS
//            )
//        }
//        else if(Build.VERSION.SDK_INT >= 29) {
//            window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_VISIBLE
//            window.navigationBarColor = AppTheme.attributes.backgroundColor
//            window.statusBarColor = AppTheme.attributes.backgroundColor
//        }
    }
}