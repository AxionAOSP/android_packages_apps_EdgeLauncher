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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.yield
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.ConcurrentHashMap

object AppHelper {

    private val loadLock = Any()
    private val iconLoadLocks = ConcurrentHashMap<String, Any>()
    private val iconCache = ConcurrentHashMap<String, Painter>()
    private var sBubbles: IBubbles? = null

    @Volatile
    private var cachedApps: List<AppInfo>? = null

    @Volatile
    private var preloadJob: Job? = null

    private val _version = MutableStateFlow(0)
    val version: StateFlow<Int> = _version.asStateFlow()

    fun getCachedAppsOrEmpty(): List<AppInfo> = cachedApps ?: emptyList()

    fun preloadApps(context: Context, scope: CoroutineScope): Job {
        val appContext = context.applicationContext
        preloadJob?.takeIf { it.isActive }?.let { return it }
        return scope.launch(Dispatchers.Default) {
            preloadAppIcons(appContext, loadAppsCached(appContext))
        }.also { preloadJob = it }
    }

    fun loadAppsCached(context: Context): List<AppInfo> {
        cachedApps?.let { return it }
        val appContext = context.applicationContext
        return synchronized(loadLock) {
            cachedApps
                ?: getInstalledApps(appContext).also { loaded ->
                    cachedApps = loaded
                    notifyAppsChanged()
                }
        }
    }

    fun invalidateCache() {
        synchronized(loadLock) {
            preloadJob?.cancel()
            preloadJob = null
            cachedApps = null
            iconCache.clear()
            iconLoadLocks.clear()
            notifyAppsChanged()
        }
    }

    private fun notifyAppsChanged() {
        _version.value = _version.value + 1
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

    fun getCachedAppPainter(appInfo: AppInfo): Painter? = iconCache[appInfo.iconKey]

    fun getAppPainter(context: Context, appInfo: AppInfo): Painter {
        return getOrCreateAppPainter(
            context = context,
            cacheKey = appInfo.iconKey,
            icon = loadActivityIcon(context, appInfo)
        )
    }

    private suspend fun preloadAppIcons(context: Context, apps: List<AppInfo>) {
        var loadedAny = false
        apps.forEachIndexed { index, app ->
            if (getCachedAppPainter(app) == null) {
                getAppPainter(context, app)
                loadedAny = true
            }
            if (index % ICON_PRELOAD_YIELD_INTERVAL == 0) {
                yield()
            }
        }
        if (loadedAny) notifyAppsChanged()
    }

    private fun getOrCreateAppPainter(
        context: Context,
        cacheKey: String,
        icon: Drawable?
    ): Painter {
        iconCache[cacheKey]?.let { return it }

        val lock = iconLoadLocks.getOrPut(cacheKey) { Any() }
        return synchronized(lock) {
            iconCache[cacheKey] ?: createIconPainter(context, icon).also {
                iconCache[cacheKey] = it
                iconLoadLocks.remove(cacheKey)
            }
        }
    }

    private fun loadActivityIcon(context: Context, appInfo: AppInfo): Drawable? {
        return try {
            context.packageManager.getActivityIcon(
                ComponentName(appInfo.packageName, appInfo.activityName)
            )
        } catch (e: Exception) {
            null
        }
    }

    private fun createIconPainter(context: Context, icon: Drawable?): Painter {
        val drawable = icon?.constantState?.newDrawable(context.resources)?.mutate()
            ?: icon?.mutate()
            ?: context.packageManager.getDefaultActivityIcon()
        return try {
            val bitmap = Bitmap.createBitmap(
                ICON_BITMAP_SIZE_PX,
                ICON_BITMAP_SIZE_PX,
                Bitmap.Config.ARGB_8888
            )
            val canvas = Canvas(bitmap)
            drawable.setBounds(0, 0, ICON_BITMAP_SIZE_PX, ICON_BITMAP_SIZE_PX)
            drawable.draw(canvas)
            BitmapPainter(bitmap.asImageBitmap())
        } catch (e: Exception) {
            val fallbackBitmap = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
            BitmapPainter(fallbackBitmap.asImageBitmap())
        }
    }

    private const val ICON_BITMAP_SIZE_PX = 96
    private const val ICON_PRELOAD_YIELD_INTERVAL = 8
}

data class AppInfo(
    val label: String,
    val packageName: String,
    val activityName: String = ""
) {
    val iconKey: String
        get() = if (activityName.isNotBlank()) {
            "$packageName/$activityName"
        } else {
            packageName
        }
}
