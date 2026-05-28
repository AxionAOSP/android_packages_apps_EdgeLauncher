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
package com.android.edge.bar.freeform.presentation

import android.util.Log
import com.android.edge.bar.freeform.domain.FreeformConstants
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

enum class VeilReason {
    DISPLAY_CREATED,
    DISPLAY_PAUSED,
    DISPLAY_STOPPED,
    RESIZE_STARTED,
    BUBBLE_EXPAND
}

sealed class SurfaceEvent {
    data class ShowVeilRequested(val reason: VeilReason) : SurfaceEvent()
    object HideVeilRequested : SurfaceEvent()
    data class OperationStarted(val operation: String) : SurfaceEvent()
    data class OperationCompleted(val operation: String) : SurfaceEvent()
    object SurfaceReady : SurfaceEvent()
    object SurfaceUnstable : SurfaceEvent()
}

sealed class VeilState {
    object Hidden : VeilState()
    data class Showing(val reason: VeilReason) : VeilState()
    object PendingHide : VeilState()
}

class SurfaceEventsManager(
    private val scope: CoroutineScope
) {
    companion object {
        private const val TAG = "SurfaceEventsManager"
    }
    
    private val eventFlow = MutableSharedFlow<SurfaceEvent>(extraBufferCapacity = 64)
    private val _veilState = MutableStateFlow<VeilState>(VeilState.Hidden)
    val veilState: StateFlow<VeilState> = _veilState.asStateFlow()
    
    private val activeOperations = mutableSetOf<String>()
    private var hideVeilJob: Job? = null
    private var isSurfaceReady = false
    
    init {
        scope.launch {
            eventFlow.collect { event ->
                processEvent(event)
            }
        }
    }
    
    fun dispatch(event: SurfaceEvent) {
        if (!eventFlow.tryEmit(event)) {
            scope.launch {
                eventFlow.emit(event)
            }
        }
    }
    
    private suspend fun processEvent(event: SurfaceEvent) {
        Log.d(TAG, "Processing event: $event, current state: ${_veilState.value}, operations: $activeOperations, surfaceReady: $isSurfaceReady")
        
        when (event) {
            is SurfaceEvent.ShowVeilRequested -> handleShowVeil(event.reason)
            is SurfaceEvent.HideVeilRequested -> handleHideVeil()
            is SurfaceEvent.OperationStarted -> handleOperationStarted(event.operation)
            is SurfaceEvent.OperationCompleted -> handleOperationCompleted(event.operation)
            is SurfaceEvent.SurfaceReady -> handleSurfaceReady()
            is SurfaceEvent.SurfaceUnstable -> handleSurfaceUnstable()
        }
    }
    
    private fun handleShowVeil(reason: VeilReason) {
        hideVeilJob?.cancel()
        hideVeilJob = null
        
        val currentState = _veilState.value
        
        if (currentState is VeilState.Showing) {
            Log.d(TAG, "Veil already showing (current: ${currentState.reason}, requested: $reason) - ignoring")
            return
        }
        
        Log.d(TAG, "Showing veil for reason: $reason")
        _veilState.value = VeilState.Showing(reason)
        isSurfaceReady = false
    }
    
    private fun handleHideVeil() {
        val currentState = _veilState.value
        
        if (currentState is VeilState.Hidden) {
            Log.d(TAG, "Veil already hidden - ignoring hide request")
            return
        }
        
        if (activeOperations.isNotEmpty() || !isSurfaceReady) {
            Log.d(TAG, "Cannot hide veil yet - operations: $activeOperations, surfaceReady: $isSurfaceReady")
            if (currentState is VeilState.Showing) {
                _veilState.value = VeilState.PendingHide
                Log.d(TAG, "Veil state set to PendingHide")
            }
            return
        }
        
        hideVeilJob?.cancel()
        hideVeilJob = scope.launch {
            Log.d(TAG, "Scheduling veil hide after ${FreeformConstants.DELAY_VEIL_HIDE_MS}ms")
            delay(FreeformConstants.DELAY_VEIL_HIDE_MS)
            
            if (activeOperations.isEmpty() && isSurfaceReady) {
                Log.d(TAG, "Hiding veil after delay")
                _veilState.value = VeilState.Hidden
            } else {
                Log.d(TAG, "Veil hide cancelled - conditions changed (operations: $activeOperations, surfaceReady: $isSurfaceReady)")
            }
        }
    }
    
    private fun handleOperationStarted(operation: String) {
        activeOperations.add(operation)
        Log.d(TAG, "Operation started: $operation, active operations: $activeOperations")
    }
    
    private fun handleOperationCompleted(operation: String) {
        if (operation == "*") {
            Log.d(TAG, "Completing all operations")
            activeOperations.clear()
        } else {
            activeOperations.remove(operation)
            Log.d(TAG, "Operation completed: $operation, remaining: $activeOperations")
        }
        
        checkAndTriggerHide()
    }
    
    private fun handleSurfaceReady() {
        Log.d(TAG, "Surface is ready")
        isSurfaceReady = true
        
        checkAndTriggerHide()
    }
    
    private fun handleSurfaceUnstable() {
        Log.d(TAG, "Surface is unstable")
        isSurfaceReady = false
        
        hideVeilJob?.cancel()
        hideVeilJob = null
    }
    
    private fun checkAndTriggerHide() {
        val currentState = _veilState.value
        
        if (currentState is VeilState.Showing || currentState is VeilState.PendingHide) {
            if (activeOperations.isEmpty() && isSurfaceReady) {
                Log.d(TAG, "All conditions met - triggering hide")
                scope.launch {
                    handleHideVeil()
                }
            }
        }
    }
    
    fun isVeilVisible(): Boolean {
        return _veilState.value !is VeilState.Hidden
    }
}
