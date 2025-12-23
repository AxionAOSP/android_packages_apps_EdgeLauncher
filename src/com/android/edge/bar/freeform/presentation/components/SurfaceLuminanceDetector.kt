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
package com.android.edge.bar.freeform.presentation.components

import android.graphics.Bitmap
import android.view.TextureView
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import kotlinx.coroutines.*

class SurfaceLuminanceDetector {
    companion object {
        const val SAMPLE_DELAY_MS = 500L
        private const val SAMPLE_HEIGHT_RATIO = 0.1f
        private const val LUMINANCE_THRESHOLD = 0.5f
    }

    private var lastLuminance: Float = 0f
    private var samplingJob: Job? = null

    fun isContentLight(bitmap: Bitmap?): Boolean {
        if (bitmap == null || bitmap.isRecycled) return false
        
        val sampleHeight = (bitmap.height * SAMPLE_HEIGHT_RATIO).toInt().coerceAtLeast(1)
        val sampleWidth = bitmap.width
        
        var totalLuminance = 0.0
        var sampleCount = 0
        
        val stepX = (sampleWidth / 10).coerceAtLeast(1)
        val stepY = (sampleHeight / 5).coerceAtLeast(1)
        
        for (y in 0 until sampleHeight step stepY) {
            for (x in 0 until sampleWidth step stepX) {
                val pixel = bitmap.getPixel(x, y)
                val r = (pixel shr 16 and 0xFF) / 255f
                val g = (pixel shr 8 and 0xFF) / 255f
                val b = (pixel and 0xFF) / 255f
                
                val luminance = 0.2126f * r + 0.7152f * g + 0.0722f * b
                totalLuminance += luminance
                sampleCount++
            }
        }
        
        lastLuminance = if (sampleCount > 0) {
            (totalLuminance / sampleCount).toFloat()
        } else {
            0f
        }
        
        return lastLuminance > LUMINANCE_THRESHOLD
    }

    fun captureBitmap(textureView: TextureView): Bitmap? {
        return try {
            textureView.bitmap
        } catch (e: Exception) {
            null
        }
    }

    fun getOverlayColors(isContentLight: Boolean): Pair<Color, Color> {
        return if (isContentLight) {
            Color(0xFF1C1C1E) to Color(0x40FFFFFF)
        } else {
            Color(0xFFFFFFFF) to Color(0x40000000)
        }
    }

    fun destroy() {
        samplingJob?.cancel()
        samplingJob = null
    }
}

@Composable
fun rememberSurfaceLuminance(
    textureView: TextureView?,
    scope: CoroutineScope
): State<Boolean> {
    val detector = remember { SurfaceLuminanceDetector() }
    val isContentLight = remember { mutableStateOf(false) }

    DisposableEffect(textureView) {
        val job = scope.launch {
            while (isActive) {
                delay(SurfaceLuminanceDetector.SAMPLE_DELAY_MS)
                textureView?.let { view ->
                    val bitmap = detector.captureBitmap(view)
                    isContentLight.value = detector.isContentLight(bitmap)
                    bitmap?.recycle()
                }
            }
        }

        onDispose {
            job.cancel()
            detector.destroy()
        }
    }

    return isContentLight
}

private const val SAMPLE_DELAY_MS = 500L
