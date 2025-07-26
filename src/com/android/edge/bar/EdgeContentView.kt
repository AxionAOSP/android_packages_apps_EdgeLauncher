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

import android.content.Context
import android.view.MotionEvent
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.text.style.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.unit.*
import kotlin.math.abs

@Composable
fun EdgeContentView(
    onPanelTap: () -> Unit,
    onPinnedAppClick: (context: Context, packageName: String) -> Unit,
    onAppDrawerClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    val allApps by produceState(initialValue = emptyList<AppInfo>()) {
        value = AppHelper.getInstalledApps(context)
    }

    val pinnedApps by produceState(initialValue = emptyList<AppInfo>(), key1 = allApps) {
        val pinnedPkgs = PinnedApps.getPinned(context)
        value = allApps.filter { it.packageName in pinnedPkgs }
    }

    val cardShape = RoundedCornerShape(32.dp)

    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest
        ),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    var initialX = 0f
                    var initialY = 0f
                    while (true) {
                        val event = awaitPointerEvent(pass = PointerEventPass.Initial)
                        event.changes.forEach { change ->
                            if (change.pressed) {
                                if (initialX == 0f && initialY == 0f) {
                                    initialX = change.position.x
                                    initialY = change.position.y
                                }
                            } else if (change.changedToUp()) {
                                val deltaX = change.position.x - initialX
                                val deltaY = change.position.y - initialY
                                if (abs(deltaX) > abs(deltaY) && abs(deltaX) > 50) {
                                    onPanelTap()
                                }
                                initialX = 0f
                                initialY = 0f
                            }
                        }
                    }
                }
            }
    ) {
        Column(
            modifier = Modifier,
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Column {
                Icon(
                    imageVector = Icons.Default.Apps,
                    contentDescription = "App Drawer",
                    modifier = Modifier
                        .size(50.dp)
                        .padding(8.dp)
                        .clickable { onAppDrawerClick() },
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }

            LazyColumn {
                if (pinnedApps.isEmpty()) {
                    item { Spacer(modifier = Modifier.height(64.dp)) }
                } else {
                    items(pinnedApps) { appInfo ->
                        Image(
                            painter = AppHelper.getAppPainter(
                                context,
                                appInfo.packageName,
                                appInfo.icon
                            ),
                            contentDescription = appInfo.label,
                            modifier = Modifier
                                .size(50.dp)
                                .padding(8.dp)
                                .clickable { onPinnedAppClick(context, appInfo.packageName) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun AppDrawerContentView(
    onDismiss: () -> Unit,
    onAppClick: (context: Context, packageName: String) -> Unit
) {
    val context = LocalContext.current
    val allApps by produceState(initialValue = emptyList<AppInfo>()) {
        value = AppHelper.getInstalledApps(context)
    }

    Card(
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .padding(16.dp)
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    var initialX = 0f
                    var initialY = 0f
                    while (true) {
                        val event = awaitPointerEvent(pass = PointerEventPass.Initial)
                        event.changes.forEach { change ->
                            if (change.pressed) {
                                if (initialX == 0f && initialY == 0f) {
                                    initialX = change.position.x
                                    initialY = change.position.y
                                }
                            } else if (change.changedToUp()) {
                                val deltaX = change.position.x - initialX
                                val deltaY = change.position.y - initialY
                                if (abs(deltaX) > abs(deltaY) && abs(deltaX) > 150) {
                                    onDismiss()
                                }
                                initialX = 0f
                                initialY = 0f
                            }
                        }
                    }
                }
            }
    ) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(4),
            contentPadding = PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp)
        ) {
            items(allApps) { appInfo ->
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier
                        .width(64.dp)
                        .height(80.dp)
                        .clickable { onAppClick(context, appInfo.packageName) }
                ) {
                    Image(
                        painter = AppHelper.getAppPainter(
                            context,
                            appInfo.packageName,
                            appInfo.icon
                        ),
                        contentDescription = appInfo.label,
                        modifier = Modifier
                            .size(48.dp)
                            .padding(bottom = 4.dp)
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = appInfo.label,
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }
    }
}
