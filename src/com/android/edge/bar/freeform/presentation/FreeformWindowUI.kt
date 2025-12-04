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

import android.graphics.Bitmap
import android.util.Log
import android.view.TextureView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn as composeScaleIn
import androidx.compose.animation.scaleOut as composeScaleOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.android.edge.bar.freeform.FreeformWindowManager
import com.android.edge.bar.freeform.presentation.components.*
import com.android.edge.bar.freeform.domain.FreeformConstants.CORNER_RADIUS_DP
import com.android.edge.bar.freeform.domain.FreeformConstants.TITLE_BAR_HEIGHT_DP
import com.android.edge.bar.freeform.domain.FreeformConstants.TITLE_BAR_HEIGHT_MIN_DP
import com.android.edge.bar.freeform.domain.FreeformConstants.TITLE_BAR_HEIGHT_MAX_DP
import com.android.edge.bar.freeform.domain.FreeformConstants.TITLE_BAR_ICON_SIZE_DP
import com.android.edge.bar.freeform.domain.FreeformConstants.TITLE_BAR_ICON_SIZE_MIN_DP
import com.android.edge.bar.freeform.domain.FreeformConstants.TITLE_BAR_ICON_SIZE_MAX_DP
import com.android.edge.bar.freeform.domain.FreeformConstants.TITLE_BAR_DRAG_HANDLE_HEIGHT_DP
import com.android.edge.bar.freeform.domain.FreeformConstants.TITLE_BAR_DRAG_HANDLE_HEIGHT_MIN_DP
import com.android.edge.bar.freeform.domain.FreeformConstants.TITLE_BAR_DRAG_HANDLE_HEIGHT_MAX_DP
import com.android.edge.bar.freeform.domain.FreeformConstants.MIN_WINDOW_WIDTH_DP
import com.android.edge.bar.freeform.domain.FreeformConstants.MIN_WINDOW_HEIGHT_DP
import com.android.edge.bar.freeform.domain.FreeformConstants.MAX_WINDOW_WIDTH_DP
import com.android.edge.bar.freeform.domain.FreeformConstants.MAX_WINDOW_HEIGHT_DP
import com.android.edge.bar.freeform.domain.FreeformConstants.BUBBLE_SIZE_DP
import com.android.edge.bar.freeform.domain.FreeformConstants.DEFAULT_WIDTH_DP
import com.android.edge.bar.freeform.domain.FreeformConstants.DEFAULT_HEIGHT_DP
import com.android.edge.bar.freeform.domain.FreeformConstants.DEFAULT_ASPECT_RATIO
import com.android.edge.bar.freeform.domain.FreeformConstants.RESIZE_HANDLE_CORNER_RADIUS_DP
import com.android.edge.bar.freeform.domain.FreeformConstants.RESIZE_HANDLE_SIZE_DP
import com.android.edge.bar.freeform.domain.FreeformConstants.RESIZE_HANDLE_SIZE_MIN_DP
import com.android.edge.bar.freeform.domain.FreeformConstants.RESIZE_HANDLE_SIZE_MAX_DP
import com.android.edge.bar.freeform.domain.FreeformConstants.RESIZE_HANDLE_CANVAS_SIZE_DP
import com.android.edge.bar.freeform.domain.FreeformConstants.RESIZE_HANDLE_CANVAS_MIN_DP
import com.android.edge.bar.freeform.domain.FreeformConstants.RESIZE_HANDLE_CANVAS_MAX_DP
import com.android.edge.bar.freeform.domain.FreeformConstants.RESIZE_HANDLE_ARM_LENGTH_DP
import com.android.edge.bar.freeform.domain.FreeformConstants.RESIZE_HANDLE_ARM_MIN_DP
import com.android.edge.bar.freeform.domain.FreeformConstants.RESIZE_HANDLE_ARM_MAX_DP
import com.android.edge.bar.freeform.domain.FreeformConstants.RESIZE_HANDLE_STROKE_DP
import com.android.edge.bar.freeform.domain.FreeformConstants.RESIZE_HANDLE_STROKE_MIN_DP
import com.android.edge.bar.freeform.domain.FreeformConstants.RESIZE_HANDLE_STROKE_MAX_DP
import com.android.edge.bar.freeform.domain.FreeformConstants.RESIZE_HANDLE_BOTTOM_WIDTH_DP
import com.android.edge.bar.freeform.domain.FreeformConstants.RESIZE_HANDLE_BOTTOM_WIDTH_MIN_DP
import com.android.edge.bar.freeform.domain.FreeformConstants.RESIZE_HANDLE_BOTTOM_WIDTH_MAX_DP
import com.android.edge.bar.freeform.domain.FreeformConstants.RESIZE_HANDLE_BOTTOM_HEIGHT_DP
import com.android.edge.bar.freeform.domain.FreeformConstants.RESIZE_HANDLE_BOTTOM_INDICATOR_DP
import com.android.edge.bar.freeform.domain.FreeformConstants.RESIZE_HANDLE_BOTTOM_INDICATOR_MIN_DP
import com.android.edge.bar.freeform.domain.FreeformConstants.RESIZE_HANDLE_BOTTOM_INDICATOR_MAX_DP
import com.android.edge.bar.freeform.domain.FreeformConstants.RESIZE_HANDLE_BOTTOM_INDICATOR_HEIGHT_DP
import com.android.axion.kotlin.math.dpToPx
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlin.math.roundToInt

@Composable
fun FreeformWindowContent(
    stateManager: FreeformStateManager,
    stateFlow: StateFlow<WindowState>,
    freeformWindowManager: FreeformWindowManager,
    density: Float,
    scope: CoroutineScope,
    onCloseAndKill: () -> Unit,
    onBringToFront: () -> Unit,
    onUpdateWindowLayout: (Int, Int, Int, Int) -> Unit,
    onSetupTextureView: (TextureView) -> Unit,
    textureViewListener: TextureView.SurfaceTextureListener
) {
    val state by stateFlow.collectAsState()
    
    if (state.isDestroyed) {
        return
    }
    
    val context = androidx.compose.ui.platform.LocalContext.current
    val defaultWidthPx = context.dpToPx(DEFAULT_WIDTH_DP)
    val sizeFactor = (state.width.toFloat() / defaultWidthPx.toFloat()).coerceIn(0.7f, 1.3f)
    val isBubbleMode = state.mode == WindowMode.BUBBLE

    AnimatedVisibility(
        visible = !isBubbleMode,
        enter = fadeIn(),
        exit = fadeOut()
    ) {
        FullWindowView(
            state = state,
            stateManager = stateManager,
            sizeFactor = sizeFactor,
            scope = scope,
            onClose = onCloseAndKill,
            onBringToFront = onBringToFront,
            onSetupTextureView = onSetupTextureView,
            textureViewListener = textureViewListener
        )
    }
    
    BubbleModeView(
        state = state,
        stateManager = stateManager,
        freeformWindowManager = freeformWindowManager,
        density = density,
        visible = isBubbleMode,
        onCloseAndKill = onCloseAndKill
    )
    
    LaunchedEffect(state.x, state.y, state.width, state.height) {
        onUpdateWindowLayout(state.x, state.y, state.width, state.height)
    }
}

@Composable
private fun BubbleModeView(
    state: WindowState,
    stateManager: FreeformStateManager,
    freeformWindowManager: FreeformWindowManager,
    density: Float,
    visible: Boolean,
    onCloseAndKill: () -> Unit
) {
    var isBubbleDragging by remember { mutableStateOf(false) }
    var isInRemoveZone by remember { mutableStateOf(false) }
    val bubbleSizePx = BUBBLE_SIZE_DP * density

    var currentBubbleX by remember { mutableStateOf(state.x.toFloat()) }
    var currentBubbleY by remember { mutableStateOf(state.y.toFloat()) }
    
    LaunchedEffect(state.x, state.y) {
        if (!isBubbleDragging) {
            currentBubbleX = state.x.toFloat()
            currentBubbleY = state.y.toFloat()
        }
    }
    
    fun getBubbleCenterX() = currentBubbleX + bubbleSizePx / 2
    fun getBubbleCenterY() = currentBubbleY + bubbleSizePx / 2
    
    LaunchedEffect(currentBubbleX, currentBubbleY, isBubbleDragging) {
        if (isBubbleDragging) {
            val inZone = freeformWindowManager.isInRemoveZone(getBubbleCenterX(), getBubbleCenterY())
            isInRemoveZone = inZone
            freeformWindowManager.setRemoveZoneHovering(inZone)
        } else {
            isInRemoveZone = false
        }
    }
    
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn() + composeScaleIn(),
        exit = fadeOut() + composeScaleOut()
    ) {
        BubbleView(
            appIcon = state.appIcon,
            onClick = { stateManager.onBubbleTap() },
            isHovered = isInRemoveZone,
            onDragStart = {
                isBubbleDragging = true
                currentBubbleX = state.x.toFloat()
                currentBubbleY = state.y.toFloat()
                freeformWindowManager.showRemoveZone()
            },
            onDrag = { deltaX, deltaY ->
                currentBubbleX += deltaX
                currentBubbleY += deltaY
                stateManager.onDrag(deltaX, deltaY)
            },
            onDragEnd = {
                val finalCenterX = getBubbleCenterX()
                val finalCenterY = getBubbleCenterY()
                val wasInRemoveZone = freeformWindowManager.isInRemoveZone(finalCenterX, finalCenterY)
                
                Log.d("BubbleModeView", "onDragEnd: x=$finalCenterX, y=$finalCenterY, inRemoveZone=$wasInRemoveZone")
                
                isBubbleDragging = false
                freeformWindowManager.hideRemoveZone()
                
                if (wasInRemoveZone) {
                    Log.d("BubbleModeView", "Calling onCloseAndKill")
                    onCloseAndKill()
                } else {
                    stateManager.onDragEnd()
                }
            }
        )
    }
}

@Composable
private fun FullWindowView(
    state: WindowState,
    stateManager: FreeformStateManager,
    sizeFactor: Float,
    scope: CoroutineScope,
    onClose: () -> Unit,
    onBringToFront: () -> Unit,
    onSetupTextureView: (TextureView) -> Unit,
    textureViewListener: TextureView.SurfaceTextureListener
) {
    val density = androidx.compose.ui.platform.LocalDensity.current
    val cornerRadius = CORNER_RADIUS_DP.dp
    val titleBarHeight = (TITLE_BAR_HEIGHT_DP.dp * sizeFactor)
        .coerceIn(TITLE_BAR_HEIGHT_MIN_DP.dp, TITLE_BAR_HEIGHT_MAX_DP.dp)
    val iconSize = (TITLE_BAR_ICON_SIZE_DP.dp * sizeFactor)
        .coerceIn(TITLE_BAR_ICON_SIZE_MIN_DP.dp, TITLE_BAR_ICON_SIZE_MAX_DP.dp)
    val dragHandleHeight = (TITLE_BAR_DRAG_HANDLE_HEIGHT_DP.dp * sizeFactor)
        .coerceIn(TITLE_BAR_DRAG_HANDLE_HEIGHT_MIN_DP.dp, TITLE_BAR_DRAG_HANDLE_HEIGHT_MAX_DP.dp)
    
    var isResizing by remember { mutableStateOf(false) }
    var isDragging by remember { mutableStateOf(false) }
    var hideHandlesJob by remember { mutableStateOf<Job?>(null) }
    
    val isHangupMode = state.mode == WindowMode.HANGUP
    val showResizeHints = (isResizing || isDragging) && !isHangupMode
    
    fun onResizeStarted() {
        hideHandlesJob?.cancel()
        hideHandlesJob = null
        isResizing = true
    }
    
    fun onResizeEnded() {
        hideHandlesJob?.cancel()
        hideHandlesJob = scope.launch {
            delay(1500)
            isResizing = false
        }
    }
    
    Box(modifier = Modifier.fillMaxSize()) {
        WindowSurface(
            state = state,
            stateManager = stateManager,
            isHangupMode = isHangupMode,
            cornerRadius = cornerRadius,
            titleBarHeight = titleBarHeight,
            iconSize = iconSize,
            dragHandleHeight = dragHandleHeight,
            scope = scope,
            onClose = onClose,
            onBringToFront = onBringToFront,
            onSetDragging = { isDragging = it },
            onSetupTextureView = onSetupTextureView,
            textureViewListener = textureViewListener
        )
        
        if (!isHangupMode) {
            ResizeHandles(
                stateManager = stateManager,
                density = density,
                cornerRadius = cornerRadius,
                sizeFactor = sizeFactor,
                showResizeHints = showResizeHints,
                onResizeStarted = { onResizeStarted() },
                onResizeEnded = { onResizeEnded() }
            )
        }
    }
}

@Composable
private fun BoxScope.WindowSurface(
    state: WindowState,
    stateManager: FreeformStateManager,
    isHangupMode: Boolean,
    cornerRadius: Dp,
    titleBarHeight: Dp,
    iconSize: Dp,
    dragHandleHeight: Dp,
    scope: CoroutineScope,
    onClose: () -> Unit,
    onBringToFront: () -> Unit,
    onSetDragging: (Boolean) -> Unit,
    onSetupTextureView: (TextureView) -> Unit,
    textureViewListener: TextureView.SurfaceTextureListener
) {
    Surface(
        modifier = Modifier
            .fillMaxSize()
            .padding(start = 16.dp, end = 16.dp, bottom = 20.dp)
            .then(
                if (isHangupMode) {
                    Modifier.pointerInput(Unit) {
                        detectTapGestures(
                            onTap = { 
                                stateManager.onHangupTap() 
                            }
                        )
                    }
                } else Modifier
            ),
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(cornerRadius)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            if (!isHangupMode) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    shape = RoundedCornerShape(topStart = cornerRadius, topEnd = cornerRadius)
                ) {
                    TitleBar(
                        onClose = onClose,
                        onMinimize = { stateManager.onMinimize() },
                        onDrag = { deltaX, deltaY ->
                            scope.launch(Dispatchers.Main) {
                                stateManager.onDrag(deltaX, deltaY)
                            }
                        },
                        onDragStart = {
                            onBringToFront()
                            onSetDragging(true)
                        },
                        onDragEnd = {
                            stateManager.onDragEnd()
                            onSetDragging(false)
                        },
                        titleBarHeight = titleBarHeight,
                        iconSize = iconSize,
                        dragHandleHeight = dragHandleHeight
                    )
                }
            }
            
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .clip(
                        if (isHangupMode) RoundedCornerShape(cornerRadius)
                        else RoundedCornerShape(bottomStart = cornerRadius, bottomEnd = cornerRadius)
                    )
            ) {
                AndroidView(
                    factory = { ctx ->
                        TextureView(ctx).also {
                            it.isOpaque = false
                            it.surfaceTextureListener = textureViewListener
                            onSetupTextureView(it)
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )

                if (isHangupMode) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .pointerInput(Unit) {
                                awaitPointerEventScope {
                                    while (true) {
                                        awaitPointerEvent()
                                    }
                                }
                            }
                    )
                }

                val showVeil = state.isPaused || !state.isSurfaceReady || state.isAppLaunching
                androidx.compose.animation.AnimatedVisibility(
                    visible = showVeil,
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.surface
                    ) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            state.appIcon?.let { icon ->
                                Image(
                                    bitmap = icon.asImageBitmap(),
                                    contentDescription = null,
                                    modifier = Modifier
                                        .size(48.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BoxScope.ResizeHandles(
    stateManager: FreeformStateManager,
    density: androidx.compose.ui.unit.Density,
    cornerRadius: Dp,
    sizeFactor: Float,
    showResizeHints: Boolean,
    onResizeStarted: () -> Unit,
    onResizeEnded: () -> Unit
) {
    fun currentState() = stateManager.state.value
    
    val minWidthPx = with(density) { MIN_WINDOW_WIDTH_DP.dp.toPx().toInt() }
    val minHeightPx = with(density) { MIN_WINDOW_HEIGHT_DP.dp.toPx().toInt() }
    val maxWidthPx = MAX_WINDOW_WIDTH_DP
    val maxHeightPx = MAX_WINDOW_HEIGHT_DP

    val handleWidth = (RESIZE_HANDLE_SIZE_DP.dp * sizeFactor)
        .coerceIn(RESIZE_HANDLE_SIZE_MIN_DP.dp, RESIZE_HANDLE_SIZE_MAX_DP.dp)
    val canvasSize = (RESIZE_HANDLE_CANVAS_SIZE_DP.dp * sizeFactor)
        .coerceIn(RESIZE_HANDLE_CANVAS_MIN_DP.dp, RESIZE_HANDLE_CANVAS_MAX_DP.dp)
    val armLength = (RESIZE_HANDLE_ARM_LENGTH_DP.dp * sizeFactor)
        .coerceIn(RESIZE_HANDLE_ARM_MIN_DP.dp, RESIZE_HANDLE_ARM_MAX_DP.dp)
    val strokeWidth = (RESIZE_HANDLE_STROKE_DP.dp * sizeFactor)
        .coerceIn(RESIZE_HANDLE_STROKE_MIN_DP.dp, RESIZE_HANDLE_STROKE_MAX_DP.dp)
    val handleCornerRadius = RESIZE_HANDLE_CORNER_RADIUS_DP.dp
    
    val bottomHandleWidth = (RESIZE_HANDLE_BOTTOM_WIDTH_DP.dp * sizeFactor)
        .coerceIn(RESIZE_HANDLE_BOTTOM_WIDTH_MIN_DP.dp, RESIZE_HANDLE_BOTTOM_WIDTH_MAX_DP.dp)
    val bottomHandleHeight = RESIZE_HANDLE_BOTTOM_HEIGHT_DP.dp
    val bottomIndicatorWidth = (RESIZE_HANDLE_BOTTOM_INDICATOR_DP.dp * sizeFactor)
        .coerceIn(RESIZE_HANDLE_BOTTOM_INDICATOR_MIN_DP.dp, RESIZE_HANDLE_BOTTOM_INDICATOR_MAX_DP.dp)
    val bottomIndicatorHeight = RESIZE_HANDLE_BOTTOM_INDICATOR_HEIGHT_DP.dp

    CornerResizeHandle(
        onResize = { deltaX, deltaY ->
            val s = currentState()
            val newWidth: Int
            val newHeight: Int
            if (kotlin.math.abs(deltaX) > kotlin.math.abs(deltaY)) {
                newWidth = (s.width + deltaX.roundToInt()).coerceIn(minWidthPx, maxWidthPx)
                newHeight = (newWidth / DEFAULT_ASPECT_RATIO).roundToInt().coerceIn(minHeightPx, maxHeightPx)
            } else {
                newHeight = (s.height + deltaY.roundToInt()).coerceIn(minHeightPx, maxHeightPx)
                newWidth = (newHeight * DEFAULT_ASPECT_RATIO).roundToInt().coerceIn(minWidthPx, maxWidthPx)
            }
            
            stateManager.onResize(newWidth, newHeight)
        },
        onResizeStart = {
            onResizeStarted()
            stateManager.onResizeStart()
        },
        onResizeEnd = {
            onResizeEnded()
            stateManager.onResizeEnd()
        },
        showIndicator = showResizeHints,
        isLeftCorner = false,
        handleWidth = handleWidth,
        canvasSize = canvasSize,
        armLength = armLength,
        strokeWidth = strokeWidth,
        cornerRadius = handleCornerRadius,
        modifier = Modifier.align(Alignment.BottomEnd)
    )
    
    CornerResizeHandle(
        onResize = { deltaX, deltaY ->
            val s = currentState()
            val newWidth: Int
            val newHeight: Int
            if (kotlin.math.abs(deltaX) > kotlin.math.abs(deltaY)) {
                newWidth = (s.width - deltaX.roundToInt()).coerceIn(minWidthPx, maxWidthPx)
                newHeight = (newWidth / DEFAULT_ASPECT_RATIO).roundToInt().coerceIn(minHeightPx, maxHeightPx)
            } else {
                newHeight = (s.height + deltaY.roundToInt()).coerceIn(minHeightPx, maxHeightPx)
                newWidth = (newHeight * DEFAULT_ASPECT_RATIO).roundToInt().coerceIn(minWidthPx, maxWidthPx)
            }
            val deltaWidth = s.width - newWidth
            
            stateManager.onResize(newWidth, newHeight)
            stateManager.onDrag(deltaWidth.toFloat(), 0f)
        },
        onResizeStart = {
            onResizeStarted()
            stateManager.onResizeStart()
        },
        onResizeEnd = {
            onResizeEnded()
            stateManager.onResizeEnd()
        },
        showIndicator = showResizeHints,
        isLeftCorner = true,
        handleWidth = handleWidth,
        canvasSize = canvasSize,
        armLength = armLength,
        strokeWidth = strokeWidth,
        cornerRadius = handleCornerRadius,
        modifier = Modifier.align(Alignment.BottomStart)
    )
    
    BottomResizeHandle(
        onResize = { deltaY ->
            val s = currentState()
            val newHeight = (s.height + deltaY.roundToInt()).coerceIn(minHeightPx, maxHeightPx)
            val newWidth = (newHeight * DEFAULT_ASPECT_RATIO).roundToInt().coerceIn(minWidthPx, maxWidthPx)
            
            stateManager.onResize(newWidth, newHeight)
        },
        onResizeStart = {
            onResizeStarted()
            stateManager.onResizeStart()
        },
        onResizeEnd = {
            onResizeEnded()
            stateManager.onResizeEnd()
        },
        showIndicator = true,
        handleWidth = bottomHandleWidth,
        handleHeight = bottomHandleHeight,
        indicatorWidth = bottomIndicatorWidth,
        indicatorHeight = bottomIndicatorHeight,
        modifier = Modifier.align(Alignment.BottomCenter)
    )
}
