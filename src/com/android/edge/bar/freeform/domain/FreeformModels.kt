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
package com.android.edge.bar.freeform.domain

import com.android.edge.bar.freeform.presentation.WindowState
import kotlin.math.ceil
import kotlin.math.roundToInt

object FreeformConstants {
    const val BUBBLE_SIZE_DP = 48
    const val BUBBLE_ICON_SIZE_DP = 40
    const val SAFE_ZONE_TOP_DP = 0
    const val SAFE_ZONE_BOTTOM_DP = 48
    const val SAFE_ZONE_EDGE_DP = 8
    
    const val DESKTOP_DEFAULT_WIDTH_DP = 600
    const val DESKTOP_DEFAULT_HEIGHT_DP = 450
    const val DESKTOP_MIN_WIDTH_DP = 300 
    const val DESKTOP_MIN_HEIGHT_DP = 250
    const val DESKTOP_TITLE_BAR_HEIGHT_DP = 32
    const val DESKTOP_CORNER_RADIUS_DP = 8
    const val DESKTOP_TASKBAR_HEIGHT_DP = 56
    const val DESKTOP_STATUS_BAR_HEIGHT_DP = 32

    const val MIN_WINDOW_WIDTH_DP = 190
    const val MIN_WINDOW_HEIGHT_DP = 200f

    const val MAX_WINDOW_WIDTH_DP = 1200
    const val MAX_WINDOW_HEIGHT_DP = 1500

    const val DEFAULT_WIDTH_DP = 220
    const val DEFAULT_HEIGHT_DP = 340
    const val DEFAULT_ASPECT_RATIO = DEFAULT_WIDTH_DP.toFloat() / DEFAULT_HEIGHT_DP.toFloat()
    const val MIN_WINDOW_ASPECT_RATIO = 0.5f
    const val MAX_WINDOW_ASPECT_RATIO = 2.0f
    const val WINDOW_SCREEN_MARGIN_DP = 16

    const val REMOVE_PILL_WIDTH_DP = 140
    const val REMOVE_PILL_HEIGHT_DP = 40
    const val REMOVE_PILL_TOP_OFFSET_DP = 36
    const val REMOVE_PILL_TOLERANCE_DP = 20

    const val DELAY_SURFACE_SETTLE_MS = 200L
    const val DELAY_RESIZE_SETTLE_MS = 200L
    const val DELAY_APP_LAUNCH_VEIL_MS = 200L
    const val DELAY_VEIL_HIDE_MS = 200L
    const val DELAY_ATTACH_TIMEOUT_MS = 5000L
    
    const val CORNER_RADIUS_DP = 12
    const val TITLE_BAR_HEIGHT_DP = 28
    const val TITLE_BAR_HEIGHT_MIN_DP = 24
    const val TITLE_BAR_HEIGHT_MAX_DP = 36
    const val TITLE_BAR_ICON_SIZE_DP = 14
    const val TITLE_BAR_ICON_SIZE_MIN_DP = 11
    const val TITLE_BAR_ICON_SIZE_MAX_DP = 18
    const val TITLE_BAR_DRAG_HANDLE_HEIGHT_DP = 3
    const val TITLE_BAR_DRAG_HANDLE_WIDTH_DP = 32
    const val TITLE_BAR_DRAG_HANDLE_HEIGHT_MIN_DP = 24
    const val TITLE_BAR_DRAG_HANDLE_HEIGHT_MAX_DP = 36
    const val TITLE_BAR_BUTTON_WIDTH_MULTIPLIER = 1.24f
    const val TITLE_BAR_BUTTON_WIDTH_MIN_DP = 22
    const val TITLE_BAR_BUTTON_WIDTH_MAX_DP = 28

    const val RESIZE_HANDLE_BOTTOM_WIDTH_DP = 188
    const val RESIZE_HANDLE_BOTTOM_WIDTH_MIN_DP = 168
    const val RESIZE_HANDLE_BOTTOM_WIDTH_MAX_DP = 220
    const val RESIZE_HANDLE_BOTTOM_HEIGHT_DP = 32

    const val OVERLAY_TITLE_BAR_HEIGHT_DP = 56
    const val OVERLAY_MENU_BUTTON_SIZE_DP = 36
    const val OVERLAY_PILL_PADDING_DP = 6
    const val OVERLAY_PILL_ICON_SIZE_DP = 32

    fun createDefaultWindowState(width: Int, height: Int, x: Float = 0f, y: Float = 0f): WindowState {
        return WindowState(
            x = x,
            y = y,
            width = width,
            height = height,
            savedWidth = width,
            savedHeight = height
        )
    }
}

fun coerceFreeformWindowSize(
    width: Int,
    height: Int,
    minWidth: Int,
    minHeight: Int,
    maxWidth: Int,
    maxHeight: Int,
    minAspectRatio: Float = FreeformConstants.MIN_WINDOW_ASPECT_RATIO,
    maxAspectRatio: Float = FreeformConstants.MAX_WINDOW_ASPECT_RATIO
): Pair<Int, Int> {
    var coercedWidth = width.coerceIn(minWidth, maxWidth)
    var coercedHeight = height.coerceIn(minHeight, maxHeight)

    val aspectRatio = coercedWidth.toFloat() / coercedHeight.toFloat()
    if (aspectRatio < minAspectRatio) {
        val expandedWidth = ceil(coercedHeight * minAspectRatio).toInt()
        if (expandedWidth <= maxWidth) {
            coercedWidth = expandedWidth
        } else {
            coercedHeight = (coercedWidth / minAspectRatio).roundToInt()
        }
    } else if (aspectRatio > maxAspectRatio) {
        val expandedHeight = ceil(coercedWidth / maxAspectRatio).toInt()
        if (expandedHeight <= maxHeight) {
            coercedHeight = expandedHeight
        } else {
            coercedWidth = (coercedHeight * maxAspectRatio).roundToInt()
        }
    }

    return coercedWidth.coerceIn(minWidth, maxWidth) to coercedHeight.coerceIn(minHeight, maxHeight)
}
