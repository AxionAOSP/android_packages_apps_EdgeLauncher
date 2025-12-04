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
package com.android.edge.bar.freeform.presentation

import android.content.Context
import android.graphics.SurfaceTexture
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Display
import android.view.MotionEvent
import android.view.Surface
import android.view.TextureView
import android.view.View
import android.view.WindowManager
import com.android.edge.bar.freeform.FreeformConfig
import com.android.edge.bar.freeform.FreeformWindowCompose
import com.android.edge.bar.freeform.FreeformWindowManager
import com.android.edge.bar.freeform.InputInjector
import com.android.edge.bar.freeform.data.FreeformRepository
import com.android.edge.bar.freeform.data.FreeformRepositoryImpl
import com.android.edge.bar.freeform.domain.FreeformConstants
import com.android.axion.kotlin.math.dpToPx
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.StateFlow

class FreeformWindowViewModel(
    private val context: Context,
    private val packageName: String,
    private val activityName: String,
    private val userId: Int,
    private val onWindowDead: (() -> Unit)? = null,
    private val serviceScope: CoroutineScope? = null
) {
    companion object {
        private const val TAG = "FreeformWindowViewModel"
    }

    private val handler = Handler(Looper.getMainLooper())
    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager

    val scope = serviceScope?.plus(SupervisorJob() + Dispatchers.Main) 
        ?: CoroutineScope(SupervisorJob() + Dispatchers.Main)

    val repository: FreeformRepository = FreeformRepositoryImpl(context)
    private val inputInjector = InputInjector(context)
    val freeformWindowManager = FreeformWindowManager.getInstance(context)

    private val displayMetrics = context.resources.displayMetrics

    val config = FreeformConfig(context = context, densityScale = 0.85f)

    val initialX: Int = (displayMetrics.widthPixels - config.width) / 2
    val initialY: Int = (displayMetrics.heightPixels - config.height) / 2
    val screenWidth: Int = displayMetrics.widthPixels
    val screenHeight: Int = displayMetrics.heightPixels
    val density: Float = displayMetrics.density

    private var displaySurface: Surface? = null
    private var textureViewRef: TextureView? = null

    var overlayView: View? = null

    val stateManager: FreeformStateManager by lazy {
        FreeformStateManager(
            context = context,
            repository = repository,
            scope = scope,
            packageName = packageName,
            freeformWindowManager = freeformWindowManager,
            initialWidth = config.width,
            initialHeight = config.height,
            hangupWidthDp = config.hangUpWidth,
            hangupHeightDp = config.hangUpHeight,
            screenWidth = screenWidth,
            screenHeight = screenHeight,
            densityDpi = density,
            initialX = initialX,
            initialY = initialY
        )
    }
    
    init {
        scope.launch {
            stateManager.effect.collect { effect ->
                when (effect) {
                    is FreeformEffect.ResizeDisplay -> resizeVirtualDisplay(effect.width, effect.height)
                }
            }
        }
    }
    
    val windowState: StateFlow<WindowState> get() = stateManager.state

    fun setupTextureViewTouch(view: View) {
        textureViewRef = view as? TextureView
        view.isClickable = true
        view.isFocusable = true
        view.isFocusableInTouchMode = true

        view.setOnTouchListener { v, event ->
            val currentState = stateManager.state.value
            val displayId = currentState.displayId
            if (displayId != Display.INVALID_DISPLAY && displayId > 0) {
                if (event.actionMasked == MotionEvent.ACTION_DOWN) {
                    val displayHeight = when (currentState.mode) {
                        WindowMode.HANGUP -> currentState.height
                        else -> currentState.height - context.dpToPx(FreeformConstants.TITLE_BAR_HEIGHT_DP)
                    }
                    inputInjector.setScale(v.width, v.height, currentState.width, displayHeight)
                }

                val result = inputInjector.injectTouchEvent(event)
                if (!result && event.actionMasked == MotionEvent.ACTION_DOWN) {
                    Log.w(TAG, "Failed to inject touch event to displayId=$displayId")
                }
            } else {
                if (event.actionMasked == MotionEvent.ACTION_DOWN) {
                    Log.w(TAG, "Touch ignored - invalid displayId: $displayId")
                }
            }
            true
        }
    }

    fun updateWindowLayout(x: Int, y: Int, width: Int, height: Int, windowParams: WindowManager.LayoutParams) {
        windowParams.x = x
        windowParams.y = y
        windowParams.width = width
        windowParams.height = height

        try {
            overlayView?.let { view ->
                if (view.isAttachedToWindow) {
                    windowManager.updateViewLayout(view, windowParams)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "updateViewLayout failed", e)
        }
    }

    fun resizeVirtualDisplay(width: Int, height: Int) {
        val currentState = stateManager.state.value
        val displayId = currentState.displayId
        if (displayId < 0) return
        
        val token = (repository as? FreeformRepositoryImpl)?.getAppToken() ?: return

        val targetDensity = if (currentState.mode == WindowMode.HANGUP) {
            (config.densityDpi * FreeformConstants.HANGUP_DENSITY_SCALE).toInt()
        } else {
            config.densityDpi
        }

        scope.launch {
            textureViewRef?.surfaceTexture?.setDefaultBufferSize(width, height)
            val result = repository.resizeDisplay(token, width, height, targetDensity)
            result.onSuccess { Log.i(TAG, "Virtual display resized to ${width}x${height} at ${targetDensity}dpi") }
                  .onFailure { Log.e(TAG, "Failed to resize virtual display", it) }
        }
    }

    fun onSurfaceTextureAvailable(
        surface: SurfaceTexture,
        width: Int,
        height: Int,
        onDisplayReady: (Int) -> Unit
    ) {
        val displayWidth = config.width
        val displayHeight = config.height - config.titleBarHeight
        
        Log.i(TAG, "onSurfaceTextureAvailable: callback=${width}x${height}, using config=${displayWidth}x${displayHeight}")

        surface.setDefaultBufferSize(displayWidth, displayHeight)

        val displaySurface = Surface(surface)
        this.displaySurface = displaySurface

        scope.launch {
            val callback = object : FreeformRepository.FreeformCallback {
                override fun onDisplayAdded(displayId: Int) {
                    Log.i(TAG, "onDisplayAdded: $displayId")
                    stateManager.setDisplayId(displayId)
                    inputInjector.setDisplayId(displayId)

                    scope.launch {
                        repository.launchApp(
                            packageName = packageName,
                            activityName = activityName,
                            displayId = displayId,
                            userId = userId
                        ).onSuccess {
                            Log.i(TAG, "App launched: $packageName/$activityName")
                            delay(FreeformConstants.DELAY_SURFACE_SETTLE_MS)
                            stateManager.markSurfaceReady()
                            onDisplayReady(displayId)
                            delay(FreeformConstants.DELAY_APP_LAUNCH_VEIL_MS)
                            stateManager.dispatch(WindowEvent.AppLaunchComplete)
                        }.onFailure {
                            Log.e(TAG, "Failed to launch app", it)
                            stateManager.dispatch(WindowEvent.AppLaunchComplete)
                        }
                    }
                }

                override fun onDisplayPaused() {
                    Log.d(TAG, "Display paused")
                    stateManager.dispatch(WindowEvent.DisplayPaused)
                }

                override fun onDisplayResumed() {
                    Log.d(TAG, "Display resumed")
                    stateManager.dispatch(WindowEvent.DisplayResumed)
                }

                override fun onDisplayStopped() {
                    Log.d(TAG, "Display stopped - cleaning up window")
                    stateManager.dispatch(WindowEvent.DisplayStopped)
                    handler.post { onWindowDead?.invoke() }
                }
            }

            val result = repository.createDisplay(displaySurface, displayWidth, displayHeight, config.densityDpi, callback)
            if (result < 0) {
                Log.e(TAG, "Failed to create display")
            } else {
                Log.d(TAG, "Display creation initiated...")
            }
        }
    }

    fun requestFocus() {
        overlayView?.requestFocus()
    }

    fun destroy() {
        stateManager.destroy()
        
        val token = (repository as? FreeformRepositoryImpl)?.getAppToken()
        token?.let {
            val releaseScope = serviceScope ?: CoroutineScope(SupervisorJob() + Dispatchers.Main)
            releaseScope.launch(NonCancellable) {
                repository.releaseDisplay(it)
                    .onSuccess { Log.i(TAG, "Display released successfully") }
                    .onFailure { Log.e(TAG, "Failed to release display", it) }
            }
        }

        displaySurface?.let {
            it.release()
            displaySurface = null
        }

        scope.cancel()
        freeformWindowManager.unregisterWindow(packageName)
    }

    fun register(window: FreeformWindowCompose) {
        freeformWindowManager.registerWindow(packageName, window)
    }
}
