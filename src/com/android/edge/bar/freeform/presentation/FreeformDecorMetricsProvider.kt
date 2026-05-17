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
package com.android.edge.bar.freeform.presentation

import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.android.edge.bar.freeform.domain.FreeformConstants.DESKTOP_TITLE_BAR_HEIGHT_DP
import com.android.edge.bar.freeform.domain.FreeformConstants.RESIZE_HANDLE_BOTTOM_HEIGHT_DP
import com.android.edge.bar.freeform.domain.FreeformConstants.RESIZE_HANDLE_BOTTOM_WIDTH_MIN_DP

data class FreeformDecorMetrics(
    val sidePadding: Dp,
    val bottomPadding: Dp,
    val resizeBorderWidth: Dp,
    val titleBarHeight: Dp,
    val menuWidth: Dp,
    val bottomBarWidth: Dp,
    val bottomBarHeight: Dp
)

object FreeformDecorMetricsProvider {
    private val sidePadding = 0.dp
    private val resizeBorderWidth = 2.dp

    fun provide(windowWidthPx: Int, density: Density): FreeformDecorMetrics {
        val bottomBarHeight = RESIZE_HANDLE_BOTTOM_HEIGHT_DP.dp
        return FreeformDecorMetrics(
            sidePadding = sidePadding,
            bottomPadding = bottomBarHeight,
            resizeBorderWidth = resizeBorderWidth,
            titleBarHeight = DESKTOP_TITLE_BAR_HEIGHT_DP.dp,
            menuWidth = calculateMenuWidth(windowWidthPx, density),
            bottomBarWidth = calculateBottomBarWidth(windowWidthPx, density),
            bottomBarHeight = bottomBarHeight
        )
    }

    private fun calculateBottomBarWidth(windowWidthPx: Int, density: Density): Dp {
        return with(density) {
            windowWidthPx.toDp() - sidePadding - sidePadding
        }.coerceAtLeast(0.dp)
    }

    private fun calculateMenuWidth(windowWidthPx: Int, density: Density): Dp {
        val availableWidth = with(density) {
            windowWidthPx.toDp() - sidePadding - sidePadding
        }.coerceAtLeast(0.dp)
        return if (availableWidth < RESIZE_HANDLE_BOTTOM_WIDTH_MIN_DP.dp) {
            availableWidth
        } else {
            availableWidth.coerceIn(RESIZE_HANDLE_BOTTOM_WIDTH_MIN_DP.dp, 210.dp)
        }
    }
}
