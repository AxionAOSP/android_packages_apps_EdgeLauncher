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
package com.android.edge.bar.freeform

import android.content.Context
import com.android.axion.kotlin.math.dpToPx
import com.android.edge.bar.freeform.domain.FreeformConstants

class FreeformConfig(
    context: Context,
    densityScale: Float = 0.85f,
    val isDesktopMode: Boolean = false
) {
    private val displayMetrics = context.resources.displayMetrics
    private val taskbarHeight = context.dpToPx(FreeformConstants.DESKTOP_TASKBAR_HEIGHT_DP)
    private val maxDesktopHeight = displayMetrics.heightPixels - taskbarHeight

    val width: Int = if (isDesktopMode) {
        context.dpToPx(FreeformConstants.DESKTOP_DEFAULT_WIDTH_DP)
    } else {
        context.dpToPx(FreeformConstants.DEFAULT_WIDTH_DP)
    }

    val height: Int = if (isDesktopMode) {
        val desiredHeight = context.dpToPx(FreeformConstants.DESKTOP_DEFAULT_HEIGHT_DP)
        minOf(desiredHeight, (maxDesktopHeight * 0.9f).toInt())
    } else {
        context.dpToPx(FreeformConstants.DEFAULT_HEIGHT_DP)
    }

    val densityDpi: Int = if (isDesktopMode) {
        284
    } else {
        (context.resources.displayMetrics.densityDpi * densityScale).toInt()
    }

    val titleBarHeight: Int = if (isDesktopMode) {
        context.dpToPx(FreeformConstants.DESKTOP_TITLE_BAR_HEIGHT_DP)
    } else {
        context.dpToPx(FreeformConstants.TITLE_BAR_HEIGHT_DP)
    }

    var secure: Boolean = false
    var ownContentOnly: Boolean = true
    var shouldShowSystemDecorations: Boolean = false
    var refreshRate: Float = 60f

    val hangUpWidth: Int = FreeformConstants.HANGUP_WIDTH
    val hangUpHeight: Int = FreeformConstants.HANGUP_HEIGHT

    var isHangUp: Boolean = false
    var notInHangUpX: Int = 0
    var notInHangUpY: Int = 0
}
