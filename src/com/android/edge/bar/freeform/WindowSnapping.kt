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
import android.graphics.Point
import android.graphics.Rect
import android.util.DisplayMetrics
import kotlin.math.abs

class WindowSnapping(private val context: Context) {

    companion object {
        private const val SNAP_THRESHOLD = 50
        private const val EDGE_MARGIN = 8
    }

    enum class SnapPosition {
        NONE,
        TOP_LEFT, TOP_CENTER, TOP_RIGHT,
        CENTER_LEFT, CENTER_RIGHT,
        BOTTOM_LEFT, BOTTOM_CENTER, BOTTOM_RIGHT,
        EDGE_LEFT, EDGE_RIGHT, EDGE_TOP, EDGE_BOTTOM
    }

    data class SnapTarget(
        val position: SnapPosition,
        val bounds: Rect,
        val snapZone: Rect
    )

    private val displayMetrics: DisplayMetrics = context.resources.displayMetrics
    private val screenWidth = displayMetrics.widthPixels
    private val screenHeight = displayMetrics.heightPixels

    fun getSnapTargets(windowWidth: Int, windowHeight: Int): List<SnapTarget> {
        val targets = mutableListOf<SnapTarget>()
        
        val quarterWidth = screenWidth / 2
        val quarterHeight = screenHeight / 2
        
        targets.add(SnapTarget(
            SnapPosition.TOP_LEFT,
            Rect(EDGE_MARGIN, EDGE_MARGIN, quarterWidth, quarterHeight),
            Rect(0, 0, SNAP_THRESHOLD, SNAP_THRESHOLD)
        ))
        
        targets.add(SnapTarget(
            SnapPosition.TOP_RIGHT,
            Rect(screenWidth - quarterWidth, EDGE_MARGIN, screenWidth - EDGE_MARGIN, quarterHeight),
            Rect(screenWidth - SNAP_THRESHOLD, 0, screenWidth, SNAP_THRESHOLD)
        ))
        
        targets.add(SnapTarget(
            SnapPosition.BOTTOM_LEFT,
            Rect(EDGE_MARGIN, screenHeight - quarterHeight, quarterWidth, screenHeight - EDGE_MARGIN),
            Rect(0, screenHeight - SNAP_THRESHOLD, SNAP_THRESHOLD, screenHeight)
        ))
        
        targets.add(SnapTarget(
            SnapPosition.BOTTOM_RIGHT,
            Rect(screenWidth - quarterWidth, screenHeight - quarterHeight, screenWidth - EDGE_MARGIN, screenHeight - EDGE_MARGIN),
            Rect(screenWidth - SNAP_THRESHOLD, screenHeight - SNAP_THRESHOLD, screenWidth, screenHeight)
        ))
        
        val halfWidth = screenWidth / 2
        val halfHeight = screenHeight / 2
        
        targets.add(SnapTarget(
            SnapPosition.EDGE_LEFT,
            Rect(EDGE_MARGIN, EDGE_MARGIN, halfWidth, screenHeight - EDGE_MARGIN),
            Rect(0, SNAP_THRESHOLD, SNAP_THRESHOLD, screenHeight - SNAP_THRESHOLD)
        ))
        
        targets.add(SnapTarget(
            SnapPosition.EDGE_RIGHT,
            Rect(halfWidth, EDGE_MARGIN, screenWidth - EDGE_MARGIN, screenHeight - EDGE_MARGIN),
            Rect(screenWidth - SNAP_THRESHOLD, SNAP_THRESHOLD, screenWidth, screenHeight - SNAP_THRESHOLD)
        ))
        
        targets.add(SnapTarget(
            SnapPosition.EDGE_TOP,
            Rect(EDGE_MARGIN, EDGE_MARGIN, screenWidth - EDGE_MARGIN, halfHeight),
            Rect(SNAP_THRESHOLD, 0, screenWidth - SNAP_THRESHOLD, SNAP_THRESHOLD)
        ))
        
        targets.add(SnapTarget(
            SnapPosition.EDGE_BOTTOM,
            Rect(EDGE_MARGIN, halfHeight, screenWidth - EDGE_MARGIN, screenHeight - EDGE_MARGIN),
            Rect(SNAP_THRESHOLD, screenHeight - SNAP_THRESHOLD, screenWidth - SNAP_THRESHOLD, screenHeight)
        ))
        
        return targets
    }

    fun getSnapTargetAtPosition(x: Int, y: Int, windowWidth: Int, windowHeight: Int): SnapTarget? {
        val targets = getSnapTargets(windowWidth, windowHeight)

        for (target in targets) {
            if (target.position.name.contains("CORNER") || target.position.name.startsWith("TOP_") || target.position.name.startsWith("BOTTOM_")) {
                if (target.snapZone.contains(x, y)) {
                    return target
                }
            }
        }
        
        for (target in targets) {
            if (target.position.name.startsWith("EDGE_")) {
                if (target.snapZone.contains(x, y)) {
                    return target
                }
            }
        }
        
        return null
    }

    fun applyMagneticSnapping(
        currentX: Int,
        currentY: Int,
        windowWidth: Int,
        windowHeight: Int
    ): Point {
        val snapTarget = getSnapTargetAtPosition(currentX, currentY, windowWidth, windowHeight)
        
        return if (snapTarget != null) {
            Point(snapTarget.bounds.left, snapTarget.bounds.top)
        } else {
            Point(currentX, currentY)
        }
    }

    fun shouldSnap(currentX: Int, currentY: Int, targetX: Int, targetY: Int): Boolean {
        val distance = abs(currentX - targetX) + abs(currentY - targetY)
        return distance < SNAP_THRESHOLD
    }

    fun getSnapBounds(position: SnapPosition, windowWidth: Int, windowHeight: Int): Rect? {
        val targets = getSnapTargets(windowWidth, windowHeight)
        return targets.find { it.position == position }?.bounds
    }
}
