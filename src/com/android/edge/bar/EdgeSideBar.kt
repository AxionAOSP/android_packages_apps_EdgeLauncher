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
package com.android.edge.bar

import android.content.Context
import android.content.res.Configuration
import android.graphics.PixelFormat
import android.provider.Settings
import android.os.Process
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.WindowManager.LayoutParams
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.android.edge.bar.lifecycle.repeatWhenAttached
import kotlinx.coroutines.*
import kotlin.math.roundToInt

class EdgeSideBar(
    private val context: Context,
    private val callback: Callback
) {
    interface Callback {
        fun onRemove()
    }

    private lateinit var panelView: View
    private lateinit var appDrawerView: View

    @Volatile
    private var isShowing = false
    @Volatile
    private var appDrawerState = AppDrawerState.HIDDEN

    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val mainScope = MainScope()

    private var sidebarPositionX = 0
    private var sidebarPositionY = 0
    
    private var xPos = 0
    private var yPos = 0
    private var sidebarHeight = 0

    private val screenWidth get() = context.resources.displayMetrics.widthPixels
    private val screenHeight get() = context.resources.displayMetrics.heightPixels
    private val isPortrait get() = context.resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT

    fun showPanelView() {
        Process.setThreadAffinity(Process.myPid(), 2)
        Process.setThreadGroupAndCpuset(Process.myPid(), Process.THREAD_GROUP_TOP_APP)
        Process.setProcessGroup(Process.myPid(), Process.THREAD_GROUP_TOP_APP)
        synchronized(this) {
            if (isShowing) return
            updateSidebarPosition()
            panelView = createComposeView {
                EdgeContentView(
                    onAppDrawerClick = { removePanelView(); showAppDrawerView() },
                    onPanelTap = { removePanelView() },
                    onPinnedAppClick = { ctx, pkg ->
                        removePanelView()
                        AppHelper.launchApp(ctx, pkg)
                    },
                    sidebarHeight = (sidebarHeight / context.resources.displayMetrics.density).roundToInt()
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
                removeViewSafely(panelView)
                isShowing = false
            }
            callback.onRemove()
        }
        Process.setThreadAffinity(Process.myPid(), 1)
        Process.setThreadGroupAndCpuset(Process.myPid(), 9)
        Process.setProcessGroup(Process.myPid(), 9)
    }

    fun showAppDrawerView() {
        Process.setThreadAffinity(Process.myPid(), 2)
        Process.setThreadGroupAndCpuset(Process.myPid(), Process.THREAD_GROUP_TOP_APP)
        Process.setProcessGroup(Process.myPid(), Process.THREAD_GROUP_TOP_APP)
        synchronized(this) {
            if (appDrawerState != AppDrawerState.HIDDEN) return
            updateSidebarPosition()
            
            val drawerWidth = (screenWidth * 0.8f).roundToInt()
            val drawerHeight = sidebarHeight
            
            val drawerMargin = (8 * context.resources.displayMetrics.density).toInt()
            
            val drawerX = if (sidebarPositionX > 0) {
                (screenWidth / 2) + drawerMargin
            } else {
                -(screenWidth / 2) + drawerMargin
            }
            
            appDrawerView = createComposeView {
                AppDrawerContentView(
                    onDismiss = {
                        removeAppDrawerView()
                        showPanelView()
                    },
                    onAppClick = { ctx, pkg ->
                        removeAppDrawerView()
                        AppHelper.launchApp(ctx, pkg)
                    },
                    drawerWidth = (drawerWidth / context.resources.displayMetrics.density).roundToInt(),
                    drawerHeight = (drawerHeight / context.resources.displayMetrics.density).roundToInt()
                )
            }
            val lp = createAppDrawerLayoutParams(drawerX, yPos)
            addViewSafely(appDrawerView, lp)
            appDrawerState = AppDrawerState.EXPANDED
        }
    }

    fun removeAppDrawerView() {
        synchronized(this) {
            if (appDrawerState == AppDrawerState.HIDDEN) return
            removeViewSafely(appDrawerView)
            appDrawerState = AppDrawerState.HIDDEN
        }
        Process.setThreadAffinity(Process.myPid(), 1)
        Process.setThreadGroupAndCpuset(Process.myPid(), 9)
        Process.setProcessGroup(Process.myPid(), 9)
    }

    fun updateSidebarPosition() {
        sidebarHeight = if (isPortrait) {
            (screenHeight * 0.4f).roundToInt()
        } else {
            (screenHeight * 0.85f).roundToInt()
        }

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
            screenWidth - SIDEBAR_WIDTH - offset
        } else {
            offset
        }
        
        yPos = (screenHeight - sidebarHeight) / 2 + sidebarPositionY

        if (isShowing) {
            mainScope.launch(Dispatchers.Main) {
                try {
                    if (panelView.isAttachedToWindow) {
                        val lp = createPanelLayoutParams()
                        windowManager.updateViewLayout(panelView, lp)
                    }
                } catch (e: Exception) {
                    Log.e("EdgeSideBar", "Failed to update view layout", e)
                }
            }
        }
    }

    private fun createBaseLayoutParams(): LayoutParams {
        return LayoutParams().apply {
            type = LayoutParams.TYPE_APPLICATION_OVERLAY
            flags = LayoutParams.FLAG_NOT_FOCUSABLE or
                    LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH or
                    LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    LayoutParams.FLAG_HARDWARE_ACCELERATED
            format = PixelFormat.TRANSLUCENT
            windowAnimations = android.R.style.Animation_Dialog
            layoutInDisplayCutoutMode = LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_NEVER
            width = LayoutParams.WRAP_CONTENT
            height = LayoutParams.WRAP_CONTENT
        }
    }

    private fun createPanelLayoutParams(): LayoutParams {
        return createBaseLayoutParams().apply {
            gravity = Gravity.TOP or Gravity.START
            x = xPos
            y = yPos
        }
    }

    private fun createAppDrawerLayoutParams(drawerX: Int, drawerY: Int): LayoutParams {
        return createBaseLayoutParams().apply {
            gravity = if (isPortrait) Gravity.TOP or Gravity.START
                    else Gravity.CENTER
            x = drawerX
            y = drawerY
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
                        val isDark = isSystemInDarkTheme()
                        val colorScheme = if (isDark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
                        MaterialTheme(colorScheme = colorScheme) {
                            content()
                        }
                    }
                }
            }
            
            setOnTouchListener { view, event ->
                if (event.action == android.view.MotionEvent.ACTION_OUTSIDE) {
                    if (isShowing) {
                        removePanelView()
                    }
                    if (appDrawerState != AppDrawerState.HIDDEN) {
                        removeAppDrawerView()
                        showPanelView()
                    }
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
            try {
                windowManager.addView(view, lp)
                view.animate().translationX(0f).setDuration(300).start()
            } catch (e: Exception) {
                Log.e("EdgeSideBar", "Failed to add view", e)
            }
        }
    }

    private fun removeViewSafely(view: View) {
        mainScope.launch(Dispatchers.Main) {
            try {
                if (view.isAttachedToWindow) {
                    windowManager.removeViewImmediate(view)
                }
            } catch (e: Exception) {
                Log.e("EdgeSideBar", "Failed to remove view", e)
            }
        }
    }

    fun release() {
        mainScope.cancel()
    }

    private enum class AppDrawerState { HIDDEN, EXPANDED }

    private val offset: Int
        get() = if (isPortrait) OFFSET_PORTRAIT else OFFSET_LANDSCAPE

    companion object {
        const val SIDEBAR_WIDTH = 192
        private const val OFFSET_PORTRAIT = 20
        private const val OFFSET_LANDSCAPE = 0
    }
}
