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
package com.android.edge.bar.freeform.presentation.components

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.*
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import com.android.edge.bar.freeform.domain.FreeformConstants.DESKTOP_TITLE_BAR_HEIGHT_DP

@Composable
fun DesktopTitleBar(
    onClose: () -> Unit,
    onMinimize: () -> Unit,
    onBack: () -> Unit,
    onDrag: (Float, Float) -> Unit,
    onDragStart: () -> Unit = {},
    onDragEnd: () -> Unit = {},
    isContentLight: Boolean,
    isMenuExpanded: Boolean,
    onMenuExpandedChange: (Boolean) -> Unit,
    appIcon: android.graphics.Bitmap? = null,
    onDropdownOffsetChanged: (Float) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val colors = rememberLuminanceColors(isContentLight)

    Box(modifier = Modifier.fillMaxWidth()) {
        val titleBarHeight = DESKTOP_TITLE_BAR_HEIGHT_DP.dp
        val buttonSize = 24.dp
        val iconSize = 16.dp
        val padding = 6.dp
        val spacing = 4.dp

        Surface(
            modifier = modifier
                .fillMaxWidth()
                .height(titleBarHeight)
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { onDragStart() },
                        onDragEnd = { onDragEnd() },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            onDrag(dragAmount.x, dragAmount.y)
                        }
                    )
                },
            color = colors.backgroundColor,
            shape = RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = padding, vertical = padding),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(spacing)
            ) {
                Surface(
                    modifier = Modifier
                        .height(titleBarHeight - (padding * 2))
                        .onGloballyPositioned { coordinates ->
                            onDropdownOffsetChanged(coordinates.positionInWindow().x)
                        }
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) {
                            onMenuExpandedChange(!isMenuExpanded)
                        },
                    color = Color.Transparent,
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        appIcon?.let {
                            Image(
                                bitmap = it.asImageBitmap(),
                                contentDescription = null,
                                modifier = Modifier.size(iconSize)
                            )
                        }
                        Icon(
                            imageVector = if (isMenuExpanded) Icons.Rounded.KeyboardArrowUp else Icons.Rounded.KeyboardArrowDown,
                            contentDescription = null,
                            modifier = Modifier.size(iconSize),
                            tint = colors.pillColor
                        )
                    }
                }

                Spacer(modifier = Modifier.weight(1f))

                DesktopTitleBarButton(
                    onClick = onBack,
                    icon = Icons.Rounded.ArrowBack,
                    contentDescription = "Back",
                    buttonColor = Color.Transparent,
                    contentColor = colors.pillColor,
                    buttonSize = buttonSize,
                    iconSize = iconSize - 2.dp
                )

                DesktopTitleBarButton(
                    onClick = onClose,
                    icon = Icons.Rounded.Close,
                    contentDescription = "Close",
                    buttonColor = Color.Transparent,
                    contentColor = colors.pillColor,
                    buttonSize = buttonSize,
                    iconSize = iconSize - 2.dp
                )
            }
        }
    }
}

@Composable
private fun DesktopTitleBarButton(
    onClick: () -> Unit,
    icon: ImageVector,
    contentDescription: String,
    buttonColor: Color,
    contentColor: Color,
    buttonSize: Dp,
    iconSize: Dp
) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = buttonColor,
        contentColor = contentColor,
        modifier = Modifier.size(buttonSize)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.fillMaxSize()
        ) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = contentColor,
                modifier = Modifier.size(iconSize)
            )
        }
    }
}

@Composable
fun DesktopMenuDropdown(
    onResizeToFullscreen: () -> Unit,
    onResizeToHalfLeft: () -> Unit,
    onResizeToHalfRight: () -> Unit,
    onMaximizeFullscreen: () -> Unit,
    isContentLight: Boolean,
    modifier: Modifier = Modifier
) {
    val colors = rememberLuminanceColors(isContentLight)

    Column(
        modifier = modifier
            .width(210.dp)
            .padding(vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(1.dp)
    ) {
        val menuItems = listOf(
            DesktopMenuItemData("Fill Screen", Icons.Rounded.Fullscreen, onResizeToFullscreen),
            DesktopMenuItemData("Left Half", Icons.Rounded.VerticalSplit, onResizeToHalfLeft),
            DesktopMenuItemData("Right Half", Icons.Rounded.VerticalSplit, onResizeToHalfRight, mirrored = true),
            DesktopMenuItemData("Open Fullscreen", Icons.Rounded.OpenInFull, onMaximizeFullscreen)
        )

        menuItems.forEachIndexed { index, item ->
            val isFirst = index == 0
            val isLast = index == menuItems.lastIndex
            val topRadius = if (isFirst) 16.dp else 4.dp
            val bottomRadius = if (isLast) 16.dp else 4.dp

            Surface(
                onClick = item.onClick,
                shape = RoundedCornerShape(
                    topStart = topRadius,
                    topEnd = topRadius,
                    bottomStart = bottomRadius,
                    bottomEnd = bottomRadius
                ),
                color = colors.contentColor,
                tonalElevation = 4.dp,
                shadowElevation = 4.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 12.dp)
                ) {
                    Icon(
                        imageVector = item.icon,
                        contentDescription = null,
                        tint = colors.pillColor,
                        modifier = Modifier
                            .size(18.dp)
                            .then(if (item.mirrored) Modifier.mirrorHorizontally() else Modifier)
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        text = item.text,
                        style = MaterialTheme.typography.bodyMedium.copy(fontSize = 12.sp),
                        color = colors.pillColor,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

private data class DesktopMenuItemData(
    val text: String,
    val icon: ImageVector,
    val onClick: () -> Unit,
    val mirrored: Boolean = false
)

private fun Modifier.mirrorHorizontally(): Modifier {
    return this.then(
        Modifier.graphicsLayer(scaleX = -1f)
    )
}


