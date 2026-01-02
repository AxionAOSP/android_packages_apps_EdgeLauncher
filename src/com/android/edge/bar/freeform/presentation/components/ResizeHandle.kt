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

import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.*
import com.android.edge.bar.freeform.domain.FreeformConstants.RESIZE_HANDLE_SIZE_DP
import com.android.edge.bar.freeform.domain.FreeformConstants.RESIZE_HANDLE_BOTTOM_WIDTH_DP
import com.android.edge.bar.freeform.domain.FreeformConstants.RESIZE_HANDLE_BOTTOM_HEIGHT_DP

@Composable
fun CornerResizeHandle(
    onResize: (Float, Float) -> Unit,
    onResizeStart: () -> Unit,
    onResizeEnd: () -> Unit,
    handleWidth: Dp = RESIZE_HANDLE_SIZE_DP.dp,
    modifier: Modifier = Modifier
) {
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
    )
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
                detectDragGestures(
                    onDragStart = { onResizeStart() },
                    onDragEnd = onResizeEnd,
                    onDrag = { change, dragAmount ->
                        change.consume()
                        onResize(dragAmount.y)
                    }
                )
            }
    )
}
