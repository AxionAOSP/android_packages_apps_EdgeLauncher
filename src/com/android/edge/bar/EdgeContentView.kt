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
import android.graphics.Rect
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.text.style.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.*
import androidx.compose.ui.draw.*
import androidx.compose.ui.focus.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.vector.*
import androidx.compose.ui.layout.*
import androidx.compose.ui.platform.*
import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.unit.*
import androidx.compose.ui.window.*
import kotlin.math.abs

@Composable
fun EdgeContentView(
    onPanelTap: () -> Unit,
    onPinnedAppClick: (context: Context, packageName: String) -> Unit,
    onAppDrawerClick: () -> Unit,
    sidebarHeight: Int,
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

    var activePopup by remember { mutableStateOf<PopupState?>(null) }

    EdgeSidebarCard(
        height = sidebarHeight,
        onPanelTap = onPanelTap
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(vertical = 12.dp, horizontal = 6.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(52.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer)
                    .clickable { onAppDrawerClick() }
            ) {
                Icon(
                    imageVector = Icons.Rounded.Apps,
                    contentDescription = "App Drawer",
                    modifier = Modifier.size(24.dp),
                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }

            Box(
                modifier = Modifier
                    .width(32.dp)
                    .height(1.5.dp)
                    .clip(RoundedCornerShape(1.dp))
                    .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.95f))
            )

            LazyColumn(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                if (pinnedApps.isEmpty()) {
                    item { 
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(120.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Add,
                                contentDescription = null,
                                modifier = Modifier.size(28.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                            )
                        }
                    }
                } else {
                    items(pinnedApps) { appInfo ->
                        AppIconButton(
                            appInfo = appInfo,
                            onClick = { onPinnedAppClick(context, appInfo.packageName) },
                            onLongClick = { bounds ->
                                activePopup = PopupState(
                                    packageName = appInfo.packageName,
                                    anchorBounds = bounds
                                )
                            }
                        )
                    }
                }
            }
        }
    }

    activePopup?.let { popup ->
        LaunchModePopup(
            anchorBounds = popup.anchorBounds,
            onDismiss = { activePopup = null },
            onLaunchFull = { 
                AppHelper.launchAppFull(context, popup.packageName)
                activePopup = null
            },
            onLaunchFreeform = { 
                AppHelper.launchApp(popup.packageName)
                activePopup = null
            }
        )
    }
}

@OptIn(ExperimentalAnimationApi::class)
@Composable
fun AppDrawerContentView(
    onDismiss: () -> Unit,
    onAppClick: (context: Context, packageName: String) -> Unit,
    drawerWidth: Int,
    drawerHeight: Int
) {
    val context = LocalContext.current
    val allApps by produceState(initialValue = emptyList<AppInfo>()) {
        value = AppHelper.getInstalledApps(context)
    }

    var activePopup by remember { mutableStateOf<PopupState?>(null) }

    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        shape = RoundedCornerShape(32.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = Modifier
            .width(drawerWidth.dp)
            .height(drawerHeight.dp)
            .padding(end = 8.dp)
            .swipeToDismiss(threshold = 150f, onDismiss = onDismiss)
    ) {
        if (allApps.isEmpty()) {
            EmptyState(text = "No apps found")
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(4),
                contentPadding = PaddingValues(horizontal = 24.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp),
                horizontalArrangement = Arrangement.spacedBy(20.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(allApps) { appInfo ->
                    AppGridItem(
                        appInfo = appInfo,
                        onClick = { onAppClick(context, appInfo.packageName) },
                        onLongClick = { bounds ->
                            activePopup = PopupState(
                                packageName = appInfo.packageName,
                                anchorBounds = bounds
                            )
                        }
                    )
                }
            }
        }
    }

    activePopup?.let { popup ->
        LaunchModePopup(
            anchorBounds = popup.anchorBounds,
            onDismiss = { activePopup = null },
            onLaunchFull = { 
                AppHelper.launchAppFull(context, popup.packageName)
                activePopup = null
            },
            onLaunchFreeform = { 
                AppHelper.launchApp(popup.packageName)
                activePopup = null
            }
        )
    }
}

@Composable
private fun EdgeSidebarCard(
    height: Int,
    onPanelTap: () -> Unit,
    content: @Composable () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        shape = RoundedCornerShape(20.dp),
        tonalElevation = 2.dp,
        shadowElevation = 4.dp,
        modifier = Modifier
            .width(64.dp)
            .height(height.dp)
            .border(
                width = 0.5.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f),
                shape = RoundedCornerShape(20.dp)
            )
            .swipeToDismiss(threshold = 50f, onDismiss = onPanelTap)
    ) {
        content()
    }
}

@Composable
private fun CircleIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.primaryContainer,
    contentColor: Color = MaterialTheme.colorScheme.onPrimaryContainer,
    size: Dp = 56.dp,
    iconSize: Dp = 28.dp
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(containerColor)
            .clickable { onClick() }
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            modifier = Modifier.size(iconSize),
            tint = contentColor
        )
    }
}

@Composable
private fun AppIconButton(
    appInfo: AppInfo,
    onClick: () -> Unit,
    onLongClick: (Rect) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var anchorBounds by remember { mutableStateOf<Rect?>(null) }
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.88f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessHigh
        )
    )

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(52.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.4f))
            .onGloballyPositioned { coords ->
                val pos = coords.positionInWindow()
                val size = coords.size
                anchorBounds = Rect(
                    pos.x.toInt(),
                    pos.y.toInt(),
                    (pos.x + size.width).toInt(),
                    (pos.y + size.height).toInt()
                )
            }
            .combinedClickable(
                interactionSource = interactionSource,
                indication = ripple(bounded = true, radius = 26.dp, color = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)),
                onClick = onClick,
                onLongClick = { anchorBounds?.let { onLongClick(it) } }
            )
    ) {
        Image(
            painter = AppHelper.getAppPainter(context, appInfo.packageName, appInfo.icon),
            contentDescription = appInfo.label,
            modifier = Modifier
                .size(32.dp)
                .clip(RoundedCornerShape(10.dp))
        )
    }
}

@Composable
private fun AppGridItem(
    appInfo: AppInfo,
    onClick: () -> Unit,
    onLongClick: (Rect) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var anchorBounds by remember { mutableStateOf<Rect?>(null) }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = modifier
            .fillMaxWidth()
            .onGloballyPositioned { coords ->
                val pos = coords.positionInWindow()
                val size = coords.size
                anchorBounds = Rect(
                    pos.x.toInt(),
                    pos.y.toInt(),
                    (pos.x + size.width).toInt(),
                    (pos.y + size.height).toInt()
                )
            }
            .combinedClickable(
                onClick = onClick,
                onLongClick = { anchorBounds?.let { onLongClick(it) } }
            )
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(68.dp)
                .clip(RoundedCornerShape(20.dp))
        ) {
            Image(
                painter = AppHelper.getAppPainter(context, appInfo.packageName, appInfo.icon),
                contentDescription = appInfo.label,
                modifier = Modifier.size(48.dp)
            )
        }

        Text(
            text = appInfo.label,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            lineHeight = 14.sp,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun EmptyState(
    text: String,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

@OptIn(ExperimentalAnimationApi::class)
@Composable
private fun LaunchModePopup(
    anchorBounds: Rect,
    onDismiss: () -> Unit,
    onLaunchFull: () -> Unit,
    onLaunchFreeform: () -> Unit
) {
    val density = LocalDensity.current
    val configuration = LocalConfiguration.current
    val screenWidth = with(density) { configuration.screenWidthDp.dp.toPx() }
    
    val popupWidth = 120.dp
    val popupWidthPx = with(density) { popupWidth.toPx() }
    
    val anchorCenterX = (anchorBounds.left + anchorBounds.right) / 2f
    var offsetX = anchorCenterX - (popupWidthPx / 2f)
    
    val margin = with(density) { 8.dp.toPx() }
    offsetX = offsetX.coerceIn(margin, screenWidth - popupWidthPx - margin)
    
    val offsetY = anchorBounds.bottom.toFloat() + with(density) { 8.dp.toPx() }

    AnimatedVisibility(
        visible = true,
        enter = fadeIn(tween(180, easing = FastOutSlowInEasing)) + 
                scaleIn(
                    initialScale = 0.88f,
                    transformOrigin = TransformOrigin(0.5f, 0f),
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessMedium
                    )
                ) +
                slideInVertically(
                    initialOffsetY = { -20 },
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessMedium
                    )
                ),
        exit = fadeOut(tween(120)) + 
               scaleOut(
                   targetScale = 0.92f,
                   transformOrigin = TransformOrigin(0.5f, 0f),
                   animationSpec = tween(120)
               )
    ) {
        Popup(
            offset = IntOffset(offsetX.toInt(), offsetY.toInt()),
            onDismissRequest = onDismiss
        ) {
            Surface(
                shape = RoundedCornerShape(32.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.96f),
                tonalElevation = 3.dp,
                shadowElevation = 12.dp,
                modifier = Modifier
                    .width(popupWidth)
                    .border(
                        width = 0.5.dp,
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
                        shape = RoundedCornerShape(32.dp)
                    )
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .padding(horizontal = 14.dp, vertical = 12.dp)
                        .fillMaxWidth()
                ) {
                    Box(
                        modifier = Modifier.weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        LaunchModeIconButton(
                            icon = Icons.Rounded.OpenInFull,
                            contentDescription = "Full screen",
                            onClick = onLaunchFull
                        )
                    }

                    Box(
                        modifier = Modifier
                            .width(1.dp)
                            .height(32.dp)
                            .background(
                                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.8f),
                                shape = RoundedCornerShape(0.5.dp)
                            )
                    )

                    Box(
                        modifier = Modifier.weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        LaunchModeIconButton(
                            icon = Icons.Rounded.OpenInBrowser,
                            contentDescription = "Freeform",
                            onClick = onLaunchFreeform
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LaunchModeIconButton(
    icon: ImageVector,
    contentDescription: String,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.9f),
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.92f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessHigh
        )
    )

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(48.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(CircleShape)
            .background(containerColor)
            .clickable(
                interactionSource = interactionSource,
                indication = ripple(bounded = true, radius = 24.dp),
                onClick = onClick
            )
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = contentColor,
            modifier = Modifier.size(22.dp)
        )
    }
}

private data class PopupState(
    val packageName: String,
    val anchorBounds: Rect
)

private fun Modifier.swipeToDismiss(
    threshold: Float,
    onDismiss: () -> Unit
): Modifier = this.pointerInput(Unit) {
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
                    if (abs(deltaX) > abs(deltaY) && abs(deltaX) > threshold) {
                        onDismiss()
                    }
                    initialX = 0f
                    initialY = 0f
                }
            }
        }
    }
}
