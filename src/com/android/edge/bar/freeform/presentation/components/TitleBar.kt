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

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

private fun lerpDp(start: Dp, stop: Dp, fraction: Float): Dp {
    return (start.value + fraction * (stop.value - start.value)).dp
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
    onMaximizeFullscreen: () -> Unit,
    onDrag: (Float, Float) -> Unit,
    onDragStart: () -> Unit = {},
    onDragEnd: () -> Unit = {},
    isContentLight: Boolean,
    scaleFactor: Float = 1f,
    modifier: Modifier = Modifier
) {
    val colors = rememberLuminanceColors(isContentLight)
    val fraction = ((scaleFactor - 0.7f) / 0.6f).coerceIn(0f, 1f)

    val titleBarHeight = lerpDp(24.dp, 36.dp, fraction)
    val buttonSize = lerpDp(18.dp, 28.dp, fraction)
    val iconSize = lerpDp(11.dp, 18.dp, fraction)
    val horizontalPadding = 6.dp
    val buttonSpacing = 4.dp
    val cornerRadius = 12.dp
    val dragHandleWidth = 32.dp
    val dragHandleHeight = 3.dp

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
        shape = RoundedCornerShape(topStart = cornerRadius, topEnd = cornerRadius)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = horizontalPadding),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .width(dragHandleWidth)
                    .height(dragHandleHeight)
                    .background(
                        color = colors.pillColor,
                        shape = RoundedCornerShape(dragHandleHeight / 2)
                    )
            )

            Row(
                modifier = Modifier.fillMaxSize(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TitleBarButton(
                    onClick = onMinimize,
                    icon = Icons.Rounded.Remove,
                    contentDescription = "Minimize",
                    buttonColor = colors.pillColor,
                    contentColor = colors.contentColor,
                    buttonSize = buttonSize,
                    iconSize = iconSize
                )

                Spacer(modifier = Modifier.width(buttonSpacing))

                TitleBarButton(
                    onClick = onMaximizeFullscreen,
                    icon = Icons.Rounded.OpenInNew,
                    contentDescription = "Fullscreen",
                    buttonColor = colors.pillColor,
                    contentColor = colors.contentColor,
                    buttonSize = buttonSize,
                    iconSize = iconSize
                )

                Spacer(modifier = Modifier.weight(1f))

                TitleBarButton(
                    onClick = onClose,
                    icon = Icons.Rounded.Close,
                    contentDescription = "Close",
                    buttonColor = colors.pillColor,
                    contentColor = colors.contentColor,
                    buttonSize = buttonSize,
                    iconSize = iconSize
                )
            }
        }
    }
}

@Composable
private fun TitleBarButton(
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
