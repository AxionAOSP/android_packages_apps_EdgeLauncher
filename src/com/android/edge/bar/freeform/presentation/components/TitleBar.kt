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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp

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
fun WindowDragHandle(
    color: Color,
    width: Dp,
    height: Dp,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .width(width)
            .height(height)
            .clip(RoundedCornerShape(height / 2))
            .background(color.copy(alpha = 0.86f))
    )
}
