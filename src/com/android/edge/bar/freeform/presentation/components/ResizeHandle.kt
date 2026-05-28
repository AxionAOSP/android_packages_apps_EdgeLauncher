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

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.android.edge.bar.R
import com.android.edge.bar.freeform.domain.FreeformConstants.RESIZE_HANDLE_BOTTOM_HEIGHT_DP
import com.android.edge.bar.freeform.domain.FreeformConstants.RESIZE_HANDLE_BOTTOM_WIDTH_DP

@Composable
fun BottomResizeBar(
    onLeftResize: (Float, Float) -> Unit,
    onBack: () -> Unit,
    onRightResize: (Float, Float) -> Unit,
    onResizeStart: () -> Unit,
    onResizeEnd: () -> Unit,
    isContentLight: Boolean,
    cornerRadius: Dp,
    barWidth: Dp = RESIZE_HANDLE_BOTTOM_WIDTH_DP.dp,
    barHeight: Dp = RESIZE_HANDLE_BOTTOM_HEIGHT_DP.dp,
    modifier: Modifier = Modifier
) {
    val colors = rememberLuminanceColors(isContentLight)
    val backDescription = stringResource(R.string.freeform_window_back)

    Surface(
        modifier = modifier
            .width(barWidth)
            .height(barHeight),
        color = colors.backgroundColor,
        shape = RoundedCornerShape(bottomStart = cornerRadius, bottomEnd = cornerRadius)
    ) {
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            Row(
                modifier = Modifier.fillMaxSize(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ResizeDotHandle(
                    color = colors.pillColor,
                    modifier = Modifier
                        .width(40.dp)
                        .fillMaxHeight()
                        .resize2dInput(onLeftResize, onResizeStart, onResizeEnd)
                )

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .semantics {
                                contentDescription = backDescription
                            }
                            .clickable(
                                role = Role.Button,
                                onClick = onBack
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.ArrowBack,
                            contentDescription = null,
                            tint = colors.pillColor,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                ResizeDotHandle(
                    color = colors.pillColor,
                    modifier = Modifier
                        .width(40.dp)
                        .fillMaxHeight()
                        .resize2dInput(onRightResize, onResizeStart, onResizeEnd)
                )
            }
        }
    }
}

@Composable
private fun ResizeDotHandle(
    color: Color,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        ResizeDot(color)
    }
}

@Composable
private fun ResizeDot(color: Color) {
    Box(
        modifier = Modifier
            .size(6.dp)
            .clip(CircleShape)
            .background(color)
    )
}

private fun Modifier.resize2dInput(
    onResize: (Float, Float) -> Unit,
    onResizeStart: () -> Unit,
    onResizeEnd: () -> Unit
): Modifier = pointerInput(onResize, onResizeStart, onResizeEnd) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        var dragged = false
        var accumulatedX = 0f
        var accumulatedY = 0f
        drag(down.id) { change ->
            val dragAmount = change.position - change.previousPosition
            accumulatedX += dragAmount.x
            accumulatedY += dragAmount.y
            if (!dragged) {
                dragged = true
                onResizeStart()
            }
            change.consume()
            onResize(accumulatedX, accumulatedY)
        }
        if (dragged) {
            onResizeEnd()
        }
    }
}
