/*
 * Copyright (C) 2025 AxionOS 
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

import android.app.Application
import android.content.Intent
import android.content.IntentFilter
import android.os.Process
import android.os.UserHandle
import android.provider.Settings
import android.util.Log

class EdgeApplication : Application() {

    private lateinit var sidebarReceiver: SidebarReceiver

    companion object {
        private const val TAG = "EdgeApplication"
        private const val SIDELINE = "sidebar_feature_enabled"
    }

    override fun onCreate() {
        super.onCreate()
        Process.setThreadAffinity(Process.myPid(), 1)
        Log.d(TAG, "Application onCreate - userId: ${UserHandle.myUserId()}")
        
        if (UserHandle.myUserId() != 0) {
            return
        }

        sidebarReceiver = SidebarReceiver()
        val filter = IntentFilter().apply {
            addAction(SidebarReceiver.ACTION_START_SIDEBAR)
            addAction(SidebarReceiver.ACTION_STOP_SIDEBAR)
        }
        registerReceiver(sidebarReceiver, filter)

        AppHelper.bindBubbleService(this)

        if (isFeatureEnabled()) {
            Log.d(TAG, "Feature enabled, starting EdgeService")
            startEdgeService()
        }
    }

    override fun onTerminate() {
        super.onTerminate()
        if (UserHandle.myUserId() == 0) {
            try {
                unregisterReceiver(sidebarReceiver)
            } catch (e: Exception) {
                Log.e(TAG, "Error unregistering receiver", e)
            }
        }
    }

    private fun startEdgeService() {
        try {
            startService(Intent(this, EdgeService::class.java))
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start EdgeService", e)
        }
    }

    private fun isFeatureEnabled(): Boolean {
        return Settings.Secure.getInt(contentResolver, SIDELINE, 0) == 1
    }
}
