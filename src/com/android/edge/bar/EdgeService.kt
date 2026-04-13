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

import com.android.edge.bar.freeform.FreeformWindowCompose
import com.android.edge.bar.freeform.FreeformWindowManager
import com.android.edge.bar.freeform.FreeformTaskStackListener

import android.app.IActivityManager
import android.app.Service
import android.app.UserSwitchObserver
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.database.ContentObserver
import android.graphics.PixelFormat
import android.graphics.Rect
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Process
import android.os.ServiceManager
import android.os.UserHandle
import android.provider.Settings
import android.view.Display
import android.view.View
import android.view.WindowManager
import android.view.WindowManager.LayoutParams
import androidx.appcompat.content.res.AppCompatResources
import com.android.axion.compose.preferences.SettingsFlow
import com.android.axion.compose.preferences.SettingsType
import com.android.internal.policy.SystemBarUtils
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlin.coroutines.CoroutineContext

class EdgeService : Service(), GestureListener.Callback, CoroutineScope {


    private val serviceJob = SupervisorJob()
    private val serviceDispatcher = Dispatchers.Default.limitedParallelism(2)
    override val coroutineContext: CoroutineContext
        get() = serviceDispatcher + serviceJob + CoroutineName("EdgeService")
    
    private val mainScope = CoroutineScope(Dispatchers.Main + serviceJob)

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
    private var sideBar: EdgeSideBar? = null
    private lateinit var freeformWM: FreeformWindowManager
    private lateinit var taskStackListener: FreeformTaskStackListener

    private var idleJob: Job? = null
    private var gameBarActive = false
    private var gamingModeJob: Job? = null

    private val sidelineSettingObserver = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) {
            val enabled = getSecureBoolean(SIDELINE, false)
            if (enabled != showSideline) {
                showSideline = enabled
                if (showSideline) {
                    showSidelineView()
                } else {
                    hideSidelineView()
                }
            }
        }
    }
    
    private val sideLineView by lazy {
        val gestureManager = MGestureManager(this@EdgeService, GestureListener(this@EdgeService))
        View(this).apply {
            background = AppCompatResources.getDrawable(this@EdgeService, R.drawable.ic_line)
            setOnTouchListener { _, event ->
                gestureManager.onTouchEvent(event)
                animate().alpha(ACTIVE_ALPHA).setDuration(FADE_DURATION).start()
                idleJob?.cancel()
                idleJob = mainScope.launch {
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
        private const val SIDELINE_HEIGHT = 200
        private const val OFFSET_PORTRAIT = 20
        private const val OFFSET_LANDSCAPE = 0

        private const val IDLE_TIMEOUT = 3000L
        private const val ACTIVE_ALPHA = 1.0f
        private const val IDLE_ALPHA = 0.3f
        private const val FADE_DURATION = 500L

        private const val GAMING_MODE_ACTIVE = "ax_gaming_mode_active"
        const val SIDELINE = "sidebar_feature_enabled"
        const val SIDELINE_POSITION_X = "sideline_position_x"
        const val SIDELINE_POSITION_Y_PORTRAIT = "sideline_position_y_portrait"
        const val SIDELINE_POSITION_Y_LANDSCAPE = "sideline_position_y_landscape"

        const val ACTION_LAUNCH_FREEFORM = "com.android.edge.bar.ACTION_LAUNCH_FREEFORM"
        const val ACTION_LAUNCH_DESKTOP_FREEFORM = "com.android.edge.bar.ACTION_LAUNCH_DESKTOP_FREEFORM"
        const val ACTION_BRING_ALL_TO_BACK = "com.android.edge.bar.ACTION_BRING_ALL_TO_BACK"
        const val EXTRA_PACKAGE_NAME = "package_name"
        const val EXTRA_ACTIVITY_NAME = "activity_name"
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND)

        userId = UserHandle.myUserId()

        if (intent?.action == ACTION_LAUNCH_FREEFORM) {
            val packageName = intent.getStringExtra(EXTRA_PACKAGE_NAME)
            val activityName = intent.getStringExtra(EXTRA_ACTIVITY_NAME)
            if (packageName != null) {
                launchFreeform(packageName, activityName)
            }
        }

        if (intent?.action == ACTION_LAUNCH_DESKTOP_FREEFORM) {
            val packageName = intent.getStringExtra(EXTRA_PACKAGE_NAME)
            val activityName = intent.getStringExtra(EXTRA_ACTIVITY_NAME)
            val targetDisplayId = intent.getIntExtra("target_display_id", Display.DEFAULT_DISPLAY)
            if (packageName != null) {
                launchDesktopFreeform(packageName, activityName, targetDisplayId)
            }
        }

        if (intent?.action == ACTION_BRING_ALL_TO_BACK) {
            if (::freeformWM.isInitialized) {
                freeformWM.bringAllWindowsToBack()
            }
        }

        if (userId != 0) {
            stopSelf()
            return START_STICKY
        }

        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        activityManager = IActivityManager.Stub.asInterface(ServiceManager.getService(Context.ACTIVITY_SERVICE))
        contentResolverRef = applicationContext.contentResolver
        
        freeformWM = FreeformWindowManager.getInstance(this)
        taskStackListener = FreeformTaskStackListener(this, freeformWM)
        taskStackListener.register()

        val metrics = windowManager.currentWindowMetrics.bounds
        screenWidth = metrics.width()
        screenHeight = metrics.height()
        freeformWM.updateScreenDimensions(screenWidth, screenHeight)

        activityManager.registerUserSwitchObserver(userSwitchObserver, "EdgeService")
        serviceStarted = true

        edgeSideBar = EdgeSideBar(this, object : EdgeSideBar.Callback {
            override fun onRemove() {
                if (isSidebarVisible && showSideline) {
                    updateSidelinePosition()
                    animateShowSideline()
                }
                isSidebarVisible = false
            }
        })

        launch { AppHelper.loadAppsCached(this@EdgeService) }

        showSideline = getSecureBoolean(SIDELINE, false)
        if (showSideline) showSidelineView()
        
        contentResolverRef.registerContentObserver(
            Settings.Secure.getUriFor(SIDELINE),
            false,
            sidelineSettingObserver
        )

        gamingModeJob = SettingsFlow(contentResolverRef, SettingsType.SECURE)
            .observeBoolean(GAMING_MODE_ACTIVE)
            .onEach { active ->
                if (active == gameBarActive) return@onEach
                gameBarActive = active
                mainScope.launch {
                    if (gameBarActive) hideSidelineView()
                    else if (showSideline) showSidelineView()
                }
            }
            .launchIn(this)

        return START_STICKY
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        val metrics = windowManager.currentWindowMetrics.bounds
        val newWidth = metrics.width()
        val newHeight = metrics.height()
        if (newWidth == screenWidth && newHeight == screenHeight) return

        screenWidth = newWidth
        screenHeight = newHeight

        if (::freeformWM.isInitialized) {
            freeformWM.updateScreenDimensions(newWidth, newHeight)
            freeformWM.notifyOrientationChangedConfig(newWidth > newHeight)
        }

        if (showSideline) updateSidelinePosition()
        if (isSidebarVisible) edgeSideBar.updateSidebarPosition()
    }

    override fun onDestroy() {
        super.onDestroy()

        gamingModeJob?.cancel()
        contentResolverRef.unregisterContentObserver(sidelineSettingObserver)
        
        if (::taskStackListener.isInitialized) {
            taskStackListener.unregister()
        }
        if (::freeformWM.isInitialized) {
            freeformWM.closeAllWindows()
        }
        
        removeView(force = true)
        serviceStarted = false
        activityManager.unregisterUserSwitchObserver(userSwitchObserver)
        serviceJob.cancel()
    }

    override fun showSidebar() {
        edgeSideBar.showPanelView()
        isSidebarVisible = true
        animateHideSideline()
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
        if (isSidelineVisible || gameBarActive) return

        mainScope.launch {
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

            try {
                windowManager.addView(sideLineView, wmLayoutParams)
                isSidelineVisible = true
                idleJob?.cancel()
                idleJob = mainScope.launch {
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
        mainScope.launch {
            try {
                windowManager.updateViewLayout(sideLineView, wmLayoutParams)
            } catch (_: Exception) { }
        }
    }

    private fun removeView(force: Boolean = false) {
        if (!isSidelineVisible && !force) return
        mainScope.launch {
            try {
                windowManager.removeViewImmediate(sideLineView)
            } catch (_: Exception) { }
        }
        edgeSideBar.removePanelView(force)
        edgeSideBar.release()
        isSidelineVisible = false
    }

    private fun animateHideSideline() {
        mainScope.launch {
            sideLineView.animate()
                .translationX(sidelinePosX * SIDELINE_WIDTH.toFloat())
                .setDuration(300)
                .start()
        }
    }

    private fun animateShowSideline() {
        mainScope.launch {
            sideLineView.animate().translationX(0f).setDuration(300).start()
        }
    }
    
    private fun hideSidelineView() {
        if (!isSidelineVisible) return
        mainScope.launch {
            try {
                windowManager.removeViewImmediate(sideLineView)
                isSidelineVisible = false
            } catch (_: Exception) { }
        }
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

    private fun launchFreeform(packageName: String, activityName: String? = null) {
        val existing = freeformWM.getWindow(packageName)
        if (existing != null) {
            if (existing.desktopMode) {
                freeformWM.closeWindow(packageName)
            } else {
                freeformWM.bringToFront(packageName)
                return
            }
        }

        val targetActivity = activityName ?: run {
             val launchIntent = packageManager.getLaunchIntentForPackage(packageName)
             launchIntent?.component?.className
        } ?: return

        val window = FreeformWindowCompose(this, packageName, targetActivity, userId)
    }

    private fun launchDesktopFreeform(packageName: String, activityName: String? = null,
            targetDisplayId: Int = Display.DEFAULT_DISPLAY) {
        val existing = freeformWM.getWindow(packageName)
        if (existing != null) {
            if (!existing.desktopMode) {
                freeformWM.closeWindow(packageName)
            } else {
                freeformWM.bringToFront(packageName)
                return
            }
        }

        val targetActivity = activityName ?: run {
             val launchIntent = packageManager.getLaunchIntentForPackage(packageName)
             launchIntent?.component?.className
        } ?: return

        val window = FreeformWindowCompose(this, packageName, targetActivity, userId,
                desktopMode = true, targetDisplayId = targetDisplayId)
    }
}
