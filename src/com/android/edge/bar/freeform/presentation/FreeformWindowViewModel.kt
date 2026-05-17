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

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.graphics.SurfaceTexture
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.*
import com.android.edge.bar.freeform.*
import com.android.edge.bar.freeform.data.*
import com.android.edge.bar.freeform.domain.FreeformConstants
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.StateFlow

class FreeformWindowViewModel(
    private val context: Context,
    private val packageName: String,
    private val activityName: String,
    private val userId: Int,
    private val taskId: Int = -1,
    private val desktopMode: Boolean = false,
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
    val inputInjector = InputInjector(context)
    val freeformWindowManager = FreeformWindowManager.getInstance(context)

    private val displayMetrics = context.resources.displayMetrics

    val config = FreeformConfig(context = context, densityScale = 0.85f, isDesktopMode = desktopMode)

    val initialX: Float = ((displayMetrics.widthPixels - config.width) / 2).toFloat()
    val initialY: Float = ((displayMetrics.heightPixels - config.height) / 2).toFloat()
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
            windowPackageName = packageName,
            freeformWindowManager = freeformWindowManager,
            initialWidth = config.width,
            initialHeight = config.height,
            screenWidth = screenWidth,
            screenHeight = screenHeight,
            densityDpi = density,
            initialX = initialX,
            initialY = initialY,
            initialMode = if (desktopMode) WindowMode.DESKTOP else WindowMode.NORMAL
        )
    }
    
    private val windowEventListener = object : FreeformWindowManager.WindowEventListener {
        override fun onWindowOrientationChanged(packageName: String, isLandscape: Boolean) {
            if (packageName == this@FreeformWindowViewModel.packageName) {
                stateManager.onOrientationChanged(isLandscape)
                val currentState = stateManager.state.value
                saveWindowState(currentState.width, currentState.height, isLandscape)
            }
        }
    }
    
    init {
        scope.launch {
            stateManager.effect.collect { effect ->
                when (effect) {
                    is FreeformEffect.ResizeDisplay -> resizeVirtualDisplay(effect.width, effect.height)
                }
            }
        }
        freeformWindowManager.addListener(windowEventListener)
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
                    val (displayWidth, displayHeight) = stateManager.getAppSurfaceDimensions(
                        windowWidth = currentState.width,
                        windowHeight = currentState.height,
                        mode = currentState.mode
                    )
                    inputInjector.setScale(v.width, v.height, displayWidth, displayHeight)
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

    fun updateWindowLayout(x: Float, y: Float, width: Int, height: Int, windowParams: WindowManager.LayoutParams) {
        windowParams.x = x.toInt()
        windowParams.y = y.toInt()
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

        val targetDensity = config.densityDpi

        scope.launch {
            textureViewRef?.let { view ->
                view.surfaceTexture?.setDefaultBufferSize(width, height)
                inputInjector.setScale(view.width, view.height, width, height)
            }
            val result = repository.resizeDisplay(token, width, height, targetDensity)
            result.onSuccess { 
                val latestState = stateManager.state.value
                Log.i(TAG, "Virtual display resized to ${width}x${height} at ${targetDensity}dpi")
                saveWindowState(latestState.width, latestState.height, latestState.isLandscape)
            }.onFailure { Log.e(TAG, "Failed to resize virtual display", it) }
        }
    }

    private fun resolveInitialOrientation(packageName: String, activityName: String): Int {
        return try {
            val ensureComponent = ComponentName(packageName, activityName)
            val activityInfo = context.packageManager.getActivityInfo(ensureComponent, 0)
            activityInfo.screenOrientation
        } catch (e: Exception) {
            Log.e(TAG, "Failed to resolve initial orientation for $packageName/$activityName", e)
            ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }

    private fun isLandscapeOrientation(orientation: Int): Boolean {
        return when (orientation) {
            ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE,
            ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE,
            ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE,
            ActivityInfo.SCREEN_ORIENTATION_USER_LANDSCAPE -> true
            else -> false
        }
    }
    
    private fun isPortraitOrientation(orientation: Int): Boolean {
        return when (orientation) {
            ActivityInfo.SCREEN_ORIENTATION_PORTRAIT,
            ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT,
            ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT,
            ActivityInfo.SCREEN_ORIENTATION_USER_PORTRAIT -> true
            else -> false
        }
    }
    
    private fun resolveInitialLandscape(packageName: String, activityName: String): Boolean {
        val orientation = resolveInitialOrientation(packageName, activityName)
        if (isLandscapeOrientation(orientation)) return true
        if (isPortraitOrientation(orientation)) return false
        return false
    }

    
    private fun getPrefs() = context.getSharedPreferences("freeform_window_prefs", Context.MODE_PRIVATE)
    private fun getPrefKey(suffix: String): String {
        val modePrefix = if (desktopMode) "desktop_" else ""
        return "${packageName}_${activityName}_$modePrefix$suffix"
    }

    private fun saveWindowState(width: Int, height: Int, isLandscape: Boolean) {
        getPrefs().edit().apply {
            putInt(getPrefKey("width"), width)
            putInt(getPrefKey("height"), height)
            putBoolean(getPrefKey("is_landscape"), isLandscape)
            apply()
        }
    }
    
    private fun clearWindowState() {
         getPrefs().edit().apply {
            remove(getPrefKey("width"))
            remove(getPrefKey("height"))
            remove(getPrefKey("is_landscape"))
            apply()
        }
    }

    private data class PersistedState(val width: Int, val height: Int, val isLandscape: Boolean)

    private fun restoreWindowState(): PersistedState? {
        val prefs = getPrefs()
        if (!prefs.contains(getPrefKey("width"))) return null
        
        val width = prefs.getInt(getPrefKey("width"), config.width)
        val height = prefs.getInt(getPrefKey("height"), config.height)
        val isLandscape = prefs.getBoolean(getPrefKey("is_landscape"), false)
        return PersistedState(width, height, isLandscape)
    }

    fun onSurfaceTextureAvailable(
        surface: SurfaceTexture,
        width: Int,
        height: Int,
        onDisplayReady: (Int) -> Unit
    ) {
        var orientation = resolveInitialOrientation(packageName, activityName)
        var isLandscape = isLandscapeOrientation(orientation)
        var isPortrait = isPortraitOrientation(orientation)
        
        var windowWidth = config.width
        var windowHeight = config.height
        
        val persisted = restoreWindowState()
        if (persisted != null) {
            windowWidth = persisted.width
            windowHeight = persisted.height
            isLandscape = persisted.isLandscape
            Log.i(TAG, "Restored persisted state: ${windowWidth}x${windowHeight}, isLandscape=$isLandscape")
        } else {
            Log.i(TAG, "Resolved orientation: $orientation (landscape=$isLandscape, portrait=$isPortrait) for $packageName/$activityName")
            if (isLandscape && windowWidth < windowHeight) {
                val temp = windowHeight
                windowWidth = (windowHeight * 1.3f).toInt()
                windowHeight = temp.coerceAtMost(config.width)
                Log.i(TAG, "Landscape app detected, adjusted dimensions to ${windowWidth}x${windowHeight}")
            } else if (!isLandscape && windowWidth > windowHeight) {
                val temp = windowWidth
                windowWidth = windowHeight
                windowHeight = temp
                Log.i(TAG, "Portrait adjustment, swapped to ${windowWidth}x${windowHeight}")
            }
        }
        
        stateManager.restoreWindowSize(windowWidth, windowHeight, isLandscape)
        val restoredState = stateManager.state.value
        windowWidth = restoredState.width
        windowHeight = restoredState.height

        saveWindowState(windowWidth, windowHeight, isLandscape)
        
        if (isLandscape) {
            stateManager.onOrientationChanged(true)
        } else {
             stateManager.onOrientationChanged(false)
        }
        
        val (displayWidth, displayHeight) = stateManager.getAppSurfaceDimensions(
            windowWidth = windowWidth,
            windowHeight = windowHeight,
            mode = restoredState.mode
        )

        Log.i(
            TAG,
            "onSurfaceTextureAvailable: window=${windowWidth}x${windowHeight}, " +
                "display=${displayWidth}x${displayHeight}, isLandscape=$isLandscape"
        )

        surface.setDefaultBufferSize(displayWidth, displayHeight)
        inputInjector.setScale(width, height, displayWidth, displayHeight)

        val displaySurface = Surface(surface)
        this.displaySurface = displaySurface

        scope.launch {
            var attachFinished = false
            var attachTimeoutJob: Job? = null

            suspend fun finishAttach(displayId: Int, onDisplayReady: (Int) -> Unit) {
                if (attachFinished) return
                attachFinished = true
                attachTimeoutJob?.cancel()
                stateManager.markSurfaceReady()
                onDisplayReady(displayId)
                delay(FreeformConstants.DELAY_APP_LAUNCH_VEIL_MS)
                stateManager.dispatch(WindowEvent.AppLaunchComplete)
            }

            fun failAttach(message: String, throwable: Throwable? = null) {
                if (attachFinished) return
                attachFinished = true
                attachTimeoutJob?.cancel()
                if (throwable != null) {
                    Log.e(TAG, message, throwable)
                } else {
                    Log.e(TAG, message)
                }
                stateManager.dispatch(WindowEvent.AppLaunchComplete)
                handler.post { onWindowDead?.invoke() }
            }

            suspend fun launchAppOnDisplay(displayId: Int, onDisplayReady: (Int) -> Unit) {
                repository.launchApp(
                    packageName = packageName,
                    activityName = activityName,
                    displayId = displayId,
                    userId = userId
                ).onSuccess {
                    Log.i(TAG, "App launched: $packageName/$activityName on display $displayId")
                    delay(FreeformConstants.DELAY_SURFACE_SETTLE_MS)
                    val launchedTaskId = repository.findRunningTaskId(packageName, activityName)
                    if (launchedTaskId == -1 || !repository.isTaskOnDisplay(launchedTaskId, displayId)) {
                        failAttach("App launch did not attach $packageName to display $displayId")
                        return
                    }
                    finishAttach(displayId, onDisplayReady)
                }.onFailure {
                    failAttach("Failed to launch app", it)
                }
            }

            attachTimeoutJob = launch {
                delay(FreeformConstants.DELAY_ATTACH_TIMEOUT_MS)
                failAttach("Timed out waiting for $packageName to attach to freeform display")
            }

            val callback = object : FreeformRepository.FreeformCallback {
                override fun onDisplayAdded(displayId: Int) {
                    Log.i(TAG, "onDisplayAdded: displayId=$displayId, taskId=$taskId, isLandscape=$isLandscape")
                    stateManager.setDisplayId(displayId)
                    inputInjector.setDisplayId(displayId)

                    scope.launch {
                        if (taskId != -1) {
                            repository.moveRootTaskToDisplay(taskId, displayId)
                                .onSuccess {
                                    Log.i(TAG, "Moved task $taskId to display $displayId")
                                    delay(FreeformConstants.DELAY_SURFACE_SETTLE_MS)
                                    if (repository.isTaskOnDisplay(taskId, displayId)) {
                                        finishAttach(displayId, onDisplayReady)
                                    } else {
                                        failAttach("Moved task $taskId did not attach to display $displayId")
                                    }
                                }
                                .onFailure { e ->
                                    Log.e(TAG, "Failed to move task $taskId, falling back to launch", e)
                                    launchAppOnDisplay(displayId, onDisplayReady)
                                }
                        } else {
                            launchAppOnDisplay(displayId, onDisplayReady)
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
                failAttach("Failed to create display")
            } else {
                Log.d(TAG, "Display creation initiated...")
            }
        }
    }

    fun onBackPress() {
        val currentState = stateManager.state.value
        val displayId = currentState.displayId
        
        if (displayId < 0) {
            Log.w(TAG, "Cannot inject back press: invalid displayId $displayId")
            return
        }
        
        scope.launch {
            repository.injectBackKey(displayId)
                .onSuccess { Log.d(TAG, "Injected back button press to display $displayId") }
                .onFailure { Log.w(TAG, "Failed to inject back button press to display $displayId", it) }
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
        freeformWindowManager.removeListener(windowEventListener)
        freeformWindowManager.unregisterWindow(packageName)
    }

    fun register(window: FreeformWindowCompose) {
        freeformWindowManager.registerWindow(packageName, window)
    }
    
    fun launchFullscreen(onComplete: () -> Unit) {
        scope.launch {
            try {
                val intent = Intent(Intent.ACTION_MAIN).apply {
                    component = ComponentName(packageName, activityName)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                    addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
                }
                context.startActivity(intent)
                Log.i(TAG, "Launched $packageName/$activityName in fullscreen")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to launch activity in fullscreen", e)
            } finally {
                onComplete()
            }
        }
    }
}
