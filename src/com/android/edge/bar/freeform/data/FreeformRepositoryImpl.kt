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
package com.android.edge.bar.freeform.data

import android.app.ActivityManager
import android.app.ActivityOptions
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.SurfaceTexture
import android.hardware.display.DisplayManager
import android.os.IBinder
import android.os.ServiceManager
import android.util.Log
import android.view.Display
import android.view.Surface
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import android.app.IFreeformOverlayManager
import android.app.IFreeformDisplayCallback

class FreeformRepositoryImpl(
    private val context: Context
) : FreeformRepository {
    
    companion object {
        private const val TAG = "FreeformRepository"
    }
    
    private val activityManager: ActivityManager by lazy {
        context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    }
    
    private val displayManager: DisplayManager by lazy {
        context.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
    }
    
    private var service: IFreeformOverlayManager? = null
    
    private var currentAppToken: IBinder? = null
    
    fun getAppToken(): IBinder? = currentAppToken
    
    init {
        val binder = ServiceManager.getService("freeform_overlay")
        if (binder != null) {
            service = IFreeformOverlayManager.Stub.asInterface(binder)
            Log.d(TAG, "FreeformOverlayManagerService connected")
        } else {
            Log.e(TAG, "FreeformOverlayManagerService not found")
        }
    }
    
    override suspend fun createDisplay(
        surface: Surface,
        width: Int,
        height: Int,
        densityDpi: Int,
        callback: FreeformRepository.FreeformCallback
    ): Int = withContext(Dispatchers.IO) {
        try {
            var dpId = Display.DEFAULT_DISPLAY
            val wrappedCallback = object : IFreeformDisplayCallback.Stub() {
                override fun onDisplayAdd(displayId: Int) {
                    currentAppToken = this.asBinder()
                    callback.onDisplayAdded(displayId)
                    dpId = displayId
                }
                override fun onDisplayPaused() {
                    callback.onDisplayPaused()
                }
                override fun onDisplayResumed() {
                    callback.onDisplayResumed()
                }
                override fun onDisplayStopped() {
                    callback.onDisplayStopped()
                }
            }
            
            service?.createFreeform(
                "freeform_${width}x${height}",
                wrappedCallback,
                width,
                height,
                densityDpi,
                false,
                false,
                false,
                surface,
                getRefreshRate(dpId),
                0L
            )
            
            Log.d(TAG, "Freeform display creation initiated...")
            0
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create display", e)
            -1
        }
    }
    
    override suspend fun pauseDisplay(displayId: Int): Result<Unit> = withContext(Dispatchers.IO) {
        return@withContext try {
            service?.pauseDisplay(displayId)
            Log.d(TAG, "Paused display: $displayId")
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    override suspend fun resumeDisplay(displayId: Int): Result<Unit> = withContext(Dispatchers.IO) {
        return@withContext try {
            service?.resumeDisplay(displayId)
            Log.d(TAG, "Resumed display: $displayId")
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    override suspend fun launchApp(
        packageName: String,
        activityName: String,
        displayId: Int,
        userId: Int
    ): Result<Unit> = withContext(Dispatchers.IO) {
        return@withContext try {
            service?.launchAppOnDisplay(packageName, activityName, displayId, userId)
            Log.i(TAG, "Launched $packageName on display $displayId")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to launch app", e)
            Result.failure(e)
        }
    }
    
    override suspend fun resizeDisplay(
        appToken: android.os.IBinder,
        width: Int,
        height: Int,
        densityDpi: Int
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            service?.resizeFreeform(appToken, width, height, densityDpi)
            Log.d(TAG, "Display resized: ${width}x${height} @ ${densityDpi}dpi")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to resize display", e)
            Result.failure(e)
        }
    }
    
    override suspend fun releaseDisplay(appToken: android.os.IBinder): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            service?.releaseFreeform(appToken)
            Log.d(TAG, "Display released successfully")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to release display", e)
            Result.failure(e)
        }
    }
    
    override suspend fun getAppIcon(packageName: String): Result<Bitmap> = withContext(Dispatchers.IO) {
        runCatching {
            context.packageManager.getApplicationIcon(packageName).toBitmap()
        }
    }
    

    private fun getRefreshRate(id: Int): Float {
        val display = displayManager.getDisplay(id)
        return display?.supportedModes
            ?.maxByOrNull { it.refreshRate }
            ?.refreshRate ?: display?.refreshRate ?: 60f
    }
}
