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

import android.app.ActivityOptions
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Point
import android.graphics.Rect
import android.graphics.drawable.Drawable
import android.view.WindowManager
import android.widget.Toast
import android.app.WindowConfiguration
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.Painter
import com.android.internal.util.NTAppLockerHelper

object AppHelper {

    private val iconCache = mutableMapOf<String, Painter?>()

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
                packageName = it.activityInfo.packageName
            )
        }.sortedBy { it.label.lowercase() }
    }

    fun launchApp(context: Context, packageName: String) {
        NTAppLockerHelper.init(context)
        if (NTAppLockerHelper.get().isAppLocked(packageName)) {
            Toast.makeText(
                context,
                context.getString(R.string.app_locked_message),
                Toast.LENGTH_SHORT
            ).show()
            return
        }
        val pm = context.packageManager
        val launchIntent = pm.getLaunchIntentForPackage(packageName) ?: return
        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val launchBounds = calculateFreeformBounds(context)
        val options = ActivityOptions.makeBasic().apply {
            setLaunchWindowingMode(WindowConfiguration.WINDOWING_MODE_FREEFORM)
            setLaunchBounds(launchBounds)
        }
        context.startActivity(launchIntent, options.toBundle())
    }

    private fun calculateFreeformBounds(context: Context): Rect {
        val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val screenSize = Point()
        wm.defaultDisplay.getSize(screenSize)
        val screenWidth = screenSize.x
        val screenHeight = screenSize.y

        val width = (screenWidth * 0.45).toInt()
        val height = (screenHeight * 0.45).toInt()

        val left = (screenWidth - width) / 2
        val top = (screenHeight - height) / 2

        return Rect(left, top, left + width, top + height)
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
        }!!
    }
}

data class AppInfo(
    val label: String,
    val icon: Drawable,
    val packageName: String
)
