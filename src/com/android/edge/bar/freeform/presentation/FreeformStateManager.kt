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
import android.graphics.Bitmap
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.Log
import android.view.WindowManager
import com.android.edge.bar.freeform.FreeformWindowManager
import com.android.edge.bar.freeform.WindowSnapping
import com.android.edge.bar.freeform.data.FreeformRepository
import com.android.edge.bar.freeform.data.FreeformRepositoryImpl
import com.android.edge.bar.freeform.domain.FreeformConstants
import com.android.axion.kotlin.math.dpToPx
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

sealed class WindowEvent {
    data class DisplayCreated(val displayId: Int) : WindowEvent()
    object DisplayPaused : WindowEvent()
    object DisplayResumed : WindowEvent()
    object DisplayStopped : WindowEvent()
    
    object SurfaceSettled : WindowEvent()
    object AppLaunchComplete : WindowEvent()

    data class Drag(val deltaX: Float, val deltaY: Float) : WindowEvent()
    object DragEnd : WindowEvent()

    object Minimize : WindowEvent()
    object Hangup : WindowEvent()
    object BubbleTap : WindowEvent()
    object HangupTap : WindowEvent()

    object ResizeStart : WindowEvent()
    data class Resize(val newWidth: Int, val newHeight: Int) : WindowEvent()
    object ResizeEnd : WindowEvent()

    object Maximize : WindowEvent()

    object ResizeToFullscreen : WindowEvent()
    object ResizeToHalfLeft : WindowEvent()
    object ResizeToHalfRight : WindowEvent()

    data class IconLoaded(val icon: Bitmap) : WindowEvent()

    data class OrientationChanged(val isLandscape: Boolean) : WindowEvent()
}

sealed class FreeformEffect {
    data class ResizeDisplay(val width: Int, val height: Int) : FreeformEffect()
}

enum class WindowMode {
    NORMAL,
    BUBBLE,
    HANGUP,
    DESKTOP
}

data class WindowState(
    val x: Float = 0f,
    val y: Float = 0f,
    val width: Int,
    val height: Int,
    
    val mode: WindowMode = WindowMode.NORMAL,
    
    val displayId: Int = -1,
    val isPaused: Boolean = false,
    
    val isSurfaceReady: Boolean = false,
    val isAppLaunching: Boolean = true,
    val isDestroyed: Boolean = false,
    
    val savedX: Float = 0f,
    val savedY: Float = 0f,
    val savedWidth: Int,
    val savedHeight: Int,
    val savedBubbleX: Float = -1f,
    val savedBubbleY: Float = -1f,
    
    val appIcon: Bitmap? = null,
    val isResizing: Boolean = false,
    
    val snapPosition: WindowSnapping.SnapPosition? = null,
    
    val isLandscape: Boolean = false
)

class FreeformStateManager(
    private val context: Context,
    private val repository: FreeformRepository,
    private val scope: CoroutineScope,
    private val windowPackageName: String,
    private val freeformWindowManager: FreeformWindowManager,
    initialWidth: Int,
    initialHeight: Int,
    private val hangupWidthDp: Int = FreeformConstants.HANGUP_WIDTH,
    private val hangupHeightDp: Int = FreeformConstants.HANGUP_HEIGHT,
    private var screenWidth: Int,
    private var screenHeight: Int,
    private val densityDpi: Float,
    initialX: Float = 0f,
    initialY: Float = 0f,
    private val initialMode: WindowMode = WindowMode.NORMAL
) {
    companion object {
        private const val TAG = "FreeformStateManager"
    }

    private val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator

    private val _effect = MutableSharedFlow<FreeformEffect>()
    val effect: SharedFlow<FreeformEffect> = _effect.asSharedFlow()

    private val surfaceEventsManager = SurfaceEventsManager(scope)

    private val eventFlow = MutableSharedFlow<WindowEvent>(extraBufferCapacity = 64)

    private val _state = MutableStateFlow(WindowState(
        x = initialX,
        y = initialY,
        width = initialWidth,
        height = initialHeight,
        mode = initialMode,
        savedWidth = initialWidth,
        savedHeight = initialHeight
    ))
    val state: StateFlow<WindowState> = _state.asStateFlow()
    
    private val currentState: WindowState get() = _state.value
    
    val packageName: String get() = windowPackageName
    
    private fun getDisplayDimensions(): Pair<Int, Int> {
        val displayWidth = currentState.width
        val displayHeight = currentState.height
        return displayWidth to displayHeight
    }
    
    init {
        scope.launch {
            eventFlow.collect { event ->
                processEvent(event)
            }
        }
        
        scope.launch {
            surfaceEventsManager.veilState.collect { veilState ->
                updateState { 
                    copy(isSurfaceReady = veilState is VeilState.Hidden) 
                }
            }
        }
        
        loadAppInfo()
    }
    
    fun dispatch(event: WindowEvent) {
        scope.launch {
            eventFlow.emit(event)
        }
    }
    
    private fun updateState(transform: WindowState.() -> WindowState) {
        _state.value = currentState.transform()
    }
    
    private suspend fun processEvent(event: WindowEvent) {
        Log.d(TAG, "Processing event: $event")
        
        when (event) {
            is WindowEvent.DisplayCreated -> handleDisplayCreated(event.displayId)
            is WindowEvent.DisplayPaused -> handleDisplayPaused()
            is WindowEvent.DisplayResumed -> handleDisplayResumed()
            is WindowEvent.DisplayStopped -> handleDisplayStopped()
            is WindowEvent.SurfaceSettled -> handleSurfaceSettled()
            is WindowEvent.AppLaunchComplete -> handleAppLaunchComplete()
            
            is WindowEvent.Drag -> handleDrag(event.deltaX, event.deltaY)
            is WindowEvent.DragEnd -> handleDragEnd()
            
            is WindowEvent.Minimize -> handleMinimize()
            is WindowEvent.Hangup -> enterHangupMode()
            is WindowEvent.BubbleTap -> handleBubbleExpand()
            is WindowEvent.HangupTap -> handleHangupTap()
            
            is WindowEvent.ResizeStart -> handleResizeStart()
            is WindowEvent.Resize -> handleResize(event.newWidth, event.newHeight)
            is WindowEvent.ResizeEnd -> handleResizeEnd()
            is WindowEvent.Maximize -> handleMaximize()

            is WindowEvent.ResizeToFullscreen -> handleResizeToFullscreen()
            is WindowEvent.ResizeToHalfLeft -> handleResizeToHalf(isLeft = true)
            is WindowEvent.ResizeToHalfRight -> handleResizeToHalf(isLeft = false)

            is WindowEvent.IconLoaded -> updateState { copy(appIcon = event.icon) }
            
            is WindowEvent.OrientationChanged -> handleOrientationChanged(event.isLandscape)
        }
    }
    
    private fun handleDisplayCreated(displayId: Int) {
        surfaceEventsManager.dispatch(SurfaceEvent.ShowVeilRequested(VeilReason.DISPLAY_CREATED))
        surfaceEventsManager.dispatch(SurfaceEvent.OperationStarted("display_init"))
        updateState { copy(displayId = displayId) }
        freeformWindowManager.registerDisplayId(displayId, windowPackageName)
    }
    
    private suspend fun handleOrientationChanged(isLandscape: Boolean) {
        if (currentState.isLandscape == isLandscape) return
        
        Log.d(TAG, "Orientation changed to landscape=$isLandscape (mode=${currentState.mode})")
        
        updateState { copy(isLandscape = isLandscape) }

        when (currentState.mode) {
            WindowMode.BUBBLE -> snapBubbleToSafeZone()
            WindowMode.HANGUP -> enterHangupMode()
            WindowMode.NORMAL, WindowMode.DESKTOP -> { /* Handled below */ }
        }

        if (currentState.mode != WindowMode.NORMAL && currentState.mode != WindowMode.DESKTOP) return
        
        Log.d(TAG, "Handling window resize for orientation change")
        
        surfaceEventsManager.dispatch(SurfaceEvent.ShowVeilRequested(VeilReason.RESIZE_STARTED))
        surfaceEventsManager.dispatch(SurfaceEvent.OperationStarted("orientation_change"))
        
        val displayId = currentState.displayId
        if (displayId >= 0) {
            repository.pauseDisplay(displayId)
        }
        
        val newWidth = currentState.height
        val newHeight = currentState.width
        
        updateState {
            copy(
                width = newWidth,
                height = newHeight,
                isLandscape = isLandscape,
                isPaused = true
            )
        }
        
        if (displayId >= 0) {
            repository.resumeDisplay(displayId)
        }
        
        val (displayWidth, displayHeight) = getDisplayDimensions()
        _effect.emit(FreeformEffect.ResizeDisplay(displayWidth, displayHeight))
        
        delay(FreeformConstants.DELAY_SURFACE_SETTLE_MS)
        surfaceEventsManager.dispatch(SurfaceEvent.OperationCompleted("orientation_change"))
        dispatch(WindowEvent.SurfaceSettled)
    }
    
    private fun handleDisplayPaused() {
        surfaceEventsManager.dispatch(SurfaceEvent.ShowVeilRequested(VeilReason.DISPLAY_PAUSED))
        surfaceEventsManager.dispatch(SurfaceEvent.OperationStarted("display_paused"))
        updateState { copy(isPaused = true) }
    }
    
    private fun handleDisplayResumed() {
        updateState { copy(isPaused = false) }
    }
    
    private fun handleDisplayStopped() {
        Log.d(TAG, "Display stopped")
        surfaceEventsManager.dispatch(SurfaceEvent.ShowVeilRequested(VeilReason.DISPLAY_STOPPED))
    }

    private suspend fun handleSurfaceSettled() {
        Log.d(TAG, "Surface settled - triggering resize to ensure proper rendering")
        updateState { copy(isPaused = false) }
        val (displayWidth, displayHeight) = getDisplayDimensions()
        _effect.emit(FreeformEffect.ResizeDisplay(displayWidth, displayHeight))
        delay(FreeformConstants.DELAY_SURFACE_SETTLE_MS)
        surfaceEventsManager.dispatch(SurfaceEvent.SurfaceReady)
        surfaceEventsManager.dispatch(SurfaceEvent.OperationCompleted("*"))
        surfaceEventsManager.dispatch(SurfaceEvent.HideVeilRequested)
    }
    
    private fun handleAppLaunchComplete() {
        Log.d(TAG, "App launch complete - hiding launch veil")
        updateState { copy(isAppLaunching = false) }
    }
    
    private fun handleDrag(deltaX: Float, deltaY: Float) {
        updateState {
            copy(
                x = x + deltaX,
                y = y + deltaY,
                snapPosition = null
            )
        }

        if (currentState.mode == WindowMode.NORMAL) {
            val threshold = currentState.width / 4
            val windowRight = currentState.x + currentState.width
            val isOffRightEdge = windowRight > screenWidth + threshold
            val isOffLeftEdge = currentState.x < -threshold

            if ((isOffRightEdge || isOffLeftEdge) && !currentState.isResizing) {
                scope.launch { enterHangupMode() }
            }
        }
    }

    private fun handleDragEnd() {
        when (currentState.mode) {
            WindowMode.BUBBLE -> snapBubbleToSafeZone()
            WindowMode.HANGUP -> { /* Already in hangup, nothing to do */ }
            WindowMode.NORMAL, WindowMode.DESKTOP -> clampWindowToSafeZone()
        }
    }
    
    private suspend fun handleMinimize() {
        if (currentState.mode == WindowMode.DESKTOP) {
            Log.d(TAG, "Desktop mode window - minimize ignored (no bubble mode)")
            return
        }

        val slot = freeformWindowManager.getNextBubbleSlot(windowPackageName)
        val bubbleSizePx = context.dpToPx(FreeformConstants.BUBBLE_SIZE_DP)
        val margin = context.dpToPx(16)
        val slotSpacing = context.dpToPx(8)

        val bubbleX: Float
        val bubbleY: Float

        if (currentState.savedBubbleX >= 0f && currentState.savedBubbleY >= 0f) {
            bubbleX = currentState.savedBubbleX
            bubbleY = currentState.savedBubbleY
        } else {
            bubbleX = (screenWidth - bubbleSizePx - margin).toFloat()
            bubbleY = (margin + (slot * (bubbleSizePx + slotSpacing))).toFloat()
        }

        updateState {
            copy(
                mode = WindowMode.BUBBLE,
                savedX = x,
                savedY = y,
                savedWidth = width,
                savedHeight = height,
                x = bubbleX,
                y = bubbleY,
                width = bubbleSizePx,
                height = bubbleSizePx
            )
        }

        val displayId = currentState.displayId
        if (displayId >= 0) {
            repository.pauseDisplay(displayId)
        }
    }
    
    private suspend fun handleBubbleExpand() {
        freeformWindowManager.releaseBubbleSlot(windowPackageName)

        val targetWidth = currentState.savedWidth
        val targetHeight = currentState.savedHeight
        val targetX = currentState.savedX
        val targetY = currentState.savedY

        val safeTop = context.dpToPx(FreeformConstants.SAFE_ZONE_TOP_DP)
        val safeBottom = context.dpToPx(FreeformConstants.SAFE_ZONE_BOTTOM_DP)
        val minY = safeTop.toFloat()
        val maxY = (screenHeight - safeBottom - 48).toFloat()
        
        val clampedY = targetY.coerceIn(minY, maxY)
        val halfWidth = targetWidth / 2f
        val clampedX = targetX.coerceIn(-halfWidth, (screenWidth - halfWidth).toFloat())

        updateState {
            copy(
                mode = WindowMode.NORMAL,
                x = clampedX,
                y = clampedY,
                width = targetWidth,
                height = targetHeight,
                savedBubbleX = x,
                savedBubbleY = y,
                isPaused = true
            )
        }

        val displayId = currentState.displayId
        if (displayId >= 0) {
            surfaceEventsManager.dispatch(SurfaceEvent.ShowVeilRequested(VeilReason.BUBBLE_EXPAND))
            surfaceEventsManager.dispatch(SurfaceEvent.OperationStarted("bubble_expand"))
            
            val result = repository.resumeDisplay(displayId)
            result.onSuccess {
                val (displayWidth, displayHeight) = getDisplayDimensions()
                _effect.emit(FreeformEffect.ResizeDisplay(displayWidth, displayHeight))
                delay(FreeformConstants.DELAY_SURFACE_SETTLE_MS)
                dispatch(WindowEvent.SurfaceSettled)
                surfaceEventsManager.dispatch(SurfaceEvent.OperationCompleted("bubble_expand"))
            }.onFailure {
                Log.e(TAG, "Failed to resume display", it)
                surfaceEventsManager.dispatch(SurfaceEvent.OperationCompleted("bubble_expand"))
                dispatch(WindowEvent.SurfaceSettled)
            }
        } else {
            dispatch(WindowEvent.SurfaceSettled)
        }
    }

    private suspend fun handleHangupTap() {
        if (currentState.mode == WindowMode.HANGUP) {
            val targetWidth = currentState.savedWidth
            val targetHeight = currentState.savedHeight
            val safeTop = context.dpToPx(FreeformConstants.SAFE_ZONE_TOP_DP)
            val safeBottom = context.dpToPx(FreeformConstants.SAFE_ZONE_BOTTOM_DP)
            val minY = safeTop.toFloat()
            val maxY = (screenHeight - safeBottom - 48).toFloat()
            
            val safeX = if (currentState.savedX < -targetWidth / 2 || currentState.savedX > screenWidth - targetWidth / 2) {
                ((screenWidth - targetWidth) / 2).toFloat()
            } else {
                currentState.savedX
            }
            val safeY = currentState.savedY.coerceIn(minY, maxY)

            updateState {
                copy(
                    mode = WindowMode.NORMAL,
                    x = safeX,
                    y = safeY,
                    width = targetWidth,
                    height = targetHeight,
                    isPaused = false
                )
            }
            
            surfaceEventsManager.dispatch(SurfaceEvent.ShowVeilRequested(VeilReason.HANGUP_EXPAND))
            surfaceEventsManager.dispatch(SurfaceEvent.OperationStarted("hangup_expand"))
            performHapticFeedback()
            val (displayWidth, displayHeight) = getDisplayDimensions()
            _effect.emit(FreeformEffect.ResizeDisplay(displayWidth, displayHeight))
            delay(FreeformConstants.DELAY_SURFACE_SETTLE_MS)
            updateState { copy(isPaused = false) }
            handleSurfaceSettled()
            surfaceEventsManager.dispatch(SurfaceEvent.OperationCompleted("hangup_expand"))
        }
    }
    
    private suspend fun enterHangupMode() {
        surfaceEventsManager.dispatch(SurfaceEvent.ShowVeilRequested(VeilReason.HANGUP_EXPAND))
        surfaceEventsManager.dispatch(SurfaceEvent.OperationStarted("enter_hangup"))
        
        val hangupWidthPx: Int
        val hangupHeightPx: Int
        if (currentState.isLandscape) {
            hangupWidthPx = context.dpToPx(hangupHeightDp)
            hangupHeightPx = context.dpToPx(hangupWidthDp)
        } else {
            hangupWidthPx = context.dpToPx(hangupWidthDp)
            hangupHeightPx = context.dpToPx(hangupHeightDp)
        }
        
        val centeredX = ((screenWidth - currentState.savedWidth) / 2).toFloat()
        val centeredY = ((screenHeight - currentState.savedHeight) / 2).toFloat()
        
        updateState {
            copy(
                mode = WindowMode.HANGUP,
                savedX = centeredX,
                savedY = centeredY,
                x = (screenWidth - hangupWidthPx - 16).toFloat(),
                y = 16f,
                width = hangupWidthPx,
                height = hangupHeightPx
            )
        }
        performHapticFeedback()
        val (displayWidth, displayHeight) = getDisplayDimensions()
        _effect.emit(FreeformEffect.ResizeDisplay(displayWidth, displayHeight))
        delay(FreeformConstants.DELAY_SURFACE_SETTLE_MS)
        surfaceEventsManager.dispatch(SurfaceEvent.SurfaceReady)
        surfaceEventsManager.dispatch(SurfaceEvent.OperationCompleted("enter_hangup"))
        surfaceEventsManager.dispatch(SurfaceEvent.HideVeilRequested)
    }
    
    private suspend fun handleResizeStart() {
        updateState { copy(isResizing = true) }
        surfaceEventsManager.dispatch(SurfaceEvent.ShowVeilRequested(VeilReason.RESIZE_STARTED))
        surfaceEventsManager.dispatch(SurfaceEvent.OperationStarted("resize"))
        
        val displayId = currentState.displayId
        if (displayId >= 0) {
            repository.pauseDisplay(displayId)
        }

        updateState { copy(isPaused = true) }
    }
    
    private fun handleResize(newWidth: Int, newHeight: Int) {
        if (currentState.mode == WindowMode.HANGUP) return

        var width: Int
        var height: Int

        if (currentState.mode == WindowMode.DESKTOP) {
            val minWidth = context.dpToPx(FreeformConstants.DESKTOP_MIN_WIDTH_DP)
            val minHeight = context.dpToPx(FreeformConstants.DESKTOP_MIN_HEIGHT_DP)
            val taskbarHeight = context.dpToPx(FreeformConstants.DESKTOP_TASKBAR_HEIGHT_DP)

            val maxWidth = screenWidth
            val maxHeight = screenHeight - taskbarHeight

            width = newWidth.coerceIn(minWidth, maxWidth)
            height = newHeight.coerceIn(minHeight, maxHeight)
        } else {
            val baseMinWidth = context.dpToPx(FreeformConstants.HANGUP_WIDTH)
            val baseMinHeight = context.dpToPx(FreeformConstants.HANGUP_HEIGHT)
            val minDimension = Math.min(baseMinWidth, baseMinHeight)
            width = newWidth.coerceAtLeast(minDimension)
            height = newHeight.coerceAtLeast(minDimension)
        }

        updateState { copy(width = width, height = height) }
    }

    private suspend fun handleResizeEnd() {
        updateState { 
            copy(
                isResizing = false,
                savedWidth = width,
                savedHeight = height
            ) 
        }

        val displayId = currentState.displayId

        if (displayId >= 0) {
            repository.resumeDisplay(displayId)
        }

        val (displayWidth, displayHeight) = getDisplayDimensions()
        _effect.emit(FreeformEffect.ResizeDisplay(displayWidth, displayHeight))

        delay(FreeformConstants.DELAY_SURFACE_SETTLE_MS)
        surfaceEventsManager.dispatch(SurfaceEvent.OperationCompleted("resize"))
        dispatch(WindowEvent.SurfaceSettled)
    }
    
    private suspend fun handleMaximize() {
        if (currentState.mode != WindowMode.NORMAL) return
        
        surfaceEventsManager.dispatch(SurfaceEvent.ShowVeilRequested(VeilReason.RESIZE_STARTED))
        surfaceEventsManager.dispatch(SurfaceEvent.OperationStarted("maximize"))
        
        val displayId = currentState.displayId
        if (displayId >= 0) {
            repository.pauseDisplay(displayId)
        }
        
        val padding = context.dpToPx(32)
        val maxWidth = screenWidth - (padding * 2)
        val maxHeight = screenHeight - (padding * 2)
        
        val targetWidth: Int
        val targetHeight: Int
        val currentAspectRatio = currentState.width.toFloat() / currentState.height.toFloat()
        
        if (maxWidth / currentAspectRatio <= maxHeight) {
            targetWidth = maxWidth
            targetHeight = (maxWidth / currentAspectRatio).toInt()
        } else {
            targetHeight = maxHeight
            targetWidth = (maxHeight * currentAspectRatio).toInt()
        }
        
        val targetX = ((screenWidth - targetWidth) / 2).toFloat()
        val targetY = ((screenHeight - targetHeight) / 2).toFloat()
        
        updateState {
            copy(
                x = targetX,
                y = targetY,
                width = targetWidth,
                height = targetHeight,
                isPaused = true
            )
        }
        
        if (displayId >= 0) {
            repository.resumeDisplay(displayId)
        }
        
        val (displayWidth, displayHeight) = getDisplayDimensions()
        _effect.emit(FreeformEffect.ResizeDisplay(displayWidth, displayHeight))
        
        delay(FreeformConstants.DELAY_SURFACE_SETTLE_MS)
        surfaceEventsManager.dispatch(SurfaceEvent.OperationCompleted("maximize"))
        dispatch(WindowEvent.SurfaceSettled)
    }

    private suspend fun handleResizeToFullscreen() {
        if (currentState.mode != WindowMode.DESKTOP) return

        surfaceEventsManager.dispatch(SurfaceEvent.ShowVeilRequested(VeilReason.RESIZE_STARTED))
        surfaceEventsManager.dispatch(SurfaceEvent.OperationStarted("resize_fullscreen"))

        val displayId = currentState.displayId
        if (displayId >= 0) {
            repository.pauseDisplay(displayId)
        }

        val taskbarHeight = context.dpToPx(FreeformConstants.DESKTOP_TASKBAR_HEIGHT_DP)
        val statusBarHeight = context.dpToPx(FreeformConstants.DESKTOP_STATUS_BAR_HEIGHT_DP)
        val targetWidth = screenWidth
        val targetHeight = screenHeight - taskbarHeight - statusBarHeight
        val targetX = 0f
        val targetY = statusBarHeight.toFloat()

        updateState {
            copy(
                x = targetX,
                y = targetY,
                width = targetWidth,
                height = targetHeight,
                isPaused = true
            )
        }

        if (displayId >= 0) {
            repository.resumeDisplay(displayId)
        }

        val (displayWidth, displayHeight) = getDisplayDimensions()
        _effect.emit(FreeformEffect.ResizeDisplay(displayWidth, displayHeight))

        delay(FreeformConstants.DELAY_SURFACE_SETTLE_MS)
        surfaceEventsManager.dispatch(SurfaceEvent.OperationCompleted("resize_fullscreen"))
        dispatch(WindowEvent.SurfaceSettled)
    }

    private suspend fun handleResizeToHalf(isLeft: Boolean) {
        if (currentState.mode != WindowMode.DESKTOP) return

        surfaceEventsManager.dispatch(SurfaceEvent.ShowVeilRequested(VeilReason.RESIZE_STARTED))
        surfaceEventsManager.dispatch(SurfaceEvent.OperationStarted("resize_half"))

        val displayId = currentState.displayId
        if (displayId >= 0) {
            repository.pauseDisplay(displayId)
        }

        val taskbarHeight = context.dpToPx(FreeformConstants.DESKTOP_TASKBAR_HEIGHT_DP)
        val statusBarHeight = context.dpToPx(FreeformConstants.DESKTOP_STATUS_BAR_HEIGHT_DP)
        val targetWidth = screenWidth / 2
        val targetHeight = screenHeight - taskbarHeight - statusBarHeight
        val targetX = if (isLeft) 0f else (screenWidth / 2).toFloat()
        val targetY = statusBarHeight.toFloat()

        updateState {
            copy(
                x = targetX,
                y = targetY,
                width = targetWidth,
                height = targetHeight,
                isPaused = true
            )
        }

        if (displayId >= 0) {
            repository.resumeDisplay(displayId)
        }

        val (displayWidth, displayHeight) = getDisplayDimensions()
        _effect.emit(FreeformEffect.ResizeDisplay(displayWidth, displayHeight))

        delay(FreeformConstants.DELAY_SURFACE_SETTLE_MS)
        surfaceEventsManager.dispatch(SurfaceEvent.OperationCompleted("resize_half"))
        dispatch(WindowEvent.SurfaceSettled)
    }

    private fun snapBubbleToSafeZone() {
        val bubbleSize = currentState.width
        val isLandscape = currentState.isLandscape

        val safeTop = if (isLandscape) {
            context.dpToPx(16)
        } else {
            context.dpToPx(FreeformConstants.SAFE_ZONE_TOP_DP)
        }
        
        val safeBottom = if (isLandscape) {
            context.dpToPx(16)
        } else {
            context.dpToPx(FreeformConstants.SAFE_ZONE_BOTTOM_DP)
        }
        
        val safeEdge = context.dpToPx(FreeformConstants.SAFE_ZONE_EDGE_DP)
        
        val minY = safeTop.toFloat()
        val maxY = (screenHeight - safeBottom - bubbleSize).toFloat()
        val clampedY = currentState.y.coerceIn(minY, maxY)
        
        val centerX = currentState.x + bubbleSize / 2f
        val screenCenter = screenWidth / 2f
        val snappedX = if (centerX < screenCenter) {
            safeEdge.toFloat()
        } else {
            (screenWidth - bubbleSize - safeEdge).toFloat()
        }
        
        updateState { copy(x = snappedX, y = clampedY) }
    }
    
    private fun clampWindowToSafeZone() {
        val safeTop = context.dpToPx(FreeformConstants.SAFE_ZONE_TOP_DP)
        val safeBottom = context.dpToPx(FreeformConstants.SAFE_ZONE_BOTTOM_DP)
        
        val minY = safeTop.toFloat()
        val maxY = (screenHeight - safeBottom - 48).toFloat()
        val clampedY = currentState.y.coerceIn(minY, maxY)
        
        val halfWidth = currentState.width / 2f
        val clampedX = currentState.x.coerceIn(-halfWidth, screenWidth - halfWidth)
        
        updateState { copy(x = clampedX, y = clampedY) }
    }
    
    private fun performHapticFeedback() {
        vibrator.vibrate(VibrationEffect.createOneShot(30, VibrationEffect.DEFAULT_AMPLITUDE))
    }
    
    private fun loadAppInfo() {
        scope.launch {
            repository.getAppIcon(windowPackageName)
                .onSuccess { dispatch(WindowEvent.IconLoaded(it)) }
        }
    }
    
    fun loadAppIcon() {
        loadAppInfo()
    }
    
    fun onDrag(deltaX: Float, deltaY: Float) {
        if (!eventFlow.tryEmit(WindowEvent.Drag(deltaX, deltaY))) {
            dispatch(WindowEvent.Drag(deltaX, deltaY))
        }
    }
    
    fun onDragEnd() {
        dispatch(WindowEvent.DragEnd)
    }
    
    fun onMinimize() {
        dispatch(WindowEvent.Minimize)
    }
    
    fun onHangup() {
        dispatch(WindowEvent.Hangup)
    }
    
    fun onBubbleTap() {
        dispatch(WindowEvent.BubbleTap)
    }
    
    fun onHangupTap() {
        dispatch(WindowEvent.HangupTap)
    }
    
    fun onResizeStart() {
        dispatch(WindowEvent.ResizeStart)
    }
    
    fun onResize(width: Int, height: Int) {
        dispatch(WindowEvent.Resize(width, height))
    }
    
    fun onResizeEnd() {
        dispatch(WindowEvent.ResizeEnd)
    }
    
    fun onMaximize() {
        dispatch(WindowEvent.Maximize)
    }

    fun onResizeToFullscreen() {
        dispatch(WindowEvent.ResizeToFullscreen)
    }

    fun onResizeToHalfLeft() {
        dispatch(WindowEvent.ResizeToHalfLeft)
    }

    fun onResizeToHalfRight() {
        dispatch(WindowEvent.ResizeToHalfRight)
    }

    fun setDisplayId(displayId: Int) {
        dispatch(WindowEvent.DisplayCreated(displayId))
    }
    
    fun onOrientationChanged(isLandscape: Boolean) {
        dispatch(WindowEvent.OrientationChanged(isLandscape))
    }
    
    fun onBubblePositionUpdate(newY: Float) {
        updateState { copy(y = newY) }
    }

    fun onScreenDimensionsChanged(width: Int, height: Int) {
        this.screenWidth = width
        this.screenHeight = height
        Log.d(TAG, "Screen dimensions updated in $windowPackageName: ${width}x${height}")
        scope.launch {
            when (currentState.mode) {
                WindowMode.BUBBLE -> snapBubbleToSafeZone()
                WindowMode.HANGUP -> enterHangupMode()
                WindowMode.NORMAL, WindowMode.DESKTOP -> clampWindowToSafeZone()
            }
        }
    }
    
    fun isHangupMode(): Boolean = currentState.mode == WindowMode.HANGUP
    
    fun isSurfaceReady(): Boolean = currentState.isSurfaceReady
    
    fun markSurfaceReady() {
        dispatch(WindowEvent.SurfaceSettled)
    }
    
    fun destroy() {
        val displayId = currentState.displayId
        if (displayId >= 0) {
            freeformWindowManager.unregisterDisplayId(displayId)
        }
        updateState { copy(isDestroyed = true) }
    }
}
