/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.amqhi.cloud

import android.content.res.ColorStateList
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDialog
import androidx.appcompat.widget.PopupMenu
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import com.amqhi.cloud.core.Core
import com.amqhi.cloud.core.EventCode
import com.amqhi.cloud.core.NavigationBarStyle
import com.amqhi.cloud.core.UpdateListener
import com.amqhi.cloud.databinding.ActivitySettingsBinding
import com.amqhi.cloud.databinding.DialogConfirmBinding
import org.json.JSONObject

class SettingsActivity : AppCompatActivity(), UpdateListener {

    private lateinit var binding: ActivitySettingsBinding

    override fun onDestroy() {
        super.onDestroy()
        if (::binding.isInitialized) {
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
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        applyTheme()
        ViewCompat.setOnApplyWindowInsetsListener(binding.main) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        Core.notifier.registerListener(this)
        bindActions()
        applySettings()
    }

    private fun applySettings() {
        binding.navigationBarStyleButton.setText(navigationBarStyleLabel(Core.settings.getNavigationBarStyle()))
    }

    private fun bindActions() {
        binding.backButton.setOnClickListener {
            finish()
        }
        binding.logoutButton.setOnClickListener {
            val dialog = AppCompatDialog(this, R.style.Theme_Cloud_Dialog)
            val content = DialogConfirmBinding.inflate(layoutInflater)
            content.message.setText(R.string.logout_message)
            content.message.setTextColor(AppTheme.attributes.textColorPrimary)
            content.background.backgroundTintList = ColorStateList.valueOf(AppTheme.attributes.surfaceColor)
            content.cancel.setTextColor(AppTheme.attributes.iconButtonColor)
            content.confirm.setTextColor(AppTheme.attributes.iconButtonColor)
            content.cancel.setOnClickListener { dialog.dismiss() }
            content.confirm.setOnClickListener {
                Core.handleLogout()
            }
            dialog.setContentView(content.root)
            dialog.window?.setLayout(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            dialog.show()
        }
        binding.navigationBarStyleButton.setOnClickListener { showNavigationBarStyleMenu() }
    }

    private fun showNavigationBarStyleMenu() {
        val popup = PopupMenu(this, binding.navigationBarStyleButton, Gravity.END)
        popup.menuInflater.inflate(R.menu.menu_navigation_bar_style, popup.menu)
        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.navigation_bar_normal -> {
                    Core.settings.setNavigationBarStyle(NavigationBarStyle.NORMAL)
                    binding.navigationBarStyleButton.setText(R.string.navigation_bar_style_normal)
                }
                R.id.navigation_bar_transparent -> {
                    Core.settings.setNavigationBarStyle(NavigationBarStyle.TRANSPARENT)
                    binding.navigationBarStyleButton.setText(R.string.navigation_bar_style_transparent)
                }
                else -> return@setOnMenuItemClickListener false
            }
            Core.settings.save()
            true
        }
        popup.show()
    }

    private fun navigationBarStyleLabel(style: Char): Int {
        return when (style) {
            NavigationBarStyle.TRANSPARENT -> R.string.navigation_bar_style_transparent
            else -> R.string.navigation_bar_style_normal
        }
    }
    private fun applyTheme() {
        setNavigationBarColor(binding.main)
        val theme = AppTheme.attributes
        binding.main.setBackgroundColor(theme.backgroundColor)
        binding.appBar.setBackgroundColor(theme.backgroundColor)
        binding.backButton.setTextColor(theme.iconButtonColor)
        binding.title.applyAppTitle(theme)
    }

    override fun onNotify(code: Short) {
        when(code) {
            EventCode.LOGOUT_SUCCESS -> {
                finish()
            }
            else -> {
                Toast.makeText(this, "event is not handled: $code", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onNotify(code: Short, data: String) {
        when(code) {
            else -> {
                Toast.makeText(this, "event is not handled: code: $code, data: ${data}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onNotify(code: Short, data: JSONObject) {
        when(code) {
            EventCode.LOGOUT_FAILURE -> {
                Toast.makeText(this, getString(R.string.logout_failure), Toast.LENGTH_SHORT).show()
            }
            else -> {
                Toast.makeText(this, "event is not handled: code: $code, data: ${data}", Toast.LENGTH_SHORT).show()
            }
        }
    }
}