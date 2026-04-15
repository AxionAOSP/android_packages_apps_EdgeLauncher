/*
 * Copyright (C) 2025-2026 AxionOS Project
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
package com.android.edge.bar

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.PixelFormat
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.WindowManager.LayoutParams
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.activity.OnBackPressedDispatcher
import androidx.activity.OnBackPressedDispatcherOwner
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.repeatOnLifecycle
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import com.android.edge.bar.lifecycle.repeatWhenAttached
import com.android.edge.bar.settings.SettingsActivity
import kotlinx.coroutines.*
import com.android.internal.policy.SystemBarUtils
import kotlin.math.roundToInt

class EdgeSideBar(
    private val context: Context,
    private val callback: Callback
) {
    interface Callback {
        fun onRemove()
    }

    private val noOpBackDispatcherOwner = object : OnBackPressedDispatcherOwner {
        override val onBackPressedDispatcher = OnBackPressedDispatcher()
        private val reg = LifecycleRegistry(this).apply { currentState = Lifecycle.State.RESUMED }
        override val lifecycle: Lifecycle get() = reg
    }

    private lateinit var panelView: View

    @Volatile
    private var isShowing = false

    private val panelVisible = mutableStateOf(false)
    private val fromRight = mutableStateOf(true)

    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val mainScope = MainScope()

    private var sidebarPositionX = 0
    private var sidebarPositionY = 0
    private var xPos = 0
    private var yPos = 0

    private var dragAccumX = 0f
    private var dragAccumY = 0f
    private var cachedStatusBarHeight = 0
    private var cachedNavBarHeight = 0

    private val density get() = context.resources.displayMetrics.density
    private val screenWidth get() = context.resources.displayMetrics.widthPixels
    private val screenHeight get() = context.resources.displayMetrics.heightPixels
    private val isPortrait get() = context.resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT

    private val panelWidthPx get() = (PANEL_WIDTH_DP * density).roundToInt()
    private val panelHeightPx get() = (PANEL_HEIGHT_DP * density).roundToInt()
    private val marginPx get() = (PANEL_MARGIN_DP * density).roundToInt()

    fun showPanelView() {
        synchronized(this) {
            if (isShowing) return
            if (::panelView.isInitialized && panelView.isAttachedToWindow) {
                removeViewSafely(panelView)
            }
            updateSidebarPosition()
            fromRight.value = sidebarPositionX > 0
            panelView = createComposeView {
                EdgeContentView(
                    onPinnedAppClick = { _, pkg, activityName ->
                        removePanelView()
                        val launchMode = Settings.Secure.getInt(
                            context.contentResolver,
                            EdgeService.LAUNCH_MODE,
                            0
                        )
                        when (launchMode) {
                            1 -> AppHelper.launchApp(pkg)
                            2 -> if (AppHelper.isBubbleSupported()) {
                                AppHelper.launchAsBubble(context, pkg, activityName)
                            } else {
                                AppHelper.launchApp(pkg)
                            }
                            else -> AppHelper.launchAppFull(context, pkg)
                        }
                    },
                    onSettingsClick = {
                        removePanelView()
                        context.startActivity(
                            Intent(context, SettingsActivity::class.java)
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                    },
                    onDrag = { dx, dy -> handlePanelDrag(dx, dy) },
                    onDragEnd = { handlePanelDragEnd() },
                    panelOnRight = fromRight.value
                )
            }
            val lp = createPanelLayoutParams()
            addViewSafely(panelView, lp)
            isShowing = true
        }
    }

    fun removePanelView(force: Boolean = false) {
        synchronized(this) {
            if (::panelView.isInitialized) {
                if (!isShowing && !force) return
                isShowing = false
                val viewToRemove = panelView
                if (force) {
                    panelVisible.value = false
                    removeViewSafely(viewToRemove)
                } else {
                    panelVisible.value = false
                    mainScope.launch(Dispatchers.Main) {
                        delay(EXIT_ANIM_DURATION)
                        removeViewSafely(viewToRemove)
                    }
                }
            }
            callback.onRemove()
        }
    }

    fun updateSidebarPosition() {
        cachedStatusBarHeight = SystemBarUtils.getStatusBarHeight(context)
        cachedNavBarHeight = if (isPortrait) {
            context.resources.getDimensionPixelSize(
                com.android.internal.R.dimen.navigation_bar_height
            )
        } else 0

        sidebarPositionX = Settings.Secure.getInt(
            context.contentResolver,
            EdgeService.SIDELINE_POSITION_X,
            1
        )

        sidebarPositionY = if (isPortrait) {
            Settings.Secure.getInt(
                context.contentResolver,
                EdgeService.SIDELINE_POSITION_Y_PORTRAIT,
                -screenHeight / 6
            )
        } else {
            Settings.Secure.getInt(
                context.contentResolver,
                EdgeService.SIDELINE_POSITION_Y_LANDSCAPE,
                -screenHeight / 6
            )
        }

        xPos = if (sidebarPositionX > 0) {
            screenWidth - panelWidthPx - marginPx
        } else {
            marginPx
        }

        yPos = constrainY((screenHeight - panelHeightPx) / 2 + sidebarPositionY)

        if (isShowing) {
            mainScope.launch(Dispatchers.Main) {
                try {
                    if (panelView.isAttachedToWindow) {
                        val lp = panelView.layoutParams as LayoutParams
                        lp.x = xPos
                        lp.y = yPos
                        windowManager.updateViewLayout(panelView, lp)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to update view layout", e)
                }
            }
        }
    }

    private fun handlePanelDrag(deltaX: Float, deltaY: Float) {
        dragAccumX += deltaX
        val newY = yPos + (dragAccumY + deltaY).roundToInt()
        val constrainedY = constrainY(newY)
        dragAccumY += deltaY
        if (constrainedY != newY) {
            dragAccumY = (constrainedY - yPos).toFloat()
        }
        try {
            if (::panelView.isInitialized && panelView.isAttachedToWindow) {
                val lp = panelView.layoutParams as LayoutParams
                lp.x = xPos + dragAccumX.roundToInt()
                lp.y = yPos + dragAccumY.roundToInt()
                windowManager.updateViewLayout(panelView, lp)
            }
        } catch (_: Exception) {}
    }

    private fun handlePanelDragEnd() {
        xPos += dragAccumX.roundToInt()
        yPos = constrainY(yPos + dragAccumY.roundToInt())

        val panelCenterX = xPos + panelWidthPx / 2
        sidebarPositionX = if (panelCenterX > screenWidth / 2) 1 else -1
        fromRight.value = sidebarPositionX > 0

        xPos = if (sidebarPositionX > 0) {
            screenWidth - panelWidthPx - marginPx
        } else {
            marginPx
        }

        yPos = constrainY(yPos)
        sidebarPositionY = yPos - (screenHeight - panelHeightPx) / 2

        dragAccumX = 0f
        dragAccumY = 0f

        Settings.Secure.putInt(
            context.contentResolver,
            EdgeService.SIDELINE_POSITION_X,
            sidebarPositionX
        )
        Settings.Secure.putInt(
            context.contentResolver,
            if (isPortrait) EdgeService.SIDELINE_POSITION_Y_PORTRAIT
            else EdgeService.SIDELINE_POSITION_Y_LANDSCAPE,
            sidebarPositionY
        )

        mainScope.launch(Dispatchers.Main) {
            try {
                if (::panelView.isInitialized && panelView.isAttachedToWindow) {
                    val lp = panelView.layoutParams as LayoutParams
                    lp.x = xPos
                    lp.y = yPos
                    windowManager.updateViewLayout(panelView, lp)
                }
            } catch (_: Exception) {}
        }
    }

    private fun constrainY(y: Int): Int {
        return y.coerceIn(cachedStatusBarHeight, screenHeight - panelHeightPx - cachedNavBarHeight)
    }

    private fun createPanelLayoutParams(): LayoutParams {
        return LayoutParams().apply {
            type = LayoutParams.TYPE_APPLICATION_OVERLAY
            flags = LayoutParams.FLAG_NOT_FOCUSABLE or
                    LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH or
                    LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    LayoutParams.FLAG_HARDWARE_ACCELERATED
            format = PixelFormat.TRANSLUCENT
            layoutInDisplayCutoutMode = LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_NEVER
            width = LayoutParams.WRAP_CONTENT
            height = LayoutParams.WRAP_CONTENT
            gravity = Gravity.TOP or Gravity.START
            x = xPos
            y = yPos
        }
    }

    private fun createComposeView(content: @Composable () -> Unit): ComposeView {
        return ComposeView(context).apply {
            repeatWhenAttached {
                repeatOnLifecycle(Lifecycle.State.STARTED) {
                    setViewCompositionStrategy(
                        ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed
                    )
                    setContent {
                        CompositionLocalProvider(
                            LocalOnBackPressedDispatcherOwner provides noOpBackDispatcherOwner
                        ) {
                            val isDark = isSystemInDarkTheme()
                            val colorScheme = if (isDark) dynamicDarkColorScheme(context)
                                else dynamicLightColorScheme(context)
                            val slideRight = fromRight.value
                            val visibility = remember { Animatable(0f) }
                            LaunchedEffect(panelVisible.value) {
                                if (panelVisible.value) {
                                    visibility.animateTo(
                                        1f,
                                        tween(ENTER_ANIM_DURATION.toInt())
                                    )
                                } else {
                                    visibility.animateTo(
                                        0f,
                                        tween(EXIT_ANIM_DURATION.toInt())
                                    )
                                }
                            }
                            LaunchedEffect(Unit) {
                                panelVisible.value = true
                            }
                            MaterialTheme(colorScheme = colorScheme) {
                                Box(
                                    modifier = Modifier.graphicsLayer {
                                        alpha = visibility.value
                                        val dir = if (slideRight) 1f else -1f
                                        translationX = dir * 40f * (1f - visibility.value)
                                    }
                                ) {
                                    content()
                                }
                            }
                        }
                    }
                }
            }

            setOnTouchListener { view, event ->
                if (event.action == MotionEvent.ACTION_OUTSIDE) {
                    if (isShowing) removePanelView()
                    true
                } else {
                    view.performClick()
                    false
                }
            }
        }
    }

    private fun addViewSafely(view: View, lp: LayoutParams) {
        mainScope.launch(Dispatchers.Main) {
            runCatching { windowManager.addView(view, lp) }
        }
    }

    private fun removeViewSafely(view: View) {
        mainScope.launch(Dispatchers.Main) {
            runCatching {
                if (view.isAttachedToWindow) {
                    windowManager.removeViewImmediate(view)
                }
            }
        }
    }

    fun release() {
        mainScope.cancel()
    }

    companion object {
        private const val TAG = "EdgeSideBar"
        const val PANEL_WIDTH_DP = 130f
        const val PANEL_HEIGHT_DP = 336f
        const val PANEL_MARGIN_DP = 10f
        private const val ENTER_ANIM_DURATION = 250L
        private const val EXIT_ANIM_DURATION = 200L
    }
}
