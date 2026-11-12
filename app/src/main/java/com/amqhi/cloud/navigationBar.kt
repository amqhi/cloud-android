/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.amqhi.cloud

import android.view.View
import androidx.appcompat.app.AppCompatActivity
import com.amqhi.cloud.core.Core
import com.amqhi.cloud.core.NavigationBarStyle
import com.amqhi.shared.ui.applyNavigationBar

fun AppCompatActivity.setNavigationBarColor(rootComponent: View) {
    val theme = AppTheme.attributes
    applyNavigationBar(
        rootComponent = rootComponent,
        transparent = Core.settings.getNavigationBarStyle() == NavigationBarStyle.TRANSPARENT,
        backgroundColor = theme.backgroundColor,
        isDark = theme.isDark,
    )
}
