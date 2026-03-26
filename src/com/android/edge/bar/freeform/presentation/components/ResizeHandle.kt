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

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.*
import com.android.edge.bar.freeform.domain.FreeformConstants.RESIZE_HANDLE_SIZE_DP
import com.android.edge.bar.freeform.domain.FreeformConstants.RESIZE_HANDLE_BOTTOM_WIDTH_DP
import com.android.edge.bar.freeform.domain.FreeformConstants.RESIZE_HANDLE_BOTTOM_HEIGHT_DP
import com.android.edge.bar.freeform.domain.FreeformConstants.RESIZE_HANDLE_THICKNESS_DP
import com.android.edge.bar.freeform.domain.FreeformConstants.RESIZE_HANDLE_LENGTH_DP

@Composable
fun CornerResizeHandle(
    onResize: (Float, Float) -> Unit,
    onResizeStart: () -> Unit,
    onResizeEnd: () -> Unit,
    handleWidth: Dp = RESIZE_HANDLE_SIZE_DP.dp,
    modifier: Modifier = Modifier,
    isLeft: Boolean = false
) {
    val thickness = RESIZE_HANDLE_THICKNESS_DP.dp
    val length = RESIZE_HANDLE_LENGTH_DP.dp
    
    Box(
        modifier = modifier
            .size(length)
    ) {
        val dragLogic = Modifier.pointerInput(Unit) {
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

        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(thickness)
                .then(dragLogic)
        )

        Box(
            modifier = Modifier
                .align(if (isLeft) Alignment.CenterStart else Alignment.CenterEnd)
                .fillMaxHeight()
                .width(thickness)
                .then(dragLogic)
        )
    }
}

@Composable
fun BottomResizeHandle(
    onResize: (Float) -> Unit,
    onResizeStart: () -> Unit,
    onResizeEnd: () -> Unit,
    handleWidth: Dp = RESIZE_HANDLE_BOTTOM_WIDTH_DP.dp,
    handleHeight: Dp = RESIZE_HANDLE_BOTTOM_HEIGHT_DP.dp,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .width(handleWidth)
            .height(handleHeight)
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    var dragged = false
                    var accumulatedY = 0f
                    drag(down.id) { change ->
                        val dragAmount = change.position - change.previousPosition
                        accumulatedY += dragAmount.y
                        if (!dragged) {
                            dragged = true
                            onResizeStart()
                        }
                        change.consume()
                        onResize(accumulatedY)
                    }
                    if (dragged) {
                        onResizeEnd()
                    }
                }
            }
    )
}
