/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.amqhi.cloud

import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.res.ColorStateList
import android.net.Uri
import android.os.Build
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDialog
import com.amqhi.cloud.core.Core
import com.amqhi.cloud.databinding.DialogConfirmBinding
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

private const val RELEASES_URL = "https://api.github.com/repos/amqhi/cloud-android/releases/latest"
private const val CHECK_INTERVAL_SECONDS = 2L * 24 * 60 * 60

private data class LatestRelease(val versionName: String, val pageUrl: String)

fun automaticCheckForUpdate(activity: AppCompatActivity) {
    val lastCheck = Core.cachedState.getLastUpdateCheck()
    val now = System.currentTimeMillis() / 1000
//    if (lastCheck != 0L && now - lastCheck < CHECK_INTERVAL_SECONDS) {
//        return
//    }

    Thread {
        val release = fetchLatestRelease() ?: return@Thread
        Core.cachedState.updateLastUpdateCheck()
        Core.cachedState.save()
        if (release.versionName == "0.1.0.alpha.20261031") {
            return@Thread
        }
        activity.runOnUiThread {
            if (!activity.isUiAlive()) {
                return@runOnUiThread
            }
            showUpdateDialog(activity, release.pageUrl)
        }
    }.start()
}

private fun fetchLatestRelease(): LatestRelease? {
    val connection = (URL(RELEASES_URL).openConnection() as HttpURLConnection).apply {
        connectTimeout = 15_000
        readTimeout = 15_000
        requestMethod = "GET"
        setRequestProperty("Accept", "application/vnd.github+json")
        setRequestProperty("User-Agent", "amqhi-cloud-android")
    }
    return try {
        if (connection.responseCode != HttpURLConnection.HTTP_OK) {
            null
        } else {
            val body = connection.inputStream.bufferedReader().use { it.readText() }
            val json = JSONObject(body)
            val versionName = json.optString("tag_name").releaseVersionName()
            val pageUrl = json.optString("html_url")
            if (versionName.isEmpty() || pageUrl.isEmpty()) {
                null
            } else {
                LatestRelease(versionName, pageUrl)
            }
        }
    } catch (_: Exception) {
        null
    } finally {
        connection.disconnect()
    }
}

private fun showUpdateDialog(activity: AppCompatActivity, pageUrl: String) {
    val dialog = AppCompatDialog(activity, R.style.Theme_Cloud_Dialog)
    val content = DialogConfirmBinding.inflate(activity.layoutInflater)
    content.message.setText(R.string.update_available_message)
    content.message.setTextColor(AppTheme.attributes.textColorPrimary)
    content.background.backgroundTintList = ColorStateList.valueOf(AppTheme.attributes.surfaceColor)
    content.cancel.setTextColor(AppTheme.attributes.iconButtonColor)
    content.confirm.setTextColor(AppTheme.attributes.iconButtonColor)
    content.cancel.setOnClickListener { dialog.dismiss() }
    content.confirm.setOnClickListener {
        dialog.dismiss()
        try {
            activity.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(pageUrl)))
        } catch (_: ActivityNotFoundException) {
        }
    }
    dialog.setContentView(content.root)
    dialog.window?.setLayout(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.WRAP_CONTENT
    )
    dialog.show()
}

private fun String?.releaseVersionName(): String {
    return orEmpty().trim().removePrefix("v").removePrefix("V")
}

private fun AppCompatActivity.isUiAlive(): Boolean {
    if (isFinishing) {
        return false
    }
    return Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN_MR1 || !isDestroyed
}
