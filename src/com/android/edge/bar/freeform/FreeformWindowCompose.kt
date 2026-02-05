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

import android.content.Context
import android.graphics.SurfaceTexture
import android.util.Log
import android.view.TextureView
import com.android.edge.bar.freeform.presentation.FreeformStateManager
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.*
import androidx.compose.ui.platform.ComposeView
import kotlin.math.roundToInt
import androidx.lifecycle.*
import com.android.edge.bar.freeform.presentation.FreeformWindowContent
import com.android.edge.bar.freeform.presentation.FreeformWindowViewModel
import com.android.edge.bar.lifecycle.repeatWhenAttached

class FreeformWindowCompose(
    private val context: Context,
    private val packageName: String,
    private val activityName: String,
    private val userId: Int,
    private val taskId: Int = -1,
    private val desktopMode: Boolean = false
) : TextureView.SurfaceTextureListener {

    companion object {
        private const val TAG = "FreeformWindowCompose"
    }

    private lateinit var textureView: TextureView

    private val windowController = WindowController(context, packageName)

    private val viewModel = FreeformWindowViewModel(
        context = context,
        packageName = packageName,
        activityName = activityName,
        userId = userId,
        taskId = taskId,
        desktopMode = desktopMode,
        onWindowDead = { destroy("App killed externally") }
    )

    val stateManager: FreeformStateManager get() = viewModel.stateManager

    init {
        viewModel.register(this)
        createFreeformView()
    }

    private fun createFreeformView() {
        windowController.initWindowParams(
            width = viewModel.config.width,
            height = viewModel.config.height,
            x = viewModel.initialX.roundToInt(),
            y = viewModel.initialY.roundToInt()
        )

        val composeView = ComposeView(context).apply {
            repeatWhenAttached {
                repeatOnLifecycle(Lifecycle.State.STARTED) {
                    setContent {
                        FreeformApp()
                    }
                }
            }
        }

        viewModel.overlayView = composeView

        windowController.createWindow(composeView) { success ->
            if (!success) {
                Log.e(TAG, "Failed to create freeform window")
            }
        }
    }

    @Composable
    private fun FreeformApp() {
        val isDark = isSystemInDarkTheme()
        val colorScheme = if (isDark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        
        LaunchedEffect(Unit) {
            viewModel.stateManager.loadAppIcon()
        }

        MaterialTheme(colorScheme = colorScheme) {
            FreeformWindowContent(
                stateManager = viewModel.stateManager,
                stateFlow = viewModel.windowState,
                freeformWindowManager = viewModel.freeformWindowManager,
                density = viewModel.density,
                scope = viewModel.scope,
                onCloseAndKill = { closeWindow() },
                onBack = { viewModel.onBackPress() },
                onBringToFront = { bringToFront() },
                onMaximizeFullscreen = { 
                    viewModel.launchFullscreen { 
                        closeWindow() 
                    } 
                },
                onUpdateWindowLayout = { x, y, w, h -> windowController.updateLayout(x.roundToInt(), y.roundToInt(), w, h) },
                onSetupTextureView = { view ->
                    textureView = view
                    viewModel.setupTextureViewTouch(view)
                },
                textureViewListener = this@FreeformWindowCompose,
                inputInjector = viewModel.inputInjector
            )
        }
    }

    fun bringToFront() {
        windowController.bringToFront()
    }

    fun bringToBack() {
        windowController.bringToBack()
    }

    fun onOutsideTouch() {
        windowController.onOutsideTouch()
    }

    private fun closeWindow() {
        destroy("User closed freeform window")
    }

    override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) {
        viewModel.onSurfaceTextureAvailable(surface, width, height) { displayId ->
            Log.i(TAG, "Display ready: $displayId")
        }
    }

    override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) {}
    override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean = true
    override fun onSurfaceTextureUpdated(surface: SurfaceTexture) {}

    fun requestFocus() {
        windowController.requestFocus()
    }

    fun destroy(reason: String) {
        Log.i(TAG, "destroy called: $reason")

        if (::textureView.isInitialized) {
            textureView.surfaceTexture?.let { surface ->
                Log.i(TAG, "Releasing surface texture")
                surface.release()
            }
            textureView.surfaceTextureListener = null
        }
        
        windowController.destroy(reason) {
            viewModel.destroy()
        }
    }
}
