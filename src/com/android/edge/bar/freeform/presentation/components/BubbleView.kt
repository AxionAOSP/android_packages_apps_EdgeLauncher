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

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.*
import androidx.compose.material3.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.*
import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.*
import com.android.edge.bar.R
import com.android.edge.bar.freeform.domain.FreeformConstants.BUBBLE_SIZE_DP
import com.android.edge.bar.freeform.domain.FreeformConstants.BUBBLE_ICON_SIZE_DP
import kotlin.math.abs

@Composable
fun BubbleView(
    appIcon: Bitmap?,
    onClick: () -> Unit,
    onDrag: ((Float, Float) -> Unit)? = null,
    onDragEnd: (() -> Unit)? = null,
    onDragStart: (() -> Unit)? = null,
    isHovered: Boolean = false,
    modifier: Modifier = Modifier
) {
    val bgColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.4f)
    
    Box(
        modifier = modifier
            .size(BUBBLE_SIZE_DP.dp)
            .clip(CircleShape)
            .background(bgColor)
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    var totalDrag = 0f
                    var wasDragging = false
                    
                    val dragResult = drag(down.id) { change ->
                        val dragAmount = change.positionChange()
                        totalDrag += abs(dragAmount.x) + abs(dragAmount.y)
                        
                        if (totalDrag > 10f && !wasDragging) {
                            wasDragging = true
                            onDragStart?.invoke()
                        }
                        
                        if (wasDragging) {
                            change.consume()
                            onDrag?.invoke(dragAmount.x, dragAmount.y)
                        }
                    }
                    
                    if (wasDragging) {
                        onDragEnd?.invoke()
                    } else {
                        onClick()
                    }
                }
            },
        contentAlignment = Alignment.Center
    ) {
        val context = LocalContext.current
        appIcon?.let {
            Image(
                bitmap = it.asImageBitmap(),
                contentDescription = stringResource(R.string.tap_to_expand),
                modifier = Modifier
                    .size(BUBBLE_ICON_SIZE_DP.dp)
                    .clip(CircleShape)
            )
        }
    }
}
