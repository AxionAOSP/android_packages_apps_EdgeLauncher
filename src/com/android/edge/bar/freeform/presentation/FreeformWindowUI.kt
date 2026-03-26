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
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.*
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.android.edge.bar.freeform.InputInjector
import com.android.edge.bar.freeform.FreeformWindowManager
import com.android.edge.bar.freeform.presentation.components.*
import com.android.edge.bar.freeform.domain.FreeformConstants.CORNER_RADIUS_DP
import com.android.edge.bar.freeform.domain.FreeformConstants.MIN_WINDOW_WIDTH_DP
import com.android.edge.bar.freeform.domain.FreeformConstants.MIN_WINDOW_HEIGHT_DP
import com.android.edge.bar.freeform.domain.FreeformConstants.MAX_WINDOW_WIDTH_DP
import com.android.edge.bar.freeform.domain.FreeformConstants.MAX_WINDOW_HEIGHT_DP
import com.android.edge.bar.freeform.domain.FreeformConstants.BUBBLE_SIZE_DP
import com.android.edge.bar.freeform.domain.FreeformConstants.DEFAULT_WIDTH_DP
import com.android.edge.bar.freeform.domain.FreeformConstants.RESIZE_HANDLE_SIZE_DP
import com.android.edge.bar.freeform.domain.FreeformConstants.RESIZE_HANDLE_SIZE_MIN_DP
import com.android.edge.bar.freeform.domain.FreeformConstants.RESIZE_HANDLE_SIZE_MAX_DP
import com.android.edge.bar.freeform.domain.FreeformConstants.RESIZE_HANDLE_BOTTOM_WIDTH_DP
import com.android.edge.bar.freeform.domain.FreeformConstants.RESIZE_HANDLE_BOTTOM_WIDTH_MIN_DP
import com.android.edge.bar.freeform.domain.FreeformConstants.RESIZE_HANDLE_BOTTOM_WIDTH_MAX_DP
import com.android.edge.bar.freeform.domain.FreeformConstants.RESIZE_HANDLE_BOTTOM_HEIGHT_DP
import com.android.edge.bar.freeform.domain.FreeformConstants.DESKTOP_MIN_WIDTH_DP
import com.android.edge.bar.freeform.domain.FreeformConstants.DESKTOP_MIN_HEIGHT_DP
import com.android.edge.bar.freeform.domain.FreeformConstants.DESKTOP_CORNER_RADIUS_DP
import com.android.edge.bar.freeform.domain.FreeformConstants.DESKTOP_TASKBAR_HEIGHT_DP
import com.android.edge.bar.freeform.domain.FreeformConstants.DESKTOP_TITLE_BAR_HEIGHT_DP
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
    onUpdateWindowLayout: (Float, Float, Int, Int) -> Unit,
    onSetupTextureView: (TextureView) -> Unit,
    textureViewListener: TextureView.SurfaceTextureListener,
    inputInjector: InputInjector
) {
    val state by stateFlow.collectAsState()
    
    if (state.isDestroyed) {
        return
    }
    
    val context = LocalContext.current
    val defaultWidthPx = context.dpToPx(DEFAULT_WIDTH_DP)
    val sizeFactor = (state.width.toFloat() / defaultWidthPx.toFloat()).coerceIn(0.7f, 1.3f)
    val isBubbleMode = state.mode == WindowMode.BUBBLE

    fun onSetDragging(dragging: Boolean) {
    }

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
            onSetDragging = ::onSetDragging,
            onSetupTextureView = onSetupTextureView,
            textureViewListener = textureViewListener,
            inputInjector = inputInjector
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

    fun getBubbleCenterX() = state.x.toFloat() + bubbleSizePx / 2
    fun getBubbleCenterY() = state.y.toFloat() + bubbleSizePx / 2
    
    fun getBubbleSlotFromPosition(y: Float): Int {
        val margin = 16 * density
        val slotSpacing = 8 * density
        return ((y - margin) / (bubbleSizePx + slotSpacing)).toInt().coerceAtLeast(0)
    }
    
    LaunchedEffect(state.y, isBubbleDragging) {
        if (isBubbleDragging) {
            val currentSlot = freeformWindowManager.getBubbleSlot(stateManager.packageName) ?: 0
            val targetSlot = getBubbleSlotFromPosition(getBubbleCenterY())
            
            if (targetSlot != currentSlot && targetSlot >= 0) {
                freeformWindowManager.reorderBubbleSlot(stateManager.packageName, targetSlot)
            }
        }
    }
    
    LaunchedEffect(state.x, state.y, isBubbleDragging) {
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
                freeformWindowManager.showRemoveZone()
            },
            onDrag = { deltaX, deltaY ->
                stateManager.onDrag(deltaX, deltaY)
            },
            onDragEnd = {
                val finalCenterX = getBubbleCenterX()
                val finalCenterY = getBubbleCenterY()
                val wasInRemoveZone = freeformWindowManager.isInRemoveZone(finalCenterX, finalCenterY)
                
                Log.d("BubbleModeView", "onDragEnd: x=$finalCenterX, y=$finalCenterY, inRemoveZone=$wasInRemoveZone")
                
                isBubbleDragging = false
                freeformWindowManager.hideRemoveZone()
                
                if (isInRemoveZone) {
                    Log.d("BubbleModeView", "Calling onCloseAndKill (dismissed in zone)")
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
    onSetDragging: (Boolean) -> Unit,
    onSetupTextureView: (TextureView) -> Unit,
    textureViewListener: TextureView.SurfaceTextureListener,
    inputInjector: InputInjector
) {
    val density = LocalDensity.current
    val isDesktopMode = state.mode == WindowMode.DESKTOP
    val cornerRadius = if (isDesktopMode) DESKTOP_CORNER_RADIUS_DP.dp else CORNER_RADIUS_DP.dp

    var isResizing by remember { mutableStateOf(false) }
    var isDragging by remember { mutableStateOf(false) }
    var hideHandlesJob by remember { mutableStateOf<Job?>(null) }
    
    var textureViewRef by remember { mutableStateOf<TextureView?>(null) }
    var isContentLight by remember { mutableStateOf(false) }
    
    val context = LocalContext.current
    
    val showResizeBorder = isResizing || isDragging
    
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
    
    val colors = rememberLuminanceColors(isContentLight)
    
    val prefs = remember { context.getSharedPreferences("edge_launcher", Context.MODE_PRIVATE) }
    var showEducation by remember { mutableStateOf(!prefs.getBoolean("education_shown", false)) }
    
    val onEducationDismissed = {
        showEducation = false
        prefs.edit().putBoolean("education_shown", true).apply()
    }
    
    LaunchedEffect(showEducation) {
        if (showEducation) {
            delay(5000)
            onEducationDismissed()
        }
    }
    
    val resizeBorderColor = Color(0xFF87CEEB)

    var isDesktopMenuExpanded by remember { mutableStateOf(false) }
    var dropdownOffsetX by remember { mutableFloatStateOf(0f) }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            if (isDesktopMode) {
                DesktopTitleBar(
                    onClose = onClose,
                    onMinimize = { stateManager.onMinimize() },
                    onBack = onBack,
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
                    isMenuExpanded = isDesktopMenuExpanded,
                    onMenuExpandedChange = { isDesktopMenuExpanded = it },
                    appIcon = state.appIcon,
                    onDropdownOffsetChanged = { dropdownOffsetX = it },
                    modifier = Modifier.padding(horizontal = 24.dp)
                )
            } else {
                TitleBar(
                    onClose = onClose,
                    onMinimize = { stateManager.onMinimize() },
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
                    scaleFactor = sizeFactor,
                    modifier = Modifier.padding(horizontal = 24.dp)
                )
            }

            Box(modifier = Modifier.weight(1f)) {
                WindowSurface(
                    state = state,
                    stateManager = stateManager,
                    cornerRadius = cornerRadius,
                    scope = scope,
                    onSetupTextureView = { view ->
                        textureViewRef = view
                        onSetupTextureView(view)
                    },
                    textureViewListener = textureViewListener,
                    inputInjector = inputInjector
                )

                ResizeHandles(
                    stateManager = stateManager,
                    density = density,
                    sizeFactor = sizeFactor,
                    onResizeStarted = { onResizeStarted() },
                    onResizeEnded = { onResizeEnded() },
                    isDesktopMode = isDesktopMode
                )
            }
        }

        AnimatedVisibility(
            visible = showResizeBorder,
            enter = fadeIn(animationSpec = tween(150)),
            exit = fadeOut(animationSpec = tween(300))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(start = 24.dp, end = 24.dp, bottom = 24.dp)
                    .border(
                        width = 2.dp,
                        color = resizeBorderColor,
                        shape = RoundedCornerShape(cornerRadius)
                    )
            )
        }

        if (isDesktopMode) {
            AnimatedVisibility(
                visible = isDesktopMenuExpanded,
                enter = fadeIn(animationSpec = tween(150)) + expandVertically(
                    animationSpec = tween(200),
                    expandFrom = Alignment.Top
                ),
                exit = fadeOut(animationSpec = tween(150)) + shrinkVertically(
                    animationSpec = tween(200),
                    shrinkTowards = Alignment.Top
                ),
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(
                        start = 24.dp + with(density) { dropdownOffsetX.toDp() },
                        top = DESKTOP_TITLE_BAR_HEIGHT_DP.dp
                    )
            ) {
                DesktopMenuDropdown(
                    onResizeToFullscreen = {
                        stateManager.onResizeToFullscreen()
                        isDesktopMenuExpanded = false
                    },
                    onResizeToHalfLeft = {
                        stateManager.onResizeToHalfLeft()
                        isDesktopMenuExpanded = false
                    },
                    onResizeToHalfRight = {
                        stateManager.onResizeToHalfRight()
                        isDesktopMenuExpanded = false
                    },
                    onMaximizeFullscreen = {
                        onMaximizeFullscreen()
                        isDesktopMenuExpanded = false
                    },
                    isContentLight = isContentLight
                )
            }
        }
    }
}

@Composable
private fun BoxScope.WindowSurface(
    state: WindowState,
    stateManager: FreeformStateManager,
    cornerRadius: Dp,
    scope: CoroutineScope,
    onSetupTextureView: (TextureView) -> Unit,
    textureViewListener: TextureView.SurfaceTextureListener,
    inputInjector: InputInjector
) {
    Surface(
        modifier = Modifier
            .fillMaxSize()
            .padding(start = 24.dp, end = 24.dp, bottom = 24.dp),
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(
            bottomStart = cornerRadius,
            bottomEnd = cornerRadius
        )
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(
                        RoundedCornerShape(
                            bottomStart = cornerRadius,
                            bottomEnd = cornerRadius
                        )
                    )
                    .pointerInput(Unit) {
                        awaitPointerEventScope {
                            while (true) {
                                val event = awaitPointerEvent(PointerEventPass.Initial)
                                if (event.type == PointerEventType.Scroll) {
                                    event.changes.forEach { change ->
                                        val scrollDelta = change.scrollDelta
                                        if (scrollDelta.x != 0f || scrollDelta.y != 0f) {
                                            inputInjector.injectScrollEvent(
                                                change.position.x,
                                                change.position.y,
                                                scrollDelta.x,
                                                scrollDelta.y
                                            )
                                            change.consume()
                                        }
                                    }
                                }
                            }
                        }
                    }
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

                val showVeil = state.isPaused || !state.isSurfaceReady || state.isAppLaunching
                AnimatedVisibility(
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
    sizeFactor: Float,
    onResizeStarted: () -> Unit,
    onResizeEnded: () -> Unit,
    isDesktopMode: Boolean = false
) {
    fun currentState() = stateManager.state.value

    val s = currentState()
    val context = LocalContext.current
    val displayMetrics = context.resources.displayMetrics

    val baseMinWidthPx = with(density) {
        if (isDesktopMode) DESKTOP_MIN_WIDTH_DP.dp.toPx().toInt()
        else MIN_WINDOW_WIDTH_DP.dp.toPx().toInt()
    }
    val baseMinHeightPx = with(density) {
        if (isDesktopMode) DESKTOP_MIN_HEIGHT_DP.dp.toPx().toInt()
        else MIN_WINDOW_HEIGHT_DP.dp.toPx().toInt()
    }

    val minWidthPx = if (s.isLandscape) baseMinHeightPx else baseMinWidthPx
    val minHeightPx = if (s.isLandscape) baseMinWidthPx else baseMinHeightPx

    val maxWidthPx = if (isDesktopMode) displayMetrics.widthPixels else MAX_WINDOW_WIDTH_DP
    val maxHeightPx = if (isDesktopMode) {
        val taskbarPx = with(density) { DESKTOP_TASKBAR_HEIGHT_DP.dp.toPx().toInt() }
        displayMetrics.heightPixels - taskbarPx
    } else {
        MAX_WINDOW_HEIGHT_DP
    }

    val handleWidth = (RESIZE_HANDLE_SIZE_DP.dp * sizeFactor)
        .coerceIn(RESIZE_HANDLE_SIZE_MIN_DP.dp, RESIZE_HANDLE_SIZE_MAX_DP.dp)

    val bottomHandleWidth = (RESIZE_HANDLE_BOTTOM_WIDTH_DP.dp * sizeFactor)
        .coerceIn(RESIZE_HANDLE_BOTTOM_WIDTH_MIN_DP.dp, RESIZE_HANDLE_BOTTOM_WIDTH_MAX_DP.dp)
    val bottomHandleHeight = RESIZE_HANDLE_BOTTOM_HEIGHT_DP.dp

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
        modifier = Modifier.align(Alignment.BottomEnd)
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
        isLeft = true,
        modifier = Modifier.align(Alignment.BottomStart)
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
        modifier = Modifier.align(Alignment.BottomCenter)
    )
}
