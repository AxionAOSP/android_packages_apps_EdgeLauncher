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

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class SidebarReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "SidebarReceiver"
        const val ACTION_START_SIDEBAR = "com.android.edge.bar.ACTION_START_SIDEBAR"
        const val ACTION_STOP_SIDEBAR  = "com.android.edge.bar.ACTION_STOP_SIDEBAR"
    }

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_MY_PACKAGE_REPLACED,
            ACTION_START_SIDEBAR -> {
                Log.d(TAG, "Broadcast received: start service")
                context.startService(Intent(context, EdgeService::class.java))
            }
            ACTION_STOP_SIDEBAR -> {
                Log.d(TAG, "Broadcast received: stop service")
                context.stopService(Intent(context, EdgeService::class.java))
            }
        }
    }
}
