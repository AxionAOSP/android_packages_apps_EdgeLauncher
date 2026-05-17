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

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.*
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.android.edge.bar.R
import com.android.edge.bar.freeform.domain.FreeformConstants.DESKTOP_TITLE_BAR_HEIGHT_DP

private val desktopDecorTitleBarHeight = DESKTOP_TITLE_BAR_HEIGHT_DP.dp
private val desktopDecorMenuStartMargin = 12.dp
private val desktopDecorMenuContentStartPadding = 6.dp
private val desktopDecorMenuContentEndPadding = 8.dp
private val desktopDecorAppIconSize = 24.dp
private val desktopDecorAppNameStartMargin = 8.dp
private val desktopDecorChevronStartMargin = 8.dp
private val desktopDecorChevronEndMargin = 8.dp
private val desktopDecorChevronSize = 16.dp
private val desktopDecorControlWidth = 40.dp
private val desktopDecorControlHeight = 40.dp
private val desktopDecorControlIconSize = 20.dp
private val desktopDecorControlEndMargin = 8.dp
private val desktopDecorRequiredDragWidth = 48.dp
private val desktopDecorCompactDragWidth = 12.dp
private val desktopDecorMaxAppNameWidth = 130.dp
private val desktopDecorMinAppNameWidth = 48.dp

@Composable
fun DesktopTitleBar(
    onClose: () -> Unit,
    onBack: (() -> Unit)? = null,
    onMinimize: (() -> Unit)? = null,
    onMaximizeFullscreen: (() -> Unit)? = null,
    onDrag: (Float, Float) -> Unit,
    onDragStart: () -> Unit = {},
    onDragEnd: () -> Unit = {},
    isContentLight: Boolean,
    isMenuExpanded: Boolean,
    onMenuExpandedChange: (Boolean) -> Unit,
    showWindowMenu: Boolean = true,
    appIcon: Bitmap? = null,
    appName: String = "",
    onDropdownOffsetChanged: (Float) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val colors = rememberLuminanceColors(isContentLight)
    val menuInteractionSource = remember { MutableInteractionSource() }

    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val rightControlCount =
            1 + listOfNotNull(onMinimize, onMaximizeFullscreen).size
        val layout = calculateDesktopDecorLayout(
            headerWidth = maxWidth,
            hasBackButton = onBack != null,
            hasAppIcon = appIcon != null,
            hasAppName = appName.isNotBlank(),
            showWindowMenu = showWindowMenu,
            rightControlCount = rightControlCount
        )

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .height(desktopDecorTitleBarHeight)
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
                modifier = Modifier.fillMaxSize(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                onBack?.let {
                    DesktopTitleBarButton(
                        onClick = it,
                        icon = Icons.Rounded.ArrowBack,
                        contentDescription = stringResource(R.string.freeform_window_back),
                        contentColor = colors.pillColor,
                        modifier = Modifier.padding(end = desktopDecorControlEndMargin)
                    )
                }

                WindowTitleMenu(
                    appIcon = appIcon,
                    appName = appName,
                    showAppName = layout.showAppName,
                    showWindowMenu = showWindowMenu,
                    isMenuExpanded = isMenuExpanded,
                    colors = colors,
                    menuInteractionSource = menuInteractionSource,
                    onMenuExpandedChange = onMenuExpandedChange,
                    onDropdownOffsetChanged = onDropdownOffsetChanged,
                    appNameMaxWidth = layout.appNameMaxWidth
                )

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .widthIn(min = layout.dragWidth)
                )

                onMinimize?.let {
                    DesktopTitleBarButton(
                        onClick = it,
                        icon = Icons.Rounded.Remove,
                        contentDescription = stringResource(R.string.freeform_window_minimize),
                        contentColor = colors.pillColor,
                        modifier = Modifier.padding(end = desktopDecorControlEndMargin)
                    )
                }

                onMaximizeFullscreen?.let {
                    DesktopTitleBarButton(
                        onClick = it,
                        icon = Icons.Rounded.OpenInNew,
                        contentDescription = stringResource(R.string.freeform_window_fullscreen),
                        contentColor = colors.pillColor,
                        modifier = Modifier.padding(end = desktopDecorControlEndMargin)
                    )
                }

                DesktopTitleBarButton(
                    onClick = onClose,
                    icon = Icons.Rounded.Close,
                    contentDescription = stringResource(R.string.freeform_window_close),
                    contentColor = colors.pillColor,
                    modifier = Modifier.padding(end = desktopDecorControlEndMargin)
                )
            }
        }
    }
}

@Composable
private fun WindowTitleMenu(
    appIcon: Bitmap?,
    appName: String,
    showAppName: Boolean,
    showWindowMenu: Boolean,
    isMenuExpanded: Boolean,
    colors: LuminanceColorProvider,
    menuInteractionSource: MutableInteractionSource,
    onMenuExpandedChange: (Boolean) -> Unit,
    onDropdownOffsetChanged: (Float) -> Unit,
    appNameMaxWidth: Dp
) {
    if (appIcon == null && appName.isBlank() && !showWindowMenu) {
        return
    }

    Box(modifier = Modifier.padding(start = desktopDecorMenuStartMargin)) {
        Surface(
            modifier = Modifier
                .height(desktopDecorTitleBarHeight)
                .onGloballyPositioned { coordinates ->
                    onDropdownOffsetChanged(coordinates.positionInWindow().x)
                }
                .then(
                    if (showWindowMenu) {
                        Modifier.clickable(
                            interactionSource = menuInteractionSource,
                            indication = null
                        ) {
                            onMenuExpandedChange(!isMenuExpanded)
                        }
                    } else {
                        Modifier
                    }
                ),
            color = Color.Transparent,
            shape = RoundedCornerShape(16.dp)
        ) {
            Row(
                modifier = Modifier.padding(
                    start = desktopDecorMenuContentStartPadding,
                    end = desktopDecorMenuContentEndPadding
                ),
                verticalAlignment = Alignment.CenterVertically
            ) {
                appIcon?.let {
                    Image(
                        bitmap = it.asImageBitmap(),
                        contentDescription = null,
                        modifier = Modifier.size(desktopDecorAppIconSize)
                    )
                }
                if (showAppName) {
                    if (appIcon != null) {
                        Spacer(Modifier.width(desktopDecorAppNameStartMargin))
                    }
                    Text(
                        text = appName,
                        style = MaterialTheme.typography.labelLarge,
                        color = colors.pillColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.widthIn(max = appNameMaxWidth)
                    )
                }
                if (showWindowMenu) {
                    if (appIcon != null || showAppName) {
                        Spacer(Modifier.width(desktopDecorChevronStartMargin))
                    }
                    Icon(
                        imageVector = Icons.Rounded.ExpandMore,
                        contentDescription = stringResource(R.string.freeform_window_menu),
                        modifier = Modifier
                            .size(desktopDecorChevronSize)
                            .graphicsLayer(rotationZ = if (isMenuExpanded) 180f else 0f),
                        tint = colors.pillColor
                    )
                }
            }
        }
    }
}

@Composable
private fun DesktopTitleBarButton(
    onClick: () -> Unit,
    icon: ImageVector,
    contentDescription: String,
    contentColor: Color,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(width = desktopDecorControlWidth, height = desktopDecorControlHeight)
            .semantics { this.contentDescription = contentDescription }
            .clickable(
                role = Role.Button,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = contentColor,
            modifier = Modifier.size(desktopDecorControlIconSize)
        )
    }
}

private data class DesktopDecorLayout(
    val showAppName: Boolean,
    val appNameMaxWidth: Dp,
    val dragWidth: Dp
)

private fun calculateDesktopDecorLayout(
    headerWidth: Dp,
    hasBackButton: Boolean,
    hasAppIcon: Boolean,
    hasAppName: Boolean,
    showWindowMenu: Boolean,
    rightControlCount: Int
): DesktopDecorLayout {
    val leftControlsWidth = if (hasBackButton) {
        desktopDecorControlWidth + desktopDecorControlEndMargin
    } else {
        0.dp
    }
    val rightControlsWidth =
        (desktopDecorControlWidth + desktopDecorControlEndMargin) * rightControlCount.toFloat()
    val compactChipWidth = calculateAppChipFixedWidth(
        hasAppIcon = hasAppIcon,
        showAppName = false,
        showWindowMenu = showWindowMenu
    )
    val spaciousDragAvailable =
        headerWidth - leftControlsWidth - rightControlsWidth - compactChipWidth
    val dragWidth = if (spaciousDragAvailable >= desktopDecorRequiredDragWidth) {
        desktopDecorRequiredDragWidth
    } else {
        desktopDecorCompactDragWidth
    }
    val expandedChipWidth = calculateAppChipFixedWidth(
        hasAppIcon = hasAppIcon,
        showAppName = hasAppName,
        showWindowMenu = showWindowMenu
    )
    val appNameMaxWidth =
        (headerWidth - leftControlsWidth - rightControlsWidth - dragWidth - expandedChipWidth)
            .coerceIn(0.dp, desktopDecorMaxAppNameWidth)
    val showAppName = hasAppName && appNameMaxWidth >= desktopDecorMinAppNameWidth
    return DesktopDecorLayout(
        showAppName = showAppName,
        appNameMaxWidth = if (showAppName) appNameMaxWidth else 0.dp,
        dragWidth = dragWidth
    )
}

private fun calculateAppChipFixedWidth(
    hasAppIcon: Boolean,
    showAppName: Boolean,
    showWindowMenu: Boolean
): Dp {
    var width = desktopDecorMenuStartMargin + desktopDecorMenuContentStartPadding
    if (hasAppIcon) {
        width += desktopDecorAppIconSize
    }
    if (showAppName && hasAppIcon) {
        width += desktopDecorAppNameStartMargin
    }
    if (showWindowMenu) {
        if (hasAppIcon || showAppName) {
            width += desktopDecorChevronStartMargin
        }
        width += desktopDecorChevronSize + desktopDecorChevronEndMargin
    } else {
        width += desktopDecorMenuContentEndPadding
    }
    return width
}

@Composable
fun DesktopMenuDropdown(
    onResizeToFullscreen: () -> Unit,
    onResizeToHalfLeft: () -> Unit,
    onResizeToHalfRight: () -> Unit,
    onMaximizeFullscreen: () -> Unit,
    isContentLight: Boolean,
    showDesktopResizeOptions: Boolean = true,
    menuWidth: Dp = 210.dp,
    modifier: Modifier = Modifier
) {
    val colors = rememberLuminanceColors(isContentLight)

    val openFullscreenItem = DesktopMenuItemData(
        stringResource(R.string.freeform_window_open_fullscreen),
        Icons.Rounded.OpenInFull,
        onMaximizeFullscreen
    )
    val menuItems = if (showDesktopResizeOptions) {
        listOf(
            DesktopMenuItemData(
                stringResource(R.string.freeform_window_fill_screen),
                Icons.Rounded.Fullscreen,
                onResizeToFullscreen
            ),
            DesktopMenuItemData(
                stringResource(R.string.freeform_window_left_half),
                Icons.Rounded.VerticalSplit,
                onResizeToHalfLeft
            ),
            DesktopMenuItemData(
                stringResource(R.string.freeform_window_right_half),
                Icons.Rounded.VerticalSplit,
                onResizeToHalfRight,
                mirrored = true
            ),
            openFullscreenItem
        )
    } else {
        listOf(openFullscreenItem)
    }

    Column(
        modifier = modifier
            .width(menuWidth)
            .padding(vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(1.dp)
    ) {
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
                tonalElevation = 0.dp,
                shadowElevation = 0.dp,
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
                        style = MaterialTheme.typography.labelMedium,
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
