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

import android.view.Surface

interface FreeformRepository {
    
    suspend fun createDisplay(
        surface: Surface,
        width: Int,
        height: Int,
        densityDpi: Int,
        callback: FreeformCallback
    ): Int
    
    suspend fun pauseDisplay(displayId: Int): Result<Unit>

    suspend fun resumeDisplay(displayId: Int): Result<Unit>

    suspend fun launchApp(packageName: String, activityName: String, displayId: Int, userId: Int): Result<Unit>

    suspend fun moveRootTaskToDisplay(taskId: Int, displayId: Int): Result<Unit>

    suspend fun findRunningTaskId(packageName: String, activityName: String): Int

    suspend fun isTaskOnDisplay(taskId: Int, displayId: Int): Boolean

    suspend fun injectBackKey(displayId: Int): Result<Unit>

    suspend fun resizeDisplay(appToken: android.os.IBinder, width: Int, height: Int, densityDpi: Int): Result<Unit>

    suspend fun releaseDisplay(appToken: android.os.IBinder): Result<Unit>

    suspend fun getAppIcon(packageName: String): Result<android.graphics.Bitmap>

    suspend fun getAppLabel(packageName: String): Result<String>

    data class DisplayInfo(
        val appToken: android.os.IBinder,
        val displayId: Int
    )

    interface FreeformCallback {
        fun onDisplayAdded(displayId: Int)
        fun onDisplayPaused()
        fun onDisplayResumed()
        fun onDisplayStopped()
    }
}
