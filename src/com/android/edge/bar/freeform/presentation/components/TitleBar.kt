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
package com.android.edge.bar.freeform.presentation.components

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.Image
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Fullscreen
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.PictureInPictureAlt
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import com.android.edge.bar.freeform.domain.FreeformConstants.TITLE_BAR_BUTTON_WIDTH_MIN_DP
import com.android.edge.bar.freeform.domain.FreeformConstants.TITLE_BAR_HEIGHT_DP
import com.android.edge.bar.freeform.domain.FreeformConstants.TITLE_BAR_ICON_SIZE_DP

private fun lerpDp(start: Dp, stop: Dp, fraction: Float): Dp {
    return (start.value + fraction * (stop.value - start.value)).dp
}

private fun lerpSp(start: TextUnit, stop: TextUnit, fraction: Float): TextUnit {
    return (start.value + fraction * (stop.value - start.value)).sp
}

data class LuminanceColorProvider(
    val backgroundColor: Color,
    val pillColor: Color,
    val contentColor: Color
)

@Composable
fun rememberLuminanceColors(isContentLight: Boolean): LuminanceColorProvider {
    val context = LocalContext.current
    return remember(isContentLight) {
        if (isContentLight) {
            LuminanceColorProvider(
                backgroundColor = Color(context.getColor(android.R.color.system_neutral1_800)),
                pillColor = Color(context.getColor(android.R.color.system_neutral1_50)),
                contentColor = Color(context.getColor(android.R.color.system_neutral1_800))
            )
        } else {
            LuminanceColorProvider(
                backgroundColor = Color(context.getColor(android.R.color.system_neutral1_50)),
                pillColor = Color(context.getColor(android.R.color.system_neutral1_800)),
                contentColor = Color(context.getColor(android.R.color.system_neutral1_50))
            )
        }
    }
}

@Composable
fun TitleBar(
    onClose: () -> Unit,
    onMinimize: () -> Unit,
    onHangup: () -> Unit,
    onMaximizeFullscreen: () -> Unit,
    onBack: () -> Unit,
    onDrag: (Float, Float) -> Unit,
    onDragStart: () -> Unit = {},
    onDragEnd: () -> Unit = {},
    isContentLight: Boolean,
    isMenuExpanded: Boolean,
    onMenuExpandedChange: (Boolean) -> Unit,
    appIcon: android.graphics.Bitmap? = null,
    onDropdownOffsetChanged: (Float) -> Unit = {},
    showEducation: Boolean = false,
    onEducationDismissed: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val colors = rememberLuminanceColors(isContentLight)
    
    Box(modifier = Modifier.fillMaxWidth()) {
        val titleBarHeight = TITLE_BAR_HEIGHT_DP.dp
        val buttonWidth = TITLE_BAR_BUTTON_WIDTH_MIN_DP.dp
        val iconSize = TITLE_BAR_ICON_SIZE_DP.dp
        val padding = 8.dp
        val spacing = 8.dp

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
            shape = RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = padding, vertical = padding),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(spacing)
            ) {
                TitleBarButton(
                    onClick = onBack,
                    icon = Icons.Rounded.ArrowBack,
                    contentDescription = "Back",
                    buttonColor = colors.pillColor,
                    contentColor = colors.contentColor,
                    buttonWidth = buttonWidth,
                    iconSize = iconSize - 2.dp
                )

                Surface(
                    modifier = Modifier
                        .height(titleBarHeight - (padding * 2))
                        .aspectRatio(2.5f, matchHeightConstraintsFirst = true)
                        .onGloballyPositioned { coordinates ->
                            onDropdownOffsetChanged(coordinates.positionInWindow().x)
                        }
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) {
                            if (showEducation) onEducationDismissed()
                            onMenuExpandedChange(!isMenuExpanded)
                        },
                    color = colors.pillColor,
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
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
                            modifier = Modifier.size(iconSize * 1.3f),
                            tint = colors.contentColor
                        )
                    }
                }

                Spacer(modifier = Modifier.weight(1f))

                TitleBarButton(
                    onClick = onClose,
                    icon = Icons.Rounded.Close,
                    contentDescription = "Close",
                    buttonColor = colors.pillColor,
                    contentColor = colors.contentColor,
                    buttonWidth = buttonWidth,
                    iconSize = iconSize - 2.dp
                )
            }
        }
    }
}

@Composable
private fun TitleBarButton(
    onClick: () -> Unit,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    buttonColor: Color,
    contentColor: Color,
    buttonWidth: Dp,
    iconSize: Dp
) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = buttonColor,
        contentColor = contentColor,
        modifier = Modifier
            .fillMaxHeight()
            .width(buttonWidth)
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
fun MenuPill(
    onMinimize: () -> Unit,
    onHangup: () -> Unit,
    onMaximizeFullscreen: () -> Unit,
    isContentLight: Boolean,
    scaleFactor: Float,
    modifier: Modifier = Modifier
) {
    val colors = rememberLuminanceColors(isContentLight)
    
    val minPillHeight = 32.dp
    val maxPillHeight = 48.dp
    val pillHeight = lerpDp(minPillHeight, maxPillHeight, scaleFactor)
    
    val minButtonSize = 24.dp
    val maxButtonSize = 40.dp
    val buttonSize = lerpDp(minButtonSize, maxButtonSize, scaleFactor)
    
    val minIconSize = 16.dp
    val maxIconSize = 24.dp
    val iconSize = lerpDp(minIconSize, maxIconSize, scaleFactor)
    
    val minPadding = 4.dp
    val maxPadding = 12.dp
    val padding = lerpDp(minPadding, maxPadding, scaleFactor)
    
    val minSpacing = 1.dp
    val maxSpacing = 4.dp
    val spacing = lerpDp(minSpacing, maxSpacing, scaleFactor)
    
    Surface(
        shape = RoundedCornerShape(lerpDp(16.dp, 24.dp, scaleFactor)),
        color = colors.pillColor,
        tonalElevation = lerpDp(2.dp, 4.dp, scaleFactor),
        shadowElevation = lerpDp(3.dp, 5.dp, scaleFactor),
        modifier = modifier.height(pillHeight)
    ) {
        Row(
            modifier = Modifier
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = padding),
            horizontalArrangement = Arrangement.spacedBy(spacing),
            verticalAlignment = Alignment.CenterVertically
        ) {
            PillIconButton(
                icon = Icons.Rounded.Remove,
                contentDescription = "Minimize",
                onClick = onMinimize,
                tint = colors.contentColor,
                buttonSize = buttonSize,
                iconSize = iconSize
            )

            PillIconButton(
                icon = Icons.Rounded.PictureInPictureAlt,
                contentDescription = "Hangup",
                onClick = onHangup,
                tint = colors.contentColor,
                buttonSize = buttonSize,
                iconSize = iconSize
            )
            
            PillIconButton(
                icon = Icons.Rounded.Fullscreen,
                contentDescription = "Fullscreen",
                onClick = onMaximizeFullscreen,
                tint = colors.contentColor,
                buttonSize = buttonSize,
                iconSize = iconSize
            )
        }
    }
}

@Composable
private fun PillIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    tint: Color,
    buttonSize: Dp,
    iconSize: Dp
) {
    IconButton(
        onClick = onClick,
        modifier = Modifier.size(buttonSize)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = tint,
            modifier = Modifier.size(iconSize)
        )
    }
}
