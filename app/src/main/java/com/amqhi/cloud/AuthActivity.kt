/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.amqhi.cloud

import android.os.Bundle
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.FragmentTransaction
import com.amqhi.cloud.core.Core
import com.amqhi.cloud.core.EventCode
import com.amqhi.cloud.core.UpdateListener
import com.amqhi.cloud.databinding.ActivityAuthBinding
import org.json.JSONObject



class AuthActivity : AppCompatActivity(), UpdateListener {

    private val launchType by lazy {
        intent.getShortExtra(EXTRA_LAUNCH_TYPE, LAUNCH_RELOGIN)
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::binding.isInitialized) {
            runCatching { Core.notifier.unregisterListener(this) }
        }
    }
    private lateinit var binding: ActivityAuthBinding

    override fun onResume() {
        super.onResume()
        applyTheme()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AppTheme.attributes = if (isDarkMode()) AppThemeAttributes.dark() else AppThemeAttributes.light()
        enableEdgeToEdge()
        binding = ActivityAuthBinding.inflate(layoutInflater)
        setContentView(binding.root)
        applyTheme()
        ViewCompat.setOnApplyWindowInsetsListener(binding.main) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        Core.notifier.registerListener(this)

        if (launchType == LAUNCH_FIRST) {
            onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    if (supportFragmentManager.backStackEntryCount > 0) {
                        supportFragmentManager.popBackStack()
                    } else {
                        finishAffinity()
                    }
                }
            })
        }

        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.authContainer, LoginFragment.newInstance(launchType))
                .commit()
        }
    }

    private fun applyTheme() {
        setNavigationBarColor(binding.main)
        val theme = AppTheme.attributes
        binding.main.setBackgroundColor(theme.backgroundColor)
        WindowCompat.getInsetsController(window, binding.root).apply {
            isAppearanceLightStatusBars = !theme.isDark
            isAppearanceLightNavigationBars = !theme.isDark
        }
    }

    fun showRegister(instanceUrl: String) {
        supportFragmentManager.beginTransaction()
            .setTransition(FragmentTransaction.TRANSIT_FRAGMENT_OPEN)
            .replace(R.id.authContainer, RegisterFragment.newInstance(instanceUrl, launchType))
            .addToBackStack("register")
            .commit()
    }

    override fun onNotify(code: Short) {
        when(code) {
            EventCode.REGISTER_SUCCESS -> {
                supportFragmentManager.popBackStack()
            }
            EventCode.LOGIN_SUCCESS -> {
                finish()
            }
            else -> {
                Toast.makeText(this, "event is not handled: code: $code", Toast.LENGTH_SHORT).show()
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
            else -> {
                Toast.makeText(this, "event is not handled: code: $code, data: ${data}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    companion object {
        const val EXTRA_LAUNCH_TYPE = "launchType"
        const val LAUNCH_FIRST: Short = 0
        const val LAUNCH_RELOGIN: Short = 1
        const val LAUNCH_ADD_USER: Short = 2
    }
}
