/*
 * Copyright (C) 2025 AxionOS Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.edge.bar.freeform

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.content.Context
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.animation.AccelerateInterpolator
import android.view.animation.DecelerateInterpolator
import androidx.compose.ui.platform.ComposeView

class WindowController(
    private val context: Context,
    private val packageName: String
) {
    companion object {
        private const val TAG = "WindowController"
        private const val SCALE_DURATION_MS = 300L
    }

    private val handler = Handler(Looper.getMainLooper())
    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    
    val windowParams = WindowManager.LayoutParams()
    var composeView: ComposeView? = null
        private set

    private inline fun runOnMain(crossinline block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            block()
        } else {
            handler.post { block() }
        }
    }

    fun initWindowParams(width: Int, height: Int, x: Int, y: Int) = runOnMain {
        windowParams.apply {
            type = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            this.width = width
            this.height = height
            this.x = x
            this.y = y
            format = PixelFormat.TRANSLUCENT
            gravity = Gravity.TOP or Gravity.START
            flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                    WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED or
                    WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH
            privateFlags = WindowManager.LayoutParams.SYSTEM_FLAG_SHOW_FOR_ALL_USERS or
                    WindowManager.LayoutParams.PRIVATE_FLAG_TRUSTED_OVERLAY
        }
    }

    fun createWindow(view: ComposeView, onResult: ((Boolean) -> Unit)? = null) = runOnMain {
        composeView = view
        try {
            windowManager.addView(view, windowParams)
            scaleInInternal()
            onResult?.invoke(true)
        } catch (e: Exception) {
            Log.e(TAG, "addView failed", e)
            onResult?.invoke(false)
        }
    }

    fun bringToFront() = runOnMain {
        try {
            composeView?.let { view ->
                if (view.isAttachedToWindow) {
                    windowManager.bringViewToFront(view)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "bringToFront failed", e)
        }
    }

    fun updateLayout(x: Int, y: Int, width: Int, height: Int) = runOnMain {
        windowParams.x = x
        windowParams.y = y
        windowParams.width = width
        windowParams.height = height

        try {
            composeView?.let { view ->
                if (view.isAttachedToWindow) {
                    windowManager.updateViewLayout(view, windowParams)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "updateViewLayout failed", e)
        }
    }

    private fun scaleInInternal() {
        composeView?.let { view ->
            val scaleX = ObjectAnimator.ofFloat(view, View.SCALE_X, 0.8f, 1f)
            val scaleY = ObjectAnimator.ofFloat(view, View.SCALE_Y, 0.8f, 1f)
            val alpha = ObjectAnimator.ofFloat(view, View.ALPHA, 0f, 1f)

            AnimatorSet().apply {
                playTogether(scaleX, scaleY, alpha)
                duration = SCALE_DURATION_MS
                interpolator = DecelerateInterpolator()
                start()
            }
        }
    }

    fun scaleOut(onComplete: () -> Unit) = runOnMain {
        composeView?.let { view ->
            val scaleX = ObjectAnimator.ofFloat(view, View.SCALE_X, 1f, 0.8f)
            val scaleY = ObjectAnimator.ofFloat(view, View.SCALE_Y, 1f, 0.8f)
            val alpha = ObjectAnimator.ofFloat(view, View.ALPHA, 1f, 0f)

            AnimatorSet().apply {
                playTogether(scaleX, scaleY, alpha)
                duration = SCALE_DURATION_MS
                interpolator = AccelerateInterpolator()
                addListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: Animator) {
                        onComplete()
                    }
                })
                start()
            }
        } ?: onComplete()
    }

    fun requestFocus() = runOnMain {
        composeView?.requestFocus()
    }

    fun onOutsideTouch() = runOnMain {
    }

    fun destroy(reason: String, onDestroyed: () -> Unit) = runOnMain {
        Log.i(TAG, "destroy: $reason")
        composeView?.let { view ->
            if (view.isAttachedToWindow) {
                view.alpha = 0f
                scaleOut {
                    removeWindowInternal()
                    onDestroyed()
                }
            } else {
                onDestroyed()
            }
        } ?: onDestroyed()
    }

    private fun removeWindowInternal() {
        try {
            composeView?.let { view ->
                if (view.isAttachedToWindow) {
                    windowManager.removeViewImmediate(view)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "removeView failed", e)
        }
        composeView = null
    }

    fun removeWindow() = runOnMain {
        removeWindowInternal()
    }
}