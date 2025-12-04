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
    object BubbleTap : WindowEvent()
    object HangupTap : WindowEvent()

    object ResizeStart : WindowEvent()
    data class Resize(val newWidth: Int, val newHeight: Int) : WindowEvent()
    object ResizeEnd : WindowEvent()
    
    data class IconLoaded(val icon: Bitmap) : WindowEvent()
}

sealed class FreeformEffect {
    data class ResizeDisplay(val width: Int, val height: Int) : FreeformEffect()
}

enum class WindowMode {
    NORMAL,
    BUBBLE,
    HANGUP
}

data class WindowState(
    val x: Int = 0,
    val y: Int = 0,
    val width: Int,
    val height: Int,
    
    val mode: WindowMode = WindowMode.NORMAL,
    
    val displayId: Int = -1,
    val isPaused: Boolean = false,
    
    val isSurfaceReady: Boolean = false,
    val isAppLaunching: Boolean = true,
    val isDestroyed: Boolean = false,
    
    val savedX: Int = 0,
    val savedY: Int = 0,
    val savedWidth: Int,
    val savedHeight: Int,
    val savedBubbleX: Int = -1,
    val savedBubbleY: Int = -1,
    
    val appIcon: Bitmap? = null,
    val isResizing: Boolean = false,
    
    val snapPosition: WindowSnapping.SnapPosition? = null
)

class FreeformStateManager(
    private val context: Context,
    private val repository: FreeformRepository,
    private val scope: CoroutineScope,
    private val packageName: String,
    private val freeformWindowManager: FreeformWindowManager,
    initialWidth: Int,
    initialHeight: Int,
    private val hangupWidthDp: Int = FreeformConstants.HANGUP_WIDTH,
    private val hangupHeightDp: Int = FreeformConstants.HANGUP_HEIGHT,
    private val screenWidth: Int,
    private val screenHeight: Int,
    private val densityDpi: Float,
    initialX: Int = 0,
    initialY: Int = 0
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
        savedWidth = initialWidth,
        savedHeight = initialHeight
    ))
    val state: StateFlow<WindowState> = _state.asStateFlow()
    
    private val currentState: WindowState get() = _state.value
    
    private fun getDisplayDimensions(): Pair<Int, Int> {
        val displayWidth = currentState.width
        val displayHeight = when (currentState.mode) {
            WindowMode.HANGUP -> currentState.height
            else -> currentState.height - context.dpToPx(FreeformConstants.TITLE_BAR_HEIGHT_DP)
        }
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
            is WindowEvent.BubbleTap -> handleBubbleExpand()
            is WindowEvent.HangupTap -> handleHangupTap()
            
            is WindowEvent.ResizeStart -> handleResizeStart()
            is WindowEvent.Resize -> handleResize(event.newWidth, event.newHeight)
            is WindowEvent.ResizeEnd -> handleResizeEnd()
            
            is WindowEvent.IconLoaded -> updateState { copy(appIcon = event.icon) }
        }
    }
    
    private fun handleDisplayCreated(displayId: Int) {
        surfaceEventsManager.dispatch(SurfaceEvent.ShowVeilRequested(VeilReason.DISPLAY_CREATED))
        surfaceEventsManager.dispatch(SurfaceEvent.OperationStarted("display_init"))
        updateState { copy(displayId = displayId) }
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
                x = x + deltaX.toInt(),
                y = y + deltaY.toInt(),
                snapPosition = null
            )
        }

        if (currentState.mode == WindowMode.NORMAL) {
            val threshold = currentState.width / 4
            val windowRight = currentState.x + currentState.width
            val isOffRightEdge = windowRight > screenWidth + threshold
            val isOffLeftEdge = currentState.x < -threshold
            
            if (isOffRightEdge || isOffLeftEdge) {
                scope.launch { enterHangupMode() }
            }
        }
    }
    
    private fun handleDragEnd() {
        when (currentState.mode) {
            WindowMode.BUBBLE -> snapBubbleToSafeZone()
            WindowMode.HANGUP -> { /* Already in hangup, nothing to do */ }
            WindowMode.NORMAL -> clampWindowToSafeZone()
        }
    }
    
    private suspend fun handleMinimize() {
        val slot = freeformWindowManager.getNextBubbleSlot(packageName)
        val bubbleSizePx = context.dpToPx(FreeformConstants.BUBBLE_SIZE_DP)
        val margin = context.dpToPx(16)
        val slotSpacing = context.dpToPx(8)
        
        val bubbleX: Int
        val bubbleY: Int
        
        if (currentState.savedBubbleX >= 0 && currentState.savedBubbleY >= 0) {
            bubbleX = currentState.savedBubbleX
            bubbleY = currentState.savedBubbleY
        } else {
            bubbleX = screenWidth - bubbleSizePx - margin
            bubbleY = margin + (slot * (bubbleSizePx + slotSpacing))
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
        freeformWindowManager.releaseBubbleSlot(packageName)

        val targetWidth = currentState.savedWidth
        val targetHeight = currentState.savedHeight
        val targetX = currentState.savedX
        val targetY = currentState.savedY

        updateState {
            copy(
                mode = WindowMode.NORMAL,
                x = targetX,
                y = targetY,
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
            
            val safeX = if (currentState.savedX < 0 || currentState.savedX > screenWidth - targetWidth) {
                (screenWidth - targetWidth) / 2
            } else {
                currentState.savedX
            }
            val safeY = if (currentState.savedY < 50 || currentState.savedY > screenHeight - 100) {
                (screenHeight - targetHeight) / 2
            } else {
                currentState.savedY
            }

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
        
        val hangupWidthPx = context.dpToPx(hangupWidthDp)
        val hangupHeightPx = context.dpToPx(hangupHeightDp)
        
        val centeredX = (screenWidth - currentState.savedWidth) / 2
        val centeredY = (screenHeight - currentState.savedHeight) / 2
        
        updateState {
            copy(
                mode = WindowMode.HANGUP,
                savedX = centeredX,
                savedY = centeredY,
                x = screenWidth - hangupWidthPx - 16,
                y = 16,
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

        var width = newWidth
        var height = newHeight
        
        val minWidthPx = context.dpToPx(FreeformConstants.MIN_WINDOW_WIDTH_DP)
        val minHeightPx = context.dpToPx(FreeformConstants.MIN_WINDOW_HEIGHT_DP)

        if (newWidth == minWidthPx && newHeight == minHeightPx) {
            return   
        }

        if (newWidth < minWidthPx || newHeight < minHeightPx) {
            width = minWidthPx
            height = minHeightPx
        }
        
        updateState { copy(width = width, height = height) }
    }

    private suspend fun handleResizeEnd() {
        updateState { copy(isResizing = false) }

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
    
    private fun snapBubbleToSafeZone() {
        val bubbleSize = currentState.width
        val safeTop = context.dpToPx(FreeformConstants.SAFE_ZONE_TOP_DP)
        val safeBottom = context.dpToPx(FreeformConstants.SAFE_ZONE_BOTTOM_DP)
        val safeEdge = context.dpToPx(FreeformConstants.SAFE_ZONE_EDGE_DP)
        
        val minY = safeTop
        val maxY = screenHeight - safeBottom - bubbleSize
        val clampedY = currentState.y.coerceIn(minY, maxY)
        
        val centerX = currentState.x + bubbleSize / 2
        val screenCenter = screenWidth / 2
        val snappedX = if (centerX < screenCenter) {
            safeEdge
        } else {
            screenWidth - bubbleSize - safeEdge
        }
        
        updateState { copy(x = snappedX, y = clampedY) }
    }
    
    private fun clampWindowToSafeZone() {
        val safeTop = context.dpToPx(FreeformConstants.SAFE_ZONE_TOP_DP)
        val safeBottom = context.dpToPx(FreeformConstants.SAFE_ZONE_BOTTOM_DP)
        
        val minY = safeTop
        val maxY = screenHeight - safeBottom - 48
        val clampedY = currentState.y.coerceIn(minY, maxY)
        
        val halfWidth = currentState.width / 2
        val clampedX = currentState.x.coerceIn(-halfWidth, screenWidth - halfWidth)
        
        updateState { copy(x = clampedX, y = clampedY) }
    }
    
    private fun performHapticFeedback() {
        vibrator.vibrate(VibrationEffect.createOneShot(30, VibrationEffect.DEFAULT_AMPLITUDE))
    }
    
    fun loadAppIcon() {
        scope.launch {
            repository.getAppIcon(packageName)
                .onSuccess { dispatch(WindowEvent.IconLoaded(it)) }
        }
    }
    
    fun onDrag(deltaX: Float, deltaY: Float) {
        dispatch(WindowEvent.Drag(deltaX, deltaY))
    }
    
    fun onDragEnd() {
        dispatch(WindowEvent.DragEnd)
    }
    
    fun onMinimize() {
        dispatch(WindowEvent.Minimize)
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
    
    fun setDisplayId(displayId: Int) {
        dispatch(WindowEvent.DisplayCreated(displayId))
    }
    
    fun isHangupMode(): Boolean = currentState.mode == WindowMode.HANGUP
    
    fun isSurfaceReady(): Boolean = currentState.isSurfaceReady
    
    fun markSurfaceReady() {
        dispatch(WindowEvent.SurfaceSettled)
    }
    
    fun destroy() {
        updateState { copy(isDestroyed = true) }
    }
}
