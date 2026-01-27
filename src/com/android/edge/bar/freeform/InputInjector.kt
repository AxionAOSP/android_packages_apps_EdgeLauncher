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
import android.hardware.input.IInputManager
import android.hardware.input.InputManager
import android.os.ServiceManager
import android.os.SystemClock
import android.util.Log
import android.view.*

class InputInjector(private val context: Context) {

    private val inputManager: InputManager by lazy {
        context.getSystemService(Context.INPUT_SERVICE) as InputManager
    }

    private val inputManagerService: IInputManager by lazy {
        IInputManager.Stub.asInterface(
            ServiceManager.getService(Context.INPUT_SERVICE)
        )
    }

    var isFocused: Boolean = false
        private set
    
    private var displayId: Int = -1

    fun setDisplayId(displayId: Int) {
        this.displayId = displayId
    }
    
    fun setFocused(focused: Boolean) {
        isFocused = focused
    }

    private var currentDownTime: Long = 0L
    private var scaleX: Float = 1.0f
    private var scaleY: Float = 1.0f
    private var velocityTracker: VelocityTracker? = null
    private var lastX: Float = 0f
    private var lastY: Float = 0f
    
    private val scrollFriction = ViewConfiguration.getScrollFriction()
    private val frictionDampingFactor = (scrollFriction * 20f).coerceIn(0.1f, 0.5f)

    companion object {
        private const val TAG = "InputInjector"
        private const val INJECT_INPUT_EVENT_MODE_ASYNC = 0
        private const val INJECT_INPUT_EVENT_MODE_WAIT_FOR_RESULT = 1
        private const val INJECT_INPUT_EVENT_MODE_WAIT_FOR_FINISH = 2
        
        private const val DEFAULT_SWIPE_DURATION = 300
        private const val FRICTION_VELOCITY_THRESHOLD = 500f
    }
    
    fun setScale(viewWidth: Int, viewHeight: Int, displayWidth: Int, displayHeight: Int) {
        this.scaleX = displayWidth.toFloat() / viewWidth
        this.scaleY = displayHeight.toFloat() / viewHeight
    }

    fun injectTouchEvent(event: MotionEvent): Boolean {
        if (displayId < 0) {
            Log.w(TAG, "Display ID not set, cannot inject touch event")
            return false
        }
        
        val action = event.actionMasked
        
        when (action) {
            MotionEvent.ACTION_DOWN -> {
                currentDownTime = event.downTime
                lastX = event.x
                lastY = event.y
                velocityTracker?.recycle()
                velocityTracker = VelocityTracker.obtain()
                velocityTracker?.addMovement(event)
            }
            MotionEvent.ACTION_MOVE -> {
                velocityTracker?.addMovement(event)
                lastX = event.x
                lastY = event.y
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                velocityTracker?.addMovement(event)
                velocityTracker?.computeCurrentVelocity(1000)
                
                val velocityX = velocityTracker?.xVelocity ?: 0f
                val velocityY = velocityTracker?.yVelocity ?: 0f
                val totalVelocity = kotlin.math.sqrt(velocityX * velocityX + velocityY * velocityY)
                
                velocityTracker?.recycle()
                velocityTracker = null
            }
        }
        
        return injectScaledEvent(event)
    }
    
    private fun actionToString(action: Int): String {
        return when (action and MotionEvent.ACTION_MASK) {
            MotionEvent.ACTION_DOWN -> "DOWN"
            MotionEvent.ACTION_UP -> "UP"
            MotionEvent.ACTION_MOVE -> "MOVE"
            MotionEvent.ACTION_CANCEL -> "CANCEL"
            MotionEvent.ACTION_POINTER_DOWN -> "POINTER_DOWN"
            MotionEvent.ACTION_POINTER_UP -> "POINTER_UP"
            else -> "UNKNOWN($action)"
        }
    }

    private fun injectScaledEvent(event: MotionEvent): Boolean {
        val actionStr = actionToString(event.action)
        val historySize = event.historySize

        val pointerCoords = arrayOfNulls<MotionEvent.PointerCoords>(event.pointerCount)
        val pointerProperties = arrayOfNulls<MotionEvent.PointerProperties>(event.pointerCount)
        
        velocityTracker?.computeCurrentVelocity(1000)
        val currentVelocity = velocityTracker?.let {
            kotlin.math.sqrt(it.xVelocity * it.xVelocity + it.yVelocity * it.yVelocity)
        } ?: 0f
        
        val dampingMultiplier = if (currentVelocity > FRICTION_VELOCITY_THRESHOLD) {
            1.0f - (frictionDampingFactor * (currentVelocity - FRICTION_VELOCITY_THRESHOLD) / currentVelocity).coerceIn(0f, 0.5f)
        } else {
            1.0f
        }
        
        for (i in 0 until event.pointerCount) {
            val oldCoords = MotionEvent.PointerCoords()
            val pointerProperty = MotionEvent.PointerProperties()
            event.getPointerCoords(i, oldCoords)
            event.getPointerProperties(i, pointerProperty)
            
            pointerCoords[i] = MotionEvent.PointerCoords().apply {
                copyFrom(oldCoords)
                if (event.actionMasked == MotionEvent.ACTION_MOVE && dampingMultiplier < 1.0f) {
                    val deltaX = (oldCoords.x - lastX) * dampingMultiplier
                    val deltaY = (oldCoords.y - lastY) * dampingMultiplier
                    x = (lastX + deltaX) * scaleX
                    y = (lastY + deltaY) * scaleY
                } else {
                    x = oldCoords.x * scaleX
                    y = oldCoords.y * scaleY
                }
            }
            pointerProperties[i] = pointerProperty
        }
        
        val scaledX = pointerCoords[0]?.x ?: 0f
        val scaledY = pointerCoords[0]?.y ?: 0f
        
        val newEvent = MotionEvent.obtain(
            if (currentDownTime > 0) currentDownTime else event.downTime,
            event.eventTime,
            event.action,
            event.pointerCount,
            pointerProperties,
            pointerCoords,
            event.metaState,
            event.buttonState,
            event.xPrecision,
            event.yPrecision,
            event.deviceId,
            event.edgeFlags,
            event.source,
            event.flags
        )

        for (h in 0 until historySize) {
            val historyTime = event.getHistoricalEventTime(h)
            val allHistCoords = Array(event.pointerCount) { p ->
                MotionEvent.PointerCoords().apply {
                    event.getHistoricalPointerCoords(p, h, this)
                    x *= scaleX
                    y *= scaleY
                }
            }
            newEvent.addBatch(historyTime, allHistCoords, event.metaState)
        }
        
        newEvent.setDisplayId(displayId)
        
        val result = injectInputEvent(newEvent)
        
        newEvent.recycle()
        
        return result
    }

    fun injectMotionEvent(action: Int, x: Float, y: Float) {
        if (displayId < 0) {
            Log.w(TAG, "Display ID not set, cannot inject motion event")
            return
        }
        
        val now = SystemClock.uptimeMillis()
        
        val maskedAction = action and MotionEvent.ACTION_MASK
        
        when (maskedAction) {
            MotionEvent.ACTION_DOWN -> {
                currentDownTime = now
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
            }
        }
        
        val pressure = when (maskedAction) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> 1.0f
            else -> 0.0f
        }
        
        val downTime = if (currentDownTime > 0) currentDownTime else now
        
        injectMotionEventInternal(
            InputDevice.SOURCE_TOUCHSCREEN,
            maskedAction, downTime, now, x, y, pressure
        )
    }

    fun injectKeyEvent(keyCode: Int, longPress: Boolean = false) {
        if (displayId < 0) {
            Log.w(TAG, "Display ID not set, cannot inject key event")
            return
        }
        
        if (displayId == Display.DEFAULT_DISPLAY) {
            Log.w(TAG, "Attempted to inject key event to default display, aborting to prevent leaks")
            return
        }
        
        val now = SystemClock.uptimeMillis()
        
        val flags = KeyEvent.FLAG_FROM_SYSTEM or KeyEvent.FLAG_VIRTUAL_HARD_KEY
        
        val downEvent = KeyEvent(
            now, now, KeyEvent.ACTION_DOWN, keyCode, 0, 0,
            KeyCharacterMap.VIRTUAL_KEYBOARD, 0, 
            flags,
            InputDevice.SOURCE_KEYBOARD
        ).apply {
            setDisplayId(displayId)
        }
        injectInputEvent(downEvent)
        
        if (longPress) {
            val repeatEvent = KeyEvent(
                now, now, KeyEvent.ACTION_DOWN, keyCode, 1, 0,
                KeyCharacterMap.VIRTUAL_KEYBOARD, 0,
                flags or KeyEvent.FLAG_LONG_PRESS,
                InputDevice.SOURCE_KEYBOARD
            ).apply {
                setDisplayId(displayId)
            }
            injectInputEvent(repeatEvent)
        }
        
        val upEvent = KeyEvent(
            now, now, KeyEvent.ACTION_UP, keyCode, 0, 0,
            KeyCharacterMap.VIRTUAL_KEYBOARD, 0, 
            flags,
            InputDevice.SOURCE_KEYBOARD
        ).apply {
            setDisplayId(displayId)
        }
        injectInputEvent(upEvent)
    }
    
    fun injectBackButton(): Boolean {
        if (displayId < 0) {
            Log.w(TAG, "Display ID not set, cannot inject back button")
            return false
        }
        
        if (displayId == Display.DEFAULT_DISPLAY) {
            Log.w(TAG, "Attempted to inject back to default display, aborting")
            return false
        }
        
        val now = SystemClock.uptimeMillis()
        
        val downEvent = KeyEvent(
            now, now,
            KeyEvent.ACTION_DOWN,
            KeyEvent.KEYCODE_BACK,
            0
        ).apply {
            source = InputDevice.SOURCE_KEYBOARD
            setDisplayId(displayId)
        }
        
        val upEvent = KeyEvent(
            now, now,
            KeyEvent.ACTION_UP,
            KeyEvent.KEYCODE_BACK,
            0
        ).apply {
            source = InputDevice.SOURCE_KEYBOARD
            setDisplayId(displayId)
        }
        
        return try {
            inputManagerService.injectInputEvent(downEvent, INJECT_INPUT_EVENT_MODE_ASYNC)
            inputManagerService.injectInputEvent(upEvent, INJECT_INPUT_EVENT_MODE_ASYNC)
            Log.d(TAG, "Injected back button via IInputManager to display $displayId")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to inject back button via IInputManager", e)
            false
        }
    }

    private fun injectMotionEvent(
        inputSource: Int,
        action: Int,
        when_: Long,
        x: Float,
        y: Float,
        pressure: Float
    ) {
        injectMotionEventInternal(inputSource, action, when_, when_, x, y, pressure)
    }

    private fun injectMotionEventInternal(
        inputSource: Int,
        action: Int,
        downTime: Long,
        eventTime: Long,
        x: Float,
        y: Float,
        pressure: Float
    ) {
        val event = MotionEvent.obtain(
            downTime, eventTime, action, x, y, pressure,
            1.0f, // size
            0, // metaState
            1.0f, // xPrecision
            1.0f, // yPrecision
            0, // deviceId
            0 // edgeFlags
        ).apply {
            source = inputSource
            setDisplayId(displayId)
        }
        
        injectInputEvent(event)
        event.recycle()
    }

    private fun injectInputEvent(event: InputEvent): Boolean {
        return try {
            val mode = if (event is MotionEvent) {
                INJECT_INPUT_EVENT_MODE_ASYNC
            } else {
                INJECT_INPUT_EVENT_MODE_WAIT_FOR_FINISH
            }
            inputManager.injectInputEvent(event, mode)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to inject input event", e)
            false
        }
    }
}
