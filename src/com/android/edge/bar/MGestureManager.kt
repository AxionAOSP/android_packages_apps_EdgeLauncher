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

import android.content.Context
import android.view.GestureDetector
import android.view.MotionEvent
import kotlin.math.abs

class MGestureManager(
    context: Context,
    private val listener: MGestureListener
) {

    private val gestureDetector: GestureDetector
    private val minVelocityThreshold = 50f

    companion object {
        private const val TAG = "MGestureManager"
    }

    interface MGestureListener {
        fun singleFingerSlipAction(
            gestureEvent: GestureEvent,
            startEvent: MotionEvent,
            endEvent: MotionEvent,
            velocity: Float
        ): Boolean

        fun onTouchEvent(event: MotionEvent)
    }

    enum class GestureEvent {
        SINGLE_FINGER_LEFT_SLIP,
        SINGLE_FINGER_RIGHT_SLIP,
        SINGLE_FINGER_UP_SLIP,
        SINGLE_FINGER_DOWN_SLIP
    }

    fun onTouchEvent(event: MotionEvent): Boolean {
        listener.onTouchEvent(event)
        return gestureDetector.onTouchEvent(event)
    }

    private inner class GestureListenerImpl : GestureDetector.SimpleOnGestureListener() {
        override fun onFling(
            e1: MotionEvent?,
            e2: MotionEvent,
            velocityX: Float,
            velocityY: Float
        ): Boolean {
            if (e1 == null) return false

            val deltaX = e2.x - e1.x
            val deltaY = e2.y - e1.y

            return when {
                isHorizontalSwipe(deltaX, deltaY, velocityX) ->
                    listener.singleFingerSlipAction(
                        if (deltaX > 0) GestureEvent.SINGLE_FINGER_RIGHT_SLIP
                        else GestureEvent.SINGLE_FINGER_LEFT_SLIP,
                        e1, e2, abs(velocityX)
                    )

                isVerticalSwipe(deltaX, deltaY, velocityY) ->
                    listener.singleFingerSlipAction(
                        if (deltaY > 0) GestureEvent.SINGLE_FINGER_DOWN_SLIP
                        else GestureEvent.SINGLE_FINGER_UP_SLIP,
                        e1, e2, abs(velocityY)
                    )

                else -> false
            }
        }

        private fun isHorizontalSwipe(dx: Float, dy: Float, velocityX: Float): Boolean {
            return abs(dx) > abs(dy) && abs(velocityX) > minVelocityThreshold
        }

        private fun isVerticalSwipe(dx: Float, dy: Float, velocityY: Float): Boolean {
            return abs(dy) > abs(dx) && abs(velocityY) > minVelocityThreshold
        }
    }

    init {
        gestureDetector = GestureDetector(context, GestureListenerImpl())
    }
}
