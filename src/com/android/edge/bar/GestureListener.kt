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
package com.android.edge.bar

import android.view.MotionEvent

class GestureListener(
    private val callback: Callback
) : MGestureManager.MGestureListener {

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

    override fun onTouchEvent(event: MotionEvent) {}

    interface Callback {
        fun showSidebar()
    }
}
