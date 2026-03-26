/*
 * Copyright (C) 2025-2026 AxionOS Project
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
import android.graphics.Rect
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.OpenInBrowser
import androidx.compose.material.icons.rounded.OpenInFull
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup

val PANEL_WIDTH = 130.dp
val PANEL_HEIGHT = 308.dp
private val PANEL_CORNER_RADIUS = 24.dp

private val HANDLE_AREA_HEIGHT = 40.dp
private val HANDLE_WIDTH = 16.dp
private val HANDLE_HEIGHT = 3.dp

private val SETTINGS_AREA_HEIGHT = 42.dp
private val SETTINGS_ICON_SIZE = 16.dp

private val ICON_SIZE = 46.dp
private val ICON_IMAGE_SIZE = 34.dp
private val ICON_CORNER_RADIUS = 14.dp

const val MAX_PINNED_APPS = 8

@Composable
fun EdgeContentView(
    onPinnedAppClick: (context: Context, packageName: String) -> Unit,
    onSettingsClick: () -> Unit,
    onDrag: (deltaX: Float, deltaY: Float) -> Unit,
    onDragEnd: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    val allApps by produceState(initialValue = emptyList<AppInfo>()) {
        value = AppHelper.getInstalledApps(context)
    }

    val pinnedApps by produceState(initialValue = emptyList<AppInfo>(), key1 = allApps) {
        val pinnedPkgs = PinnedApps.getPinned(context)
        value = allApps.filter { it.packageName in pinnedPkgs }.take(MAX_PINNED_APPS)
    }

    var activePopup by remember { mutableStateOf<PopupState?>(null) }

    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        shape = RoundedCornerShape(PANEL_CORNER_RADIUS),
        tonalElevation = 2.dp,
        modifier = modifier
            .width(PANEL_WIDTH)
            .height(PANEL_HEIGHT)
            .border(
                width = 0.5.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f),
                shape = RoundedCornerShape(PANEL_CORNER_RADIUS)
            )
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(HANDLE_AREA_HEIGHT)
                    .pointerInput(Unit) {
                        detectDragGestures(
                            onDrag = { change, dragAmount ->
                                change.consume()
                                onDrag(dragAmount.x, dragAmount.y)
                            },
                            onDragEnd = onDragEnd,
                            onDragCancel = onDragEnd
                        )
                    },
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .width(HANDLE_WIDTH)
                        .height(HANDLE_HEIGHT)
                        .clip(RoundedCornerShape(HANDLE_HEIGHT / 2))
                        .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(horizontal = 12.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                for (rowIndex in 0 until 4) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        for (colIndex in 0 until 2) {
                            val index = rowIndex * 2 + colIndex
                            if (index < pinnedApps.size) {
                                AppIconButton(
                                    appInfo = pinnedApps[index],
                                    onClick = {
                                        onPinnedAppClick(context, pinnedApps[index].packageName)
                                    },
                                    onLongClick = { bounds ->
                                        activePopup = PopupState(
                                            packageName = pinnedApps[index].packageName,
                                            anchorBounds = bounds
                                        )
                                    }
                                )
                            } else {
                                Spacer(modifier = Modifier.size(ICON_SIZE))
                            }
                        }
                    }
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(SETTINGS_AREA_HEIGHT)
                    .clickable { onSettingsClick() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.Settings,
                    contentDescription = null,
                    modifier = Modifier.size(SETTINGS_ICON_SIZE),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }

    activePopup?.let { popup ->
        LaunchModePopup(
            anchorBounds = popup.anchorBounds,
            onDismiss = { activePopup = null },
            onLaunchFull = {
                AppHelper.launchAppFull(context, popup.packageName)
                activePopup = null
            },
            onLaunchFreeform = {
                AppHelper.launchApp(popup.packageName)
                activePopup = null
            }
        )
    }
}

@Composable
private fun AppIconButton(
    appInfo: AppInfo,
    onClick: () -> Unit,
    onLongClick: (Rect) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var anchorBounds by remember { mutableStateOf<Rect?>(null) }
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.88f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessHigh
        )
    )

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(ICON_SIZE)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(RoundedCornerShape(ICON_CORNER_RADIUS))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.4f))
            .onGloballyPositioned { coords ->
                val pos = coords.positionInWindow()
                val size = coords.size
                anchorBounds = Rect(
                    pos.x.toInt(),
                    pos.y.toInt(),
                    (pos.x + size.width).toInt(),
                    (pos.y + size.height).toInt()
                )
            }
            .combinedClickable(
                interactionSource = interactionSource,
                indication = ripple(
                    bounded = true,
                    radius = ICON_SIZE / 2,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                ),
                onClick = onClick,
                onLongClick = { anchorBounds?.let { onLongClick(it) } }
            )
    ) {
        Image(
            painter = AppHelper.getAppPainter(context, appInfo.packageName, appInfo.icon),
            contentDescription = appInfo.label,
            modifier = Modifier
                .size(ICON_IMAGE_SIZE)
                .clip(RoundedCornerShape(10.dp))
        )
    }
}

@Composable
private fun LaunchModePopup(
    anchorBounds: Rect,
    onDismiss: () -> Unit,
    onLaunchFull: () -> Unit,
    onLaunchFreeform: () -> Unit
) {
    val density = LocalDensity.current
    val configuration = LocalConfiguration.current
    val screenWidth = with(density) { configuration.screenWidthDp.dp.toPx() }

    val popupWidth = 120.dp
    val popupWidthPx = with(density) { popupWidth.toPx() }

    val anchorCenterX = (anchorBounds.left + anchorBounds.right) / 2f
    var offsetX = anchorCenterX - (popupWidthPx / 2f)

    val margin = with(density) { 8.dp.toPx() }
    offsetX = offsetX.coerceIn(margin, screenWidth - popupWidthPx - margin)

    val offsetY = anchorBounds.bottom.toFloat() + with(density) { 8.dp.toPx() }

    Popup(
        offset = IntOffset(offsetX.toInt(), offsetY.toInt()),
        onDismissRequest = onDismiss
    ) {
        Surface(
            shape = RoundedCornerShape(32.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.96f),
            tonalElevation = 3.dp,
            modifier = Modifier
                .width(popupWidth)
                .border(
                    width = 0.5.dp,
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
                    shape = RoundedCornerShape(32.dp)
                )
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .padding(horizontal = 14.dp, vertical = 12.dp)
                    .fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier.weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    LaunchModeIconButton(
                        icon = Icons.Rounded.OpenInFull,
                        contentDescription = "Full screen",
                        onClick = onLaunchFull
                    )
                }

                Box(
                    modifier = Modifier
                        .width(1.dp)
                        .height(32.dp)
                        .background(
                            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.8f),
                            shape = RoundedCornerShape(0.5.dp)
                        )
                )

                Box(
                    modifier = Modifier.weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    LaunchModeIconButton(
                        icon = Icons.Rounded.OpenInBrowser,
                        contentDescription = "Freeform",
                        onClick = onLaunchFreeform
                    )
                }
            }
        }
    }
}

@Composable
private fun LaunchModeIconButton(
    icon: ImageVector,
    contentDescription: String,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.9f),
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.92f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessHigh
        )
    )

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(48.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(CircleShape)
            .background(containerColor)
            .clickable(
                interactionSource = interactionSource,
                indication = ripple(bounded = true, radius = 24.dp),
                onClick = onClick
            )
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = contentColor,
            modifier = Modifier.size(22.dp)
        )
    }
}

private data class PopupState(
    val packageName: String,
    val anchorBounds: Rect
)
