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

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.*
import androidx.compose.ui.geometry.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.unit.*
import com.android.edge.bar.freeform.domain.FreeformConstants.RESIZE_HANDLE_CORNER_RADIUS_DP
import com.android.edge.bar.freeform.domain.FreeformConstants.RESIZE_HANDLE_ARM_LENGTH_DP
import com.android.edge.bar.freeform.domain.FreeformConstants.RESIZE_HANDLE_CANVAS_SIZE_DP
import com.android.edge.bar.freeform.domain.FreeformConstants.RESIZE_HANDLE_SIZE_DP
import com.android.edge.bar.freeform.domain.FreeformConstants.RESIZE_HANDLE_STROKE_DP
import com.android.edge.bar.freeform.domain.FreeformConstants.RESIZE_HANDLE_BOTTOM_WIDTH_DP
import com.android.edge.bar.freeform.domain.FreeformConstants.RESIZE_HANDLE_BOTTOM_HEIGHT_DP
import com.android.edge.bar.freeform.domain.FreeformConstants.RESIZE_HANDLE_BOTTOM_INDICATOR_DP
import com.android.edge.bar.freeform.domain.FreeformConstants.RESIZE_HANDLE_BOTTOM_INDICATOR_HEIGHT_DP

@Composable
fun CornerResizeHandle(
    onResize: (Float, Float) -> Unit,
    onResizeStart: () -> Unit,
    onResizeEnd: () -> Unit,
    showIndicator: Boolean,
    isLeftCorner: Boolean = false,
    handleWidth: Dp = RESIZE_HANDLE_SIZE_DP.dp,
    canvasSize: Dp = RESIZE_HANDLE_CANVAS_SIZE_DP.dp,
    armLength: Dp = RESIZE_HANDLE_ARM_LENGTH_DP.dp,
    strokeWidth: Dp = RESIZE_HANDLE_STROKE_DP.dp,
    cornerRadius: Dp = RESIZE_HANDLE_CORNER_RADIUS_DP.dp,
    modifier: Modifier = Modifier
) {
    val alpha = if (showIndicator) 0.4f else 0f
    
    Box(
        modifier = modifier
            .size(handleWidth)
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { onResizeStart() },
                    onDragEnd = onResizeEnd,
                    onDrag = { change, dragAmount ->
                        change.consume()
                        onResize(dragAmount.x, dragAmount.y)
                    }
                )
            }
    ) {
        Canvas(
            modifier = Modifier
                .size(canvasSize)
                .alpha(alpha)
                .align(Alignment.Center)
        ) {
            val strokeWidthPx = strokeWidth.toPx()
            val armLengthPx = armLength.toPx()
            val cornerRadiusPx = cornerRadius.toPx()
            val halfStroke = strokeWidthPx / 2
            
            val path = Path().apply {
                if (isLeftCorner) {
                    moveTo(0f, size.height - armLengthPx)
                    quadraticTo(
                        x1 = 0f,
                        y1 = size.height,
                        x2 = armLengthPx,
                        y2 = size.height
                    )
                } else {
                    moveTo(size.width, size.height - armLengthPx)
                    quadraticTo(
                        x1 = size.width,
                        y1 = size.height,
                        x2 = size.width - armLengthPx,
                        y2 = size.height
                    )
                }
            }
            drawPath(
                path = path,
                color = Color.White,
                style = Stroke(
                    width = strokeWidthPx,
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round
                )
            )
        }
    }
}

@Composable
fun BottomResizeHandle(
    onResize: (Float) -> Unit,
    onResizeStart: () -> Unit,
    onResizeEnd: () -> Unit,
    showIndicator: Boolean,
    handleWidth: Dp = RESIZE_HANDLE_BOTTOM_WIDTH_DP.dp,
    handleHeight: Dp = RESIZE_HANDLE_BOTTOM_HEIGHT_DP.dp,
    indicatorWidth: Dp = RESIZE_HANDLE_BOTTOM_INDICATOR_DP.dp,
    indicatorHeight: Dp = RESIZE_HANDLE_BOTTOM_INDICATOR_HEIGHT_DP.dp,
    modifier: Modifier = Modifier
) {
    val alpha = if (showIndicator) 0.4f else 0.25f
    
    Box(
        modifier = modifier
            .width(handleWidth)
            .height(handleHeight)
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { onResizeStart() },
                    onDragEnd = onResizeEnd,
                    onDrag = { change, dragAmount ->
                        change.consume()
                        onResize(dragAmount.y)
                    }
                )
            }
    ) {
        Canvas(
            modifier = Modifier
                .width(indicatorWidth)
                .height(indicatorHeight)
                .alpha(alpha)
                .align(Alignment.Center)
        ) {
            drawRoundRect(
                color = Color.White,
                cornerRadius = CornerRadius(size.height / 2, size.height / 2),
                size = size
            )
        }
    }
}
