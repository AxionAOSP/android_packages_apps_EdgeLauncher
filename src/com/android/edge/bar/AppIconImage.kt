/*
 * Copyright (C) 2025-2026 AxionOS Project
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

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun AppIconImage(
    appInfo: AppInfo,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 0.dp,
    placeholderColor: Color = MaterialTheme.colorScheme.surfaceContainerHigh
) {
    val appContext = LocalContext.current.applicationContext
    val iconVersion by AppHelper.version.collectAsState()
    var painter by remember(appInfo.iconKey, iconVersion) {
        mutableStateOf(AppHelper.getCachedAppPainter(appInfo))
    }

    LaunchedEffect(appInfo.iconKey, iconVersion) {
        if (painter == null) {
            painter = withContext(Dispatchers.Default) {
                AppHelper.getAppPainter(appContext, appInfo)
            }
        }
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(cornerRadius))
            .background(placeholderColor),
        contentAlignment = Alignment.Center
    ) {
        painter?.let {
            Image(
                painter = it,
                contentDescription = contentDescription,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}
