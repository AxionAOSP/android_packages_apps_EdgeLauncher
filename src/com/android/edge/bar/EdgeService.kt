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

import android.app.IActivityManager
import android.app.Service
import android.app.UserSwitchObserver
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.PixelFormat
import android.graphics.Rect
import android.os.IBinder
import android.os.Process
import android.os.ServiceManager
import android.os.UserHandle
import android.provider.Settings
import android.view.View
import android.view.WindowManager
import android.view.WindowManager.LayoutParams
import androidx.appcompat.content.res.AppCompatResources
import com.android.internal.policy.SystemBarUtils
import kotlinx.coroutines.*
import kotlin.coroutines.CoroutineContext

class EdgeService : Service(), GestureListener.Callback, CoroutineScope {

    private val serviceJob = SupervisorJob()
    override val coroutineContext: CoroutineContext
        get() = Dispatchers.Main + serviceJob

    private lateinit var windowManager: WindowManager
    private lateinit var activityManager: IActivityManager
    private lateinit var contentResolverRef: ContentResolver
    private lateinit var edgeSideBar: EdgeSideBar

    private val wmLayoutParams = LayoutParams()

    private var userId: Int = 0
    private var serviceStarted = false
    private var showSideline = false
    private var isSidebarVisible = false
    private var isSidelineVisible = false
    private var sidelinePosX = 0
    private var sidelinePosY = 0
    private var screenWidth = 0
    private var screenHeight = 0

    private var idleJob: Job? = null
    
    private val sideLineView by lazy {
        val gestureManager = MGestureManager(this@EdgeService, GestureListener(this@EdgeService))
        View(this).apply {
            background = AppCompatResources.getDrawable(this@EdgeService, R.drawable.ic_line)
            setOnTouchListener { _, event ->
                gestureManager.onTouchEvent(event)
                animate().alpha(ACTIVE_ALPHA).setDuration(FADE_DURATION).start()
                idleJob?.cancel()
                idleJob = launch {
                    delay(IDLE_TIMEOUT)
                    animate().alpha(IDLE_ALPHA).setDuration(FADE_DURATION).start()
                }
                true
            }
        }
    }

    private val userSwitchObserver = object : UserSwitchObserver() {
        override fun onUserSwitchComplete(newUserId: Int) {
            if (!showSideline) return
            if (newUserId != userId) removeView(force = true)
            else showSidelineView()
        }
    }

    private val isPortrait: Boolean
        get() = resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT

    private val offset: Int
        get() = if (isPortrait) OFFSET_PORTRAIT else OFFSET_LANDSCAPE

    companion object {
        private const val SIDELINE_WIDTH = 100
        private const val SIDELINE_MOVE_WIDTH = 200
        private const val SIDELINE_HEIGHT = 200
        private const val OFFSET_PORTRAIT = 20
        private const val OFFSET_LANDSCAPE = 0

        private const val IDLE_TIMEOUT = 3000L
        private const val ACTIVE_ALPHA = 1.0f
        private const val IDLE_ALPHA = 0.3f
        private const val FADE_DURATION = 500L

        const val SIDELINE = "sidebar_feature_enabled"
        const val SIDELINE_POSITION_X = "sideline_position_x"
        const val SIDELINE_POSITION_Y_PORTRAIT = "sideline_position_y_portrait"
        const val SIDELINE_POSITION_Y_LANDSCAPE = "sideline_position_y_landscape"
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND)
        Process.setThreadGroupAndCpuset(Process.myPid(), 9)
        Process.setProcessGroup(Process.myPid(), 9)

        userId = UserHandle.myUserId()

        if (userId != 0) {
            stopSelf()
            return START_STICKY
        }

        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        activityManager = IActivityManager.Stub.asInterface(ServiceManager.getService(Context.ACTIVITY_SERVICE))
        contentResolverRef = applicationContext.contentResolver

        screenWidth = resources.displayMetrics.widthPixels
        screenHeight = resources.displayMetrics.heightPixels

        activityManager.registerUserSwitchObserver(userSwitchObserver, "EdgeService")
        serviceStarted = true

        edgeSideBar = EdgeSideBar(this, object : EdgeSideBar.Callback {
            override fun onRemove() {
                if (isSidebarVisible && showSideline) animateShowSideline()
                isSidebarVisible = false
            }
        })

        showSideline = getSecureBoolean(SIDELINE, false)
        if (showSideline) showSidelineView()
        
        return START_STICKY
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        val newWidth = resources.displayMetrics.widthPixels
        val newHeight = resources.displayMetrics.heightPixels
        if (newWidth == screenWidth && newHeight == screenHeight) return

        screenWidth = newWidth
        screenHeight = newHeight

        if (showSideline) updateSidelinePosition()
        if (isSidebarVisible) edgeSideBar.updateSidebarPosition()
    }

    override fun onDestroy() {
        super.onDestroy()
        if (!serviceStarted) return
        activityManager.unregisterUserSwitchObserver(userSwitchObserver)
        removeView(force = true)
        serviceJob.cancel()
    }

    override fun showSidebar() {
        edgeSideBar.showPanelView()
        isSidebarVisible = true
        animateHideSideline()
    }

    override fun beginMoveSideline() {
        wmLayoutParams.width = SIDELINE_MOVE_WIDTH
        updateViewLayout()
    }

    override fun moveSideline(xChanged: Int, yChanged: Int, posX: Int, posY: Int) {
        sidelinePosX = if (posX > screenWidth / 2) 1 else -1
        wmLayoutParams.x = sidelinePosX * (screenWidth / 2 - offset)
        wmLayoutParams.y += yChanged
        updateViewLayout()
    }

    override fun endMoveSideline() {
        wmLayoutParams.width = SIDELINE_WIDTH
        wmLayoutParams.y = constrainY(wmLayoutParams.y)
        updateViewLayout()

        putSecureInt(SIDELINE_POSITION_X, sidelinePosX)
        putSecureInt(
            if (isPortrait) SIDELINE_POSITION_Y_PORTRAIT else SIDELINE_POSITION_Y_LANDSCAPE,
            wmLayoutParams.y
        )
    }

    private fun constrainY(y: Int): Int {
        val statusBarHeight = SystemBarUtils.getStatusBarHeight(this)
        val navBarHeight = if (isPortrait) {
            resources.getDimensionPixelSize(com.android.internal.R.dimen.navigation_bar_height)
        } else 0

        return y.coerceIn(
            -(screenHeight / 2 - statusBarHeight - SIDELINE_HEIGHT / 2),
            screenHeight / 2 - navBarHeight - SIDELINE_HEIGHT / 2
        )
    }

    private fun showSidelineView() {
        if (isSidelineVisible) return

        wmLayoutParams.apply {
            type = LayoutParams.TYPE_APPLICATION_OVERLAY
            width = SIDELINE_WIDTH
            height = SIDELINE_HEIGHT
            format = PixelFormat.RGBA_8888
            windowAnimations = android.R.style.Animation_Dialog
            flags = LayoutParams.FLAG_NOT_FOCUSABLE or
                    LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    LayoutParams.FLAG_HARDWARE_ACCELERATED
            privateFlags = LayoutParams.SYSTEM_FLAG_SHOW_FOR_ALL_USERS or
                    LayoutParams.PRIVATE_FLAG_TRUSTED_OVERLAY or
                    LayoutParams.PRIVATE_FLAG_SYSTEM_APPLICATION_OVERLAY
        }

        sideLineView.setSystemGestureExclusionRects(
            listOf(Rect(0, 0, SIDELINE_WIDTH, SIDELINE_HEIGHT))
        )

        updateSidelinePosition()

        launch {
            try {
                windowManager.addView(sideLineView, wmLayoutParams)
                isSidelineVisible = true
                idleJob?.cancel()
                idleJob = launch {
                    delay(IDLE_TIMEOUT)
                    sideLineView.animate().alpha(IDLE_ALPHA).setDuration(FADE_DURATION).start()
                }
            } catch (e: Exception) {
                isSidelineVisible = false
            }
        }
    }

    private fun updateSidelinePosition() {
        sidelinePosX = getSecureInt(SIDELINE_POSITION_X, 1)
        sidelinePosY = getSecureInt(
            if (isPortrait) SIDELINE_POSITION_Y_PORTRAIT else SIDELINE_POSITION_Y_LANDSCAPE,
            -screenHeight / 6
        )

        wmLayoutParams.x = sidelinePosX * (screenWidth / 2 - offset)
        wmLayoutParams.y = constrainY(sidelinePosY)

        wmLayoutParams.layoutInDisplayCutoutMode =
            if (isPortrait) LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
            else LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES

        wmLayoutParams.flags = if (isPortrait) {
            wmLayoutParams.flags and LayoutParams.FLAG_LAYOUT_IN_SCREEN.inv() or LayoutParams.FLAG_LAYOUT_NO_LIMITS
        } else {
            wmLayoutParams.flags and LayoutParams.FLAG_LAYOUT_NO_LIMITS.inv() or LayoutParams.FLAG_LAYOUT_IN_SCREEN
        }

        if (isSidelineVisible) updateViewLayout()
    }

    private fun updateViewLayout() {
        launch {
            try {
                windowManager.updateViewLayout(sideLineView, wmLayoutParams)
            } catch (_: Exception) { }
        }
    }

    private fun removeView(force: Boolean = false) {
        if (!isSidelineVisible && !force) return
        launch {
            try {
                windowManager.removeViewImmediate(sideLineView)
            } catch (_: Exception) { }
        }
        edgeSideBar.removePanelView(force)
        edgeSideBar.release()
        isSidelineVisible = false
    }

    private fun animateHideSideline() {
        sideLineView.animate()
            .translationX(sidelinePosX * SIDELINE_WIDTH.toFloat())
            .setDuration(300)
            .start()
    }

    private fun animateShowSideline() {
        sideLineView.animate().translationX(0f).setDuration(300).start()
    }

    private fun putSecureInt(key: String, value: Int) {
        Settings.Secure.putInt(contentResolverRef, key, value)
    }

    private fun getSecureInt(key: String, defaultValue: Int): Int {
        return Settings.Secure.getInt(contentResolverRef, key, defaultValue)
    }

    private fun getSecureBoolean(key: String, defaultValue: Boolean): Boolean {
        return Settings.Secure.getInt(contentResolverRef, key, if (defaultValue) 1 else 0) == 1
    }
}
