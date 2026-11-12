/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.amqhi.cloud

import com.amqhi.shared.core.User
import com.amqhi.shared.ui.AvatarView

fun AvatarView.setUser(user: User) {
    val theme = AppTheme.attributes
    setAvatar(
        name = user.name,
        picturePath = user.cachedPicturePath,
        backgroundColor = theme.primaryColor,
        textColor = theme.onPrimaryColor,
    )
}
