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
package com.android.edge.bar.freeform

import android.content.Context
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.WindowManager
import com.android.edge.bar.freeform.domain.FreeformConstants
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.platform.*
import androidx.compose.ui.unit.*
import androidx.lifecycle.*
import com.android.edge.bar.lifecycle.repeatWhenAttached

class RemoveZoneOverlay(private val context: Context) {

    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val mainHandler = Handler(Looper.getMainLooper())
    private var overlayView: ComposeView? = null
    private var isViewAdded = false
    
    private val _isVisible = mutableStateOf(false)
    private val _isHovering = mutableStateOf(false)

    private val pillWidth = FreeformConstants.REMOVE_PILL_WIDTH_DP
    private val pillHeight = FreeformConstants.REMOVE_PILL_HEIGHT_DP
    private val pillTopOffset = FreeformConstants.REMOVE_PILL_TOP_OFFSET_DP
    
    private val layoutParams = WindowManager.LayoutParams().apply {
        type = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        width = WindowManager.LayoutParams.MATCH_PARENT
        height = WindowManager.LayoutParams.WRAP_CONTENT
        format = PixelFormat.TRANSLUCENT
        gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
        flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
        privateFlags = WindowManager.LayoutParams.SYSTEM_FLAG_SHOW_FOR_ALL_USERS or
                WindowManager.LayoutParams.PRIVATE_FLAG_TRUSTED_OVERLAY
    }

    fun show() {
        mainHandler.post {
            if (!isViewAdded) {
                createOverlayView()
                try {
                    windowManager.addView(overlayView, layoutParams)
                    isViewAdded = true
                } catch (e: Exception) {
                }
            }
            _isVisible.value = true
        }
    }
    
    fun hide() {
        mainHandler.post {
            _isVisible.value = false
            _isHovering.value = false
            mainHandler.postDelayed({
                removeViewIfAdded()
            }, 300)
        }
    }
    
    private fun removeViewIfAdded() {
        if (isViewAdded && !_isVisible.value) {
            try {
                windowManager.removeViewImmediate(overlayView)
                isViewAdded = false
                overlayView = null
            } catch (e: Exception) {
            }
        }
    }

    fun setHovering(hovering: Boolean) {
        _isHovering.value = hovering
    }

    fun isInRemoveZone(bubbleX: Float, bubbleY: Float): Boolean {
        val density = context.resources.displayMetrics.density
        val screenWidth = context.resources.displayMetrics.widthPixels
        
        val pillWidthPx = pillWidth * density
        val pillHeightPx = pillHeight * density
        val pillTopPx = pillTopOffset * density
        val tolerance = FreeformConstants.REMOVE_PILL_TOLERANCE_DP * density
        
        val centerX = screenWidth / 2f
        val halfZoneWidth = (pillWidthPx / 2f) + tolerance
        val removeZoneTop = pillTopPx - tolerance
        val removeZoneBottom = pillTopPx + pillHeightPx + tolerance
        
        val inXRange = bubbleX >= (centerX - halfZoneWidth) && bubbleX <= (centerX + halfZoneWidth)
        val inYRange = bubbleY >= removeZoneTop && bubbleY <= removeZoneBottom
        
        return inXRange && inYRange
    }
    
    fun destroy() {
        mainHandler.post {
            _isVisible.value = false
            _isHovering.value = false
            if (isViewAdded) {
                try {
                    windowManager.removeViewImmediate(overlayView)
                } catch (e: Exception) {
                }
                isViewAdded = false
            }
            overlayView = null
        }
    }
    
    private fun createOverlayView() {
        overlayView = ComposeView(context).apply {
            repeatWhenAttached {
                repeatOnLifecycle(Lifecycle.State.STARTED) {
                    setContent {
                        MaterialTheme {
                            RemoveZoneContent(
                                isVisible = _isVisible.value,
                                isHovering = _isHovering.value
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun getAccentColor(): Color {
    val ctx = LocalContext.current
    return Color(ctx.getColor(android.R.color.system_accent1_100))
}

@Composable
private fun RemoveZoneContent(
    isVisible: Boolean,
    isHovering: Boolean
) {
    val accentColor = getAccentColor()
    val removeZoneColor = if (isHovering) {
                    accentColor
                } else {
                    Color.Transparent
                }
    val removeZoneElementColor = if (isHovering) {
                    Color.Black
                } else {
                    accentColor
                }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = FreeformConstants.REMOVE_PILL_TOP_OFFSET_DP.dp),
        contentAlignment = Alignment.TopCenter
    ) {
        AnimatedVisibility(
            visible = isVisible,
            enter = fadeIn() + scaleIn(initialScale = 0.8f),
            exit = fadeOut() + scaleOut(targetScale = 0.8f)
        ) {
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = removeZoneColor,
                contentColor = removeZoneColor,
                border = if (isHovering) null else BorderStroke(
                    width = 2.dp,
                    color = accentColor
                ),
                shadowElevation = if (isHovering) 4.dp else 0.dp
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = removeZoneElementColor 
                    )
                    Text(
                        text = "Remove",
                        fontSize = 14.sp,
                        color = removeZoneElementColor
                    )
                }
            }
        }
    }
}
