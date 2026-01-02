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
import android.util.Log
import android.view.TextureView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.scaleIn as composeScaleIn
import androidx.compose.animation.scaleOut as composeScaleOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.android.edge.bar.freeform.FreeformWindowManager
import com.android.edge.bar.freeform.presentation.components.*
import com.android.edge.bar.freeform.domain.FreeformConstants.CORNER_RADIUS_DP
import com.android.edge.bar.freeform.domain.FreeformConstants.MIN_WINDOW_WIDTH_DP
import com.android.edge.bar.freeform.domain.FreeformConstants.MIN_WINDOW_HEIGHT_DP
import com.android.edge.bar.freeform.domain.FreeformConstants.MAX_WINDOW_WIDTH_DP
import com.android.edge.bar.freeform.domain.FreeformConstants.MAX_WINDOW_HEIGHT_DP
import com.android.edge.bar.freeform.domain.FreeformConstants.BUBBLE_SIZE_DP
import com.android.edge.bar.freeform.domain.FreeformConstants.DEFAULT_WIDTH_DP
import com.android.edge.bar.freeform.domain.FreeformConstants.DEFAULT_HEIGHT_DP
import com.android.edge.bar.freeform.domain.FreeformConstants.DEFAULT_ASPECT_RATIO
import com.android.edge.bar.freeform.domain.FreeformConstants.RESIZE_HANDLE_SIZE_DP
import com.android.edge.bar.freeform.domain.FreeformConstants.RESIZE_HANDLE_SIZE_MIN_DP
import com.android.edge.bar.freeform.domain.FreeformConstants.RESIZE_HANDLE_SIZE_MAX_DP
import com.android.edge.bar.freeform.domain.FreeformConstants.RESIZE_HANDLE_BOTTOM_WIDTH_DP
import com.android.edge.bar.freeform.domain.FreeformConstants.RESIZE_HANDLE_BOTTOM_WIDTH_MIN_DP
import com.android.edge.bar.freeform.domain.FreeformConstants.RESIZE_HANDLE_BOTTOM_WIDTH_MAX_DP
import com.android.edge.bar.freeform.domain.FreeformConstants.RESIZE_HANDLE_BOTTOM_HEIGHT_DP
import com.android.edge.bar.freeform.domain.FreeformConstants.RESIZE_HANDLE_INSET_DP
import com.android.edge.bar.freeform.domain.FreeformConstants.RESIZE_HANDLE_BOTTOM_INSET_DP
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
    onBack: () -> Unit,
    onBringToFront: () -> Unit,
    onMaximizeFullscreen: () -> Unit,
    onUpdateWindowLayout: (Int, Int, Int, Int) -> Unit,
    onSetupTextureView: (TextureView) -> Unit,
    textureViewListener: TextureView.SurfaceTextureListener
) {
    val state by stateFlow.collectAsState()
    
    if (state.isDestroyed) {
        return
    }
    
    val context = LocalContext.current
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
            onBack = onBack,
            onMaximizeFullscreen = onMaximizeFullscreen,
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

    var dragX by remember { mutableFloatStateOf(0f) }
    var dragY by remember { mutableFloatStateOf(0f) }

    fun getBubbleCenterX() = dragX + bubbleSizePx / 2
    fun getBubbleCenterY() = dragY + bubbleSizePx / 2
    
    LaunchedEffect(dragX, dragY, isBubbleDragging) {
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
                dragX = state.x.toFloat()
                dragY = state.y.toFloat()
                freeformWindowManager.showRemoveZone()
            },
            onDrag = { deltaX, deltaY ->
                dragX += deltaX
                dragY += deltaY
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
    onBack: () -> Unit,
    onMaximizeFullscreen: () -> Unit,
    onBringToFront: () -> Unit,
    onSetupTextureView: (TextureView) -> Unit,
    textureViewListener: TextureView.SurfaceTextureListener
) {
    val density = LocalDensity.current
    val cornerRadius = CORNER_RADIUS_DP.dp
    
    var isResizing by remember { mutableStateOf(false) }
    var isDragging by remember { mutableStateOf(false) }
    var hideHandlesJob by remember { mutableStateOf<Job?>(null) }
    
    var textureViewRef by remember { mutableStateOf<TextureView?>(null) }
    var isContentLight by remember { mutableStateOf(false) }
    
    var isMenuExpanded by remember { mutableStateOf(false) }
    
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("freeform_prefs", Context.MODE_PRIVATE) }
    var showEducation by remember { 
        mutableStateOf(!prefs.getBoolean("education_shown", false))
    }
    var showResizeEducation by remember {
        mutableStateOf(!prefs.getBoolean("resize_education_shown", false))
    }
    
    val isHangupMode = state.mode == WindowMode.HANGUP
    val showResizeHints = (isResizing || isDragging) && !isHangupMode
    
    val luminanceDetector = remember { SurfaceLuminanceDetector() }
    
    LaunchedEffect(textureViewRef) {
        textureViewRef?.let { view ->
            while (isActive) {
                delay(500)
                val bitmap = luminanceDetector.captureBitmap(view)
                isContentLight = luminanceDetector.isContentLight(bitmap)
                bitmap?.recycle()
            }
        }
    }
    
    DisposableEffect(Unit) {
        onDispose {
            luminanceDetector.destroy()
        }
    }
    
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
            isContentLight = isContentLight,
            isMenuExpanded = isMenuExpanded,
            onMenuExpandedChange = { isMenuExpanded = it },
            showEducation = showEducation,
            onEducationDismissed = {
                showEducation = false
                prefs.edit().putBoolean("education_shown", true).apply()
            },
            scope = scope,
            onClose = onClose,
            onBack = onBack,
            onMaximizeFullscreen = onMaximizeFullscreen,
            onBringToFront = onBringToFront,
            onSetDragging = { isDragging = it },
            onSetupTextureView = { view ->
                textureViewRef = view
                onSetupTextureView(view)
            },
            textureViewListener = textureViewListener
        )
        
        if (!isHangupMode) {
            ResizeHandles(
                stateManager = stateManager,
                density = density,
                sizeFactor = sizeFactor,
                showResizeEducation = showResizeEducation,
                isContentLight = isContentLight,
                onResizeEducationDismissed = {
                    showResizeEducation = false
                    prefs.edit().putBoolean("resize_education_shown", true).apply()
                },
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
    isContentLight: Boolean,
    isMenuExpanded: Boolean,
    onMenuExpandedChange: (Boolean) -> Unit,
    showEducation: Boolean,
    onEducationDismissed: () -> Unit,
    scope: CoroutineScope,
    onClose: () -> Unit,
    onBack: () -> Unit,
    onMaximizeFullscreen: () -> Unit,
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
        Box(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(cornerRadius))
                    .then(
                        if (!isHangupMode && isMenuExpanded) {
                            Modifier.pointerInput(isMenuExpanded) {
                                detectTapGestures(
                                    onTap = { 
                                        onMenuExpandedChange(false)
                                    }
                                )
                            }
                        } else Modifier
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
            
            if (!isHangupMode) {
                OverlayTitleBar(
                    onClose = onClose,
                    onBack = onBack,
                    onMinimize = { stateManager.onMinimize() },
                    onHangup = { stateManager.onHangup() },
                    onMaximizeFullscreen = onMaximizeFullscreen,
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
                    isContentLight = isContentLight,
                    isMenuExpanded = isMenuExpanded,
                    onMenuExpandedChange = onMenuExpandedChange,
                    showEducation = showEducation,
                    onEducationDismissed = onEducationDismissed,
                    modifier = Modifier.align(Alignment.TopCenter)
                )
            }
        }
    }
}

@Composable
private fun BoxScope.ResizeHandles(
    stateManager: FreeformStateManager,
    density: androidx.compose.ui.unit.Density,
    sizeFactor: Float,
    showResizeEducation: Boolean,
    isContentLight: Boolean,
    onResizeEducationDismissed: () -> Unit,
    onResizeStarted: () -> Unit,
    onResizeEnded: () -> Unit
) {
    fun currentState() = stateManager.state.value
    
    val s = currentState()
    val baseMinWidthPx = with(density) { MIN_WINDOW_WIDTH_DP.dp.toPx().toInt() }
    val baseMinHeightPx = with(density) { MIN_WINDOW_HEIGHT_DP.dp.toPx().toInt() }

    val minWidthPx = if (s.isLandscape) baseMinHeightPx else baseMinWidthPx
    val minHeightPx = if (s.isLandscape) baseMinWidthPx else baseMinHeightPx
    val maxWidthPx = MAX_WINDOW_WIDTH_DP
    val maxHeightPx = MAX_WINDOW_HEIGHT_DP

    val handleWidth = (RESIZE_HANDLE_SIZE_DP.dp * sizeFactor)
        .coerceIn(RESIZE_HANDLE_SIZE_MIN_DP.dp, RESIZE_HANDLE_SIZE_MAX_DP.dp)
    
    val bottomHandleWidth = (RESIZE_HANDLE_BOTTOM_WIDTH_DP.dp * sizeFactor)
        .coerceIn(RESIZE_HANDLE_BOTTOM_WIDTH_MIN_DP.dp, RESIZE_HANDLE_BOTTOM_WIDTH_MAX_DP.dp)
    val bottomHandleHeight = RESIZE_HANDLE_BOTTOM_HEIGHT_DP.dp

    val handleOffset = (-RESIZE_HANDLE_INSET_DP).dp
    val bottomOffset = (-RESIZE_HANDLE_BOTTOM_INSET_DP).dp

    CornerResizeHandle(
        onResize = { deltaX, deltaY ->
            val s = currentState()
            val newWidth = (s.width + deltaX.roundToInt()).coerceIn(minWidthPx, maxWidthPx)
            val newHeight = (s.height + deltaY.roundToInt()).coerceIn(minHeightPx, maxHeightPx)
            
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
        handleWidth = handleWidth,
        modifier = Modifier
            .align(Alignment.BottomEnd)
            .offset(x = handleOffset, y = handleOffset)
    )
    
    CornerResizeHandle(
        onResize = { deltaX, deltaY ->
            val s = currentState()
            val newWidth = (s.width - deltaX.roundToInt()).coerceIn(minWidthPx, maxWidthPx)
            val newHeight = (s.height + deltaY.roundToInt()).coerceIn(minHeightPx, maxHeightPx)
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
        handleWidth = handleWidth,
        modifier = Modifier
            .align(Alignment.BottomStart)
            .offset(x = -handleOffset, y = handleOffset)
    )
    
    BottomResizeHandle(
        onResize = { deltaY ->
            val s = currentState()
            val newHeight = (s.height + deltaY.roundToInt()).coerceIn(minHeightPx, maxHeightPx)
            
            stateManager.onResize(s.width, newHeight)
        },
        onResizeStart = {
            onResizeStarted()
            stateManager.onResizeStart()
        },
        onResizeEnd = {
            onResizeEnded()
            stateManager.onResizeEnd()
        },
        handleWidth = bottomHandleWidth,
        handleHeight = bottomHandleHeight,
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .offset(y = bottomOffset)
    )
    
    val expandedBackgroundColor = if (isContentLight) {
        Color.Black.copy(alpha = 0.75f)
    } else {
        Color.White.copy(alpha = 0.9f)
    }
    
    val expandedContentColor = if (isContentLight) {
        Color.White
    } else {
        Color(0xFF1C1C1E)
    }
    
    AnimatedVisibility(
        visible = showResizeEducation,
        enter = fadeIn(animationSpec = tween(300)) + 
                expandVertically(expandFrom = Alignment.Bottom, animationSpec = tween(300)),
        exit = fadeOut(animationSpec = tween(200)) + 
               shrinkVertically(shrinkTowards = Alignment.Bottom, animationSpec = tween(200)),
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .padding(bottom = 28.dp)
    ) {
        LaunchedEffect(Unit) {
            delay(3000)
            onResizeEducationDismissed()
        }
        
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(expandedBackgroundColor)
                .clickable { onResizeEducationDismissed() }
                .padding(horizontal = 12.dp, vertical = 6.dp)
        ) {
            Text(
                text = "Drag corners or bottom edge to resize",
                color = expandedContentColor,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}
