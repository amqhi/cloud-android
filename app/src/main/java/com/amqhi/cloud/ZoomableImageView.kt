/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.amqhi.cloud

import android.content.Context
import android.graphics.Matrix
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.widget.ImageView
import kotlin.math.min

internal class ZoomableImageView(context: Context) : ImageView(context) {
    private val drawMatrix = Matrix()
    private var userScale = 1f
    private var panX = 0f
    private var panY = 0f

    private val scaleDetector = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(detector: ScaleGestureDetector): Boolean {
            scaleAt(detector.scaleFactor, detector.focusX, detector.focusY)
            return true
        }
    })

    private val gestureDetector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
        override fun onDown(e: MotionEvent): Boolean = true

        override fun onScroll(e1: MotionEvent?, e2: MotionEvent, distanceX: Float, distanceY: Float): Boolean {
            if (scaleDetector.isInProgress || userScale <= 1.01f) return false
            panX -= distanceX
            panY -= distanceY
            updateMatrix()
            return true
        }

        override fun onDoubleTap(e: MotionEvent): Boolean {
            if (userScale > 1.05f) {
                userScale = 1f
                panX = 0f
                panY = 0f
                updateMatrix()
            } else {
                scaleAt(DOUBLE_TAP_SCALE / userScale, e.x, e.y)
            }
            return true
        }
    })

    init {
        scaleType = ScaleType.MATRIX
        isClickable = true
        scaleDetector.isQuickScaleEnabled = false
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        updateMatrix()
    }

    override fun setImageBitmap(bm: android.graphics.Bitmap?) {
        userScale = 1f
        panX = 0f
        panY = 0f
        super.setImageBitmap(bm)
        updateMatrix()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        scaleDetector.onTouchEvent(event)
        gestureDetector.onTouchEvent(event)
        return true
    }

    private fun scaleAt(factor: Float, focusX: Float, focusY: Float) {
        val drawable = drawable ?: return
        val drawableWidth = drawable.intrinsicWidth.toFloat()
        val drawableHeight = drawable.intrinsicHeight.toFloat()
        if (drawableWidth <= 0f || drawableHeight <= 0f || width == 0 || height == 0) return
        val fit = min(width / drawableWidth, height / drawableHeight)
        val oldScale = fit * userScale
        if (oldScale == 0f) return
        val newUserScale = (userScale * factor).coerceIn(MIN_SCALE, MAX_SCALE)
        val newScale = fit * newUserScale
        val oldTranslateX = (width - drawableWidth * oldScale) / 2f + panX
        val oldTranslateY = (height - drawableHeight * oldScale) / 2f + panY
        val contentX = (focusX - oldTranslateX) / oldScale
        val contentY = (focusY - oldTranslateY) / oldScale
        userScale = newUserScale
        panX = focusX - contentX * newScale - (width - drawableWidth * newScale) / 2f
        panY = focusY - contentY * newScale - (height - drawableHeight * newScale) / 2f
        updateMatrix()
    }

    private fun updateMatrix() {
        val drawable = drawable ?: return
        val drawableWidth = drawable.intrinsicWidth.toFloat()
        val drawableHeight = drawable.intrinsicHeight.toFloat()
        if (drawableWidth <= 0f || drawableHeight <= 0f || width == 0 || height == 0) return
        val scale = min(width / drawableWidth, height / drawableHeight) * userScale
        val maxPanX = ((drawableWidth * scale - width) / 2f).coerceAtLeast(0f)
        val maxPanY = ((drawableHeight * scale - height) / 2f).coerceAtLeast(0f)
        panX = panX.coerceIn(-maxPanX, maxPanX)
        panY = panY.coerceIn(-maxPanY, maxPanY)
        val translateX = (width - drawableWidth * scale) / 2f + panX
        val translateY = (height - drawableHeight * scale) / 2f + panY
        drawMatrix.setScale(scale, scale)
        drawMatrix.postTranslate(translateX, translateY)
        imageMatrix = drawMatrix
    }

    private companion object {
        const val MIN_SCALE = 1f
        const val MAX_SCALE = 4f
        const val DOUBLE_TAP_SCALE = 2.5f
    }
}
