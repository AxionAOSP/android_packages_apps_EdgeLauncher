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
package com.android.edge.bar

import android.view.MotionEvent
import kotlinx.coroutines.*

class GestureListener(
    private val callback: Callback,
    private val coroutineScope: CoroutineScope = CoroutineScope(Dispatchers.Main.immediate)
) : MGestureManager.MGestureListener {

    private var initialX = 0f
    private var initialY = 0f
    private var longPressActive = false
    private var longPressJob: Job? = null

    private val longPressDelay = 500L

    companion object {
        private const val TAG = "GestureListener"
    }

    override fun singleFingerSlipAction(
        gestureEvent: MGestureManager.GestureEvent,
        startEvent: MotionEvent,
        endEvent: MotionEvent,
        velocity: Float
    ): Boolean {
        return when (gestureEvent) {
            MGestureManager.GestureEvent.SINGLE_FINGER_LEFT_SLIP,
            MGestureManager.GestureEvent.SINGLE_FINGER_RIGHT_SLIP -> {
                callback.showSidebar()
                true
            }
            else -> false
        }
    }

    override fun onTouchEvent(event: MotionEvent) {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                initialX = event.rawX
                initialY = event.rawY
                longPressActive = false

                longPressJob?.cancel()
                longPressJob = coroutineScope.launch {
                    delay(longPressDelay)
                    longPressActive = true
                    callback.beginMoveSideline()
                }
            }

            MotionEvent.ACTION_MOVE -> {
                if (longPressActive) {
                    val dx = (event.rawX - initialX).toInt()
                    val dy = (event.rawY - initialY).toInt()
                    callback.moveSideline(dx, dy, event.rawX.toInt(), event.rawY.toInt())
                    initialX = event.rawX
                    initialY = event.rawY
                }
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                longPressJob?.cancel()
                if (longPressActive) {
                    callback.endMoveSideline()
                }
                longPressActive = false
            }
        }
    }

    interface Callback {
        fun showSidebar()
        fun beginMoveSideline()
        fun moveSideline(xChanged: Int, yChanged: Int, touchX: Int, touchY: Int)
        fun endMoveSideline()
    }
}
