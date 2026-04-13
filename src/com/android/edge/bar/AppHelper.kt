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

import java.util.concurrent.ConcurrentHashMap
import android.app.ActivityManager
import android.app.FreeformLauncher
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.os.IBinder
import android.os.Process
import android.util.Log
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.Painter
import com.android.wm.shell.bubbles.IBubbles
import com.android.wm.shell.shared.bubbles.BubbleAnythingFlagHelper
import com.android.wm.shell.shared.bubbles.logging.EntryPoint

object AppHelper {

    private val iconCache = ConcurrentHashMap<String, Painter>()
    private var sBubbles: IBubbles? = null

    @Volatile
    private var cachedApps: List<AppInfo>? = null

    fun getCachedAppsOrEmpty(): List<AppInfo> = cachedApps ?: emptyList()

    fun loadAppsCached(context: Context): List<AppInfo> {
        cachedApps?.let { return it }
        val loaded = getInstalledApps(context)
        loaded.forEach { getAppPainter(context, it.packageName, it.icon) }
        cachedApps = loaded
        return loaded
    }

    fun bindBubbleService(context: Context) {
        if (!isBubbleSupported()) return
        val intent = Intent("com.android.systemui.action.BUBBLE_LAUNCHER")
            .setPackage("com.android.systemui")
        context.bindService(intent, object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName, service: IBinder) {
                sBubbles = IBubbles.Stub.asInterface(service)
            }
            override fun onServiceDisconnected(name: ComponentName) {
                sBubbles = null
            }
        }, Context.BIND_AUTO_CREATE)
    }

    fun getInstalledApps(context: Context): List<AppInfo> {
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN, null).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }

        val resolveInfos = pm.queryIntentActivities(intent, 0)
        return resolveInfos.map {
            AppInfo(
                label = it.loadLabel(pm).toString(),
                icon = it.activityInfo.loadIcon(pm),
                packageName = it.activityInfo.packageName,
                activityName = it.activityInfo.name
            )
        }.sortedBy { it.label.lowercase() }
    }

    fun launchApp(packageName: String) {
        FreeformLauncher.launch(packageName)
    }

    fun launchAppFull(context: Context, packageName: String) {
        try {
            val launchIntent = context.packageManager.getLaunchIntentForPackage(packageName)
            if (launchIntent != null) {
                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(launchIntent)
            } else {
                Log.e("AppHelper", "Unable to find launch intent for $packageName")
            }
        } catch (e: Exception) {
            Log.e("AppHelper", "Failed to launch app: ${e.message}")
        }
    }
    
    fun isBubbleSupported(): Boolean = BubbleAnythingFlagHelper.enableCreateAnyBubble()

    fun launchAsBubble(context: Context, packageName: String, activityName: String) {
        try {
            val bubbles = sBubbles ?: run {
                Log.e("AppHelper", "Bubble service not connected")
                return
            }
            val intent = Intent().apply {
                setClassName(packageName, activityName)
                setPackage(packageName)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            bubbles.showAppBubble(intent, Process.myUserHandle(), EntryPoint.LAUNCHER_ICON_MENU, null)
        } catch (e: Exception) {
            Log.e("AppHelper", "Failed to launch as bubble: ${e.message}")
        }
    }

    fun killApp(context: Context, packageName: String) {
        try {
            val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            am.forceStopPackage(packageName)
            Log.i("AppHelper", "Force stopped: $packageName")
        } catch (e: Exception) {
            Log.e("AppHelper", "Failed to kill app: ${e.message}")
        }
    }

    fun getAppPainter(context: Context, packageName: String, icon: Drawable?): Painter {
        return iconCache.getOrPut(packageName) {
            val drawable = icon ?: context.packageManager.getDefaultActivityIcon()
            try {
                val size = 96
                val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(bitmap)
                drawable.setBounds(0, 0, size, size)
                drawable.draw(canvas)
                BitmapPainter(bitmap.asImageBitmap())
            } catch (e: Exception) {
                val fallbackBitmap = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
                BitmapPainter(fallbackBitmap.asImageBitmap())
            }
        }
    }
}

data class AppInfo(
    val label: String,
    val icon: Drawable,
    val packageName: String,
    val activityName: String = ""
)