/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.amqhi.cloud

import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.Outline
import android.graphics.drawable.GradientDrawable
import android.util.AttributeSet
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewOutlineProvider
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.core.view.isGone
import com.amqhi.cloud.core.User

class AvatarView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    private val image = ImageView(context).apply {
        layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
        scaleType = ImageView.ScaleType.CENTER_CROP
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        visibility = GONE
    }

    private val fallback = TextView(context).apply {
        layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
        gravity = Gravity.CENTER
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
    }

    init {
        val styled = context.obtainStyledAttributes(attrs, intArrayOf(android.R.attr.textSize))
        try {
            if (styled.hasValue(0)) {
                fallback.setTextSize(TypedValue.COMPLEX_UNIT_PX, styled.getDimension(0, fallback.textSize))
            }
        } finally {
            styled.recycle()
        }
        addView(image)
        addView(fallback)
        outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(view: View, outline: Outline) {
                outline.setOval(0, 0, view.width, view.height)
            }
        }
        clipToOutline = true
    }

    fun setUser(user: User) {
        val theme = AppTheme.attributes
        val bitmap = user.cachedPicturePath
            ?.takeIf { it.isNotEmpty() }
            ?.let { BitmapFactory.decodeFile(it) }
        if (bitmap == null) {
            image.isGone = true
            fallback.isGone = false
            fallback.text = user.name.firstOrNull()?.toString().orEmpty()
            fallback.setTextColor(theme.onPrimaryColor)
            fallback.background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(theme.primaryColor)
            }
        } else {
            fallback.isGone = true
            fallback.text = null
            fallback.background = null
            image.isGone = false
            image.setImageBitmap(bitmap)
        }
    }
}
