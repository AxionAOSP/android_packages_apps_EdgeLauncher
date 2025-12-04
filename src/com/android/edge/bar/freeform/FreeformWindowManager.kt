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
import android.util.Log
import java.util.concurrent.ConcurrentHashMap

class FreeformWindowManager private constructor(private val context: Context) {

    companion object {
        private const val TAG = "FreeformWindowManager"
        
        @Volatile
        private var instance: FreeformWindowManager? = null
        
        fun getInstance(context: Context): FreeformWindowManager {
            return instance ?: synchronized(this) {
                instance ?: FreeformWindowManager(context.applicationContext).also { instance = it }
            }
        }
    }

    private val windows = ConcurrentHashMap<String, FreeformWindowCompose>()
    private var focusedWindow: FreeformWindowCompose? = null
    private val listeners = mutableListOf<WindowEventListener>()
    private val bubbleSlots = ConcurrentHashMap<String, Int>()
    private var removeZoneOverlay: RemoveZoneOverlay? = null
    
    fun getRemoveZoneOverlay(): RemoveZoneOverlay {
        if (removeZoneOverlay == null) {
            removeZoneOverlay = RemoveZoneOverlay(context)
        }
        return removeZoneOverlay!!
    }
    
    fun showRemoveZone() {
        getRemoveZoneOverlay().show()
    }
    
    fun hideRemoveZone() {
        getRemoveZoneOverlay().hide()
    }
    
    fun setRemoveZoneHovering(hovering: Boolean) {
        removeZoneOverlay?.setHovering(hovering)
    }
    
    fun isInRemoveZone(x: Float, y: Float): Boolean {
        return removeZoneOverlay?.isInRemoveZone(x, y) ?: false
    }

    interface WindowEventListener {
        fun onWindowAdded(packageName: String, window: FreeformWindowCompose) {}
        fun onWindowRemoved(packageName: String) {}
        fun onWindowFocusChanged(packageName: String?, hasFocus: Boolean) {}
    }

    fun registerWindow(packageName: String, window: FreeformWindowCompose) {
        windows[packageName] = window
        Log.d(TAG, "Registered window for $packageName. Total windows: ${windows.size}")
        
        listeners.forEach { it.onWindowAdded(packageName, window) }
    }

    fun unregisterWindow(packageName: String) {
        windows.remove(packageName)?.let {
            if (focusedWindow == it) {
                focusedWindow = null
            }
            Log.d(TAG, "Unregistered window for $packageName. Remaining windows: ${windows.size}")
            listeners.forEach { listener -> listener.onWindowRemoved(packageName) }
        }
    }

    fun getWindow(packageName: String): FreeformWindowCompose? {
        return windows[packageName]
    }

    fun getAllWindows(): Map<String, FreeformWindowCompose> {
        return windows.toMap()
    }

    fun getWindowCount(): Int {
        return windows.size
    }

    fun hasWindow(packageName: String): Boolean {
        return windows.containsKey(packageName)
    }

    fun setFocusedWindow(packageName: String?) {
        val newFocusedWindow = packageName?.let { windows[it] }
        
        if (focusedWindow != newFocusedWindow) {
            focusedWindow?.let {
                // todo: focused based back gesture intercept
            }
            
            focusedWindow = newFocusedWindow
            
            listeners.forEach { 
                it.onWindowFocusChanged(packageName, newFocusedWindow != null) 
            }
            
            Log.d(TAG, "Focus changed to: $packageName")
        }
    }

    fun getFocusedWindow(): FreeformWindowCompose? {
        return focusedWindow
    }

    fun bringToFront(packageName: String) {
        windows[packageName]?.let { window ->
            window.bringToFront()
            setFocusedWindow(packageName)
        }
    }
    
    fun onWindowLostFocus(packageName: String) {
        if (focusedWindow == windows[packageName]) {
            focusedWindow = null
            listeners.forEach { it.onWindowFocusChanged(null, false) }
            Log.d(TAG, "Window lost focus: $packageName")
        }
    }

    fun closeWindow(packageName: String) {
        windows[packageName]?.let { window ->
            window.destroy("Closed by window manager")
            unregisterWindow(packageName)
        }
    }

    fun closeAllWindows() {
        val packagesToClose = windows.keys.toList()
        packagesToClose.forEach { closeWindow(it) }
    }

    fun addListener(listener: WindowEventListener) {
        listeners.add(listener)
    }

    fun removeListener(listener: WindowEventListener) {
        listeners.remove(listener)
    }

    fun getWindowPackages(): List<String> {
        return windows.keys.toList()
    }
    
    fun getNextBubbleSlot(packageName: String): Int {
        bubbleSlots[packageName]?.let { return it }
        
        val usedSlots = bubbleSlots.values.toSet()
        var nextSlot = 0
        while (usedSlots.contains(nextSlot)) {
            nextSlot++
        }
        
        bubbleSlots[packageName] = nextSlot
        Log.d(TAG, "Assigned bubble slot $nextSlot to $packageName")
        return nextSlot
    }

    fun releaseBubbleSlot(packageName: String) {
        bubbleSlots.remove(packageName)?.let {
            Log.d(TAG, "Released bubble slot $it from $packageName")
        }
    }

    fun getBubbleSlot(packageName: String): Int? {
        return bubbleSlots[packageName]
    }
}
