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
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.android.edge.bar.freeform.domain.FreeformConstants.TITLE_BAR_BUTTON_WIDTH_MULTIPLIER
import com.android.edge.bar.freeform.domain.FreeformConstants.TITLE_BAR_BUTTON_WIDTH_MIN_DP
import com.android.edge.bar.freeform.domain.FreeformConstants.TITLE_BAR_BUTTON_WIDTH_MAX_DP

@Composable
fun TitleBar(
    onClose: () -> Unit,
    onMinimize: () -> Unit,
    onDrag: (Float, Float) -> Unit,
    onDragStart: () -> Unit = {},
    onDragEnd: () -> Unit = {},
    titleBarHeight: Dp,
    iconSize: Dp,
    dragHandleHeight: Dp,
    modifier: Modifier = Modifier
) {
    val buttonWidth = (titleBarHeight * TITLE_BAR_BUTTON_WIDTH_MULTIPLIER)
        .coerceIn(TITLE_BAR_BUTTON_WIDTH_MIN_DP.dp, TITLE_BAR_BUTTON_WIDTH_MAX_DP.dp)
    val context = LocalContext.current
    val isDark = isSystemInDarkTheme()

    val titleBarColor = Color(context.getColor(
        if (isDark) android.R.color.system_neutral1_1000
        else android.R.color.system_neutral1_0
    ))

    val buttonColor = Color(context.getColor(
        if (isDark) android.R.color.system_neutral1_900
        else android.R.color.system_neutral1_50
    ))
    
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .height(titleBarHeight),
        color = titleBarColor,
        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Surface(
                onClick = onMinimize,
                shape = CircleShape,
                color = buttonColor,
                contentColor = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier
                    .fillMaxHeight()
                    .width(buttonWidth)
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.fillMaxSize()
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Remove,
                        contentDescription = "Minimize",
                        modifier = Modifier.size(iconSize)
                    )
                }
            }
            
            Box(
                modifier = Modifier
                    .weight(1f)
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
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    modifier = Modifier
                        .width(40.dp)
                        .height(4.dp),
                    shape = RoundedCornerShape(2.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                ) {}
            }
            
            Surface(
                onClick = onClose,
                shape = CircleShape,
                color = buttonColor,
                contentColor = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier
                    .fillMaxHeight()
                    .width(buttonWidth)
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.fillMaxSize()
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = "Close",
                        modifier = Modifier.size(iconSize)
                    )
                }
            }
        }
    }
}
