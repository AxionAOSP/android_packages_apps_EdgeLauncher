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
import android.util.Log
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
    private val lp = LayoutParams()
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
                    xPos = xPos,
                    yPos = yPos,
                    sidebarHeight = (sidebarHeight / context.resources.displayMetrics.density).roundToInt()
                )
            }
            configureLayoutParams(lp)
            addViewSafely(panelView)
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
    }

    fun showAppDrawerView() {
        synchronized(this) {
            if (appDrawerState != AppDrawerState.HIDDEN) return
            updateSidebarPosition()
            
            val drawerWidth = (screenWidth * 0.8f).roundToInt()
            val drawerHeight = sidebarHeight
            val drawerX = if (sidebarPositionX > 0) {
                screenWidth - drawerWidth - 32 
            } else {
                32
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
                    xPos = drawerX,
                    yPos = yPos,
                    drawerWidth = (drawerWidth / context.resources.displayMetrics.density).roundToInt(),
                    drawerHeight = (drawerHeight / context.resources.displayMetrics.density).roundToInt()
                )
            }
            configureLayoutParams(lp)
            addViewSafely(appDrawerView)
            appDrawerState = AppDrawerState.EXPANDED
        }
    }

    fun removeAppDrawerView() {
        synchronized(this) {
            if (appDrawerState == AppDrawerState.HIDDEN) return
            removeViewSafely(appDrawerView)
            appDrawerState = AppDrawerState.HIDDEN
        }
    }

    fun updateSidebarPosition() {
        sidebarHeight = if (isPortrait) {
            (screenHeight * 0.45f).roundToInt()
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
            0
        }

        lp.apply {
            width = LayoutParams.MATCH_PARENT
            height = LayoutParams.MATCH_PARENT
        }

        xPos = if (sidebarPositionX > 0) {
            screenWidth - SIDEBAR_WIDTH 
        } else {
            0
        }
        
        yPos = (screenHeight - sidebarHeight) / 2 + sidebarPositionY

        if (isShowing) {
            mainScope.launch(Dispatchers.Main) {
                try {
                    if (panelView.isAttachedToWindow) {
                        windowManager.updateViewLayout(panelView, lp)
                    }
                } catch (e: Exception) {
                    Log.e("EdgeSideBar", "Failed to update view layout", e)
                }
            }
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
        }
    }

    private fun configureLayoutParams(lp: LayoutParams) = lp.apply {
        type = LayoutParams.TYPE_APPLICATION_OVERLAY
        flags = LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                LayoutParams.FLAG_HARDWARE_ACCELERATED
        format = PixelFormat.RGBA_8888
        windowAnimations = android.R.style.Animation_Dialog
        layoutInDisplayCutoutMode = LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_NEVER
    }

    private fun addViewSafely(view: View) {
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

    companion object {
        private const val SIDEBAR_WIDTH = 72 * 3
    }
}
