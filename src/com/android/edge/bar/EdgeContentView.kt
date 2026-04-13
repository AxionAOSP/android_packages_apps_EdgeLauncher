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

import android.content.Context
import android.graphics.Rect
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ChatBubble
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.OpenInBrowser
import androidx.compose.material.icons.rounded.OpenInFull
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.zIndex
import com.android.compose.animation.scene.ContentScope
import com.android.compose.animation.scene.ElementKey
import com.android.compose.animation.scene.SceneKey
import com.android.compose.animation.scene.SceneTransitionLayout
import com.android.compose.animation.scene.rememberMutableSceneTransitionLayoutState
import com.android.compose.animation.scene.transitions
import com.android.edge.bar.R
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

val PANEL_WIDTH = 130.dp
private val PANEL_CORNER_RADIUS = 24.dp
private val ALL_APPS_ICON_SIZE = 34.dp
private val ALL_APPS_ICON_CORNER = 10.dp

private val HANDLE_AREA_HEIGHT = 40.dp
private val HANDLE_WIDTH = 32.dp
private val HANDLE_HEIGHT = 4.dp

private val SETTINGS_AREA_HEIGHT = 42.dp
private val SETTINGS_ICON_SIZE = 16.dp

private val ICON_SIZE = 46.dp
private val ICON_IMAGE_SIZE = 34.dp
private val ICON_CORNER_RADIUS = 14.dp

const val MAX_PINNED_APPS = 7

private object EdgeScenes {
    val Panel = SceneKey("edge_panel")
    val AllApps = SceneKey("edge_all_apps")
}

private object EdgeElements {
    val PanelCard = ElementKey("edge_panel_card")
    val AllAppsCard = ElementKey("edge_all_apps_card")
}

private val EdgeTransitions = transitions {
    from(EdgeScenes.Panel, to = EdgeScenes.AllApps) {
        spec = tween(durationMillis = 280)
        fractionRange(end = 0.35f) { fade(EdgeElements.PanelCard) }
        fractionRange(start = 0.35f) { fade(EdgeElements.AllAppsCard) }
    }
    from(EdgeScenes.AllApps, to = EdgeScenes.Panel) {
        spec = tween(durationMillis = 250)
        fractionRange(end = 0.35f) { fade(EdgeElements.AllAppsCard) }
        fractionRange(start = 0.35f) { fade(EdgeElements.PanelCard) }
    }
}

@Composable
fun EdgeContentView(
    onPinnedAppClick: (context: Context, packageName: String) -> Unit,
    onSettingsClick: () -> Unit,
    onDrag: (deltaX: Float, deltaY: Float) -> Unit,
    onDragEnd: () -> Unit,
    panelOnRight: Boolean = true,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val config = LocalConfiguration.current

    val screenHeightDp = config.screenHeightDp.dp
    val screenWidthDp = config.screenWidthDp.dp
    val panelHeight = (screenHeightDp * 0.42f).coerceIn(260.dp, 380.dp)
    val allAppsWidth = (screenWidthDp * 0.72f).coerceIn(300.dp, 400.dp)
    val allAppsHeight = (screenHeightDp * 0.65f).coerceIn(400.dp, 580.dp)

    val allApps by produceState(initialValue = AppHelper.getCachedAppsOrEmpty()) {
        if (value.isEmpty()) {
            value = withContext(Dispatchers.Default) {
                AppHelper.loadAppsCached(context)
            }
        }
    }

    val pinnedApps by produceState(initialValue = emptyList<AppInfo>(), key1 = allApps) {
        val pinnedPkgs = PinnedApps.getPinned(context)
        value = allApps.filter { it.packageName in pinnedPkgs }.take(MAX_PINNED_APPS)
    }

    var activePopup by remember { mutableStateOf<PopupState?>(null) }

    val stlState = rememberMutableSceneTransitionLayoutState(
        initialScene = EdgeScenes.Panel,
        transitions = EdgeTransitions
    )

    SceneTransitionLayout(
        state = stlState,
        modifier = modifier
    ) {
        scene(EdgeScenes.Panel) {
            EdgePanelCard(
                pinnedApps = pinnedApps,
                panelOnRight = panelOnRight,
                panelHeight = panelHeight,
                onPinnedAppClick = { pkg -> onPinnedAppClick(context, pkg) },
                onLongClick = { pkg, activityName, bounds ->
                    activePopup = PopupState(pkg, activityName, bounds)
                },
                onSettingsClick = onSettingsClick,
                onAllAppsClick = {
                    stlState.setTargetScene(EdgeScenes.AllApps, coroutineScope)
                },
                onDrag = onDrag,
                onDragEnd = onDragEnd
            )
        }
        scene(EdgeScenes.AllApps) {
            AllAppsCard(
                allApps = allApps,
                allAppsWidth = allAppsWidth,
                allAppsHeight = allAppsHeight,
                onAppClick = { pkg -> onPinnedAppClick(context, pkg) },
                onBack = { stlState.setTargetScene(EdgeScenes.Panel, coroutineScope) }
            )
        }
    }

    activePopup?.let { popup ->
        LaunchModePopup(
            anchorBounds = popup.anchorBounds,
            panelOnRight = panelOnRight,
            onDismiss = { activePopup = null },
            onLaunchFull = {
                AppHelper.launchAppFull(context, popup.packageName)
                activePopup = null
            },
            onLaunchFreeform = {
                AppHelper.launchApp(popup.packageName)
                activePopup = null
            },
            onLaunchBubble = {
                AppHelper.launchAsBubble(context, popup.packageName, popup.activityName)
                activePopup = null
            },
            bubbleSupported = remember { AppHelper.isBubbleSupported() }
        )
    }
}

@Composable
private fun ContentScope.EdgePanelCard(
    pinnedApps: List<AppInfo>,
    panelOnRight: Boolean,
    panelHeight: Dp,
    onPinnedAppClick: (packageName: String) -> Unit,
    onLongClick: (packageName: String, activityName: String, bounds: Rect) -> Unit,
    onSettingsClick: () -> Unit,
    onAllAppsClick: () -> Unit,
    onDrag: (deltaX: Float, deltaY: Float) -> Unit,
    onDragEnd: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        shape = RoundedCornerShape(PANEL_CORNER_RADIUS),
        tonalElevation = 2.dp,
        modifier = Modifier
            .element(EdgeElements.PanelCard)
            .requiredWidth(PANEL_WIDTH)
            .requiredHeight(panelHeight)
            .border(
                width = 0.5.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f),
                shape = RoundedCornerShape(PANEL_CORNER_RADIUS)
            )
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(HANDLE_AREA_HEIGHT)
                    .pointerInput(Unit) {
                        detectDragGestures(
                            onDrag = { change, dragAmount ->
                                change.consume()
                                onDrag(dragAmount.x, dragAmount.y)
                            },
                            onDragEnd = onDragEnd,
                            onDragCancel = onDragEnd
                        )
                    },
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .width(HANDLE_WIDTH)
                        .height(HANDLE_HEIGHT)
                        .clip(RoundedCornerShape(HANDLE_HEIGHT / 2))
                        .background(MaterialTheme.colorScheme.onSurfaceVariant)
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(horizontal = 12.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                for (rowIndex in 0 until 4) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        for (colIndex in 0 until 2) {
                            val index = rowIndex * 2 + colIndex
                            when {
                                index < pinnedApps.size -> {
                                    AppIconButton(
                                        appInfo = pinnedApps[index],
                                        onClick = { onPinnedAppClick(pinnedApps[index].packageName) },
                                        onLongClick = { bounds ->
                                            onLongClick(
                                                pinnedApps[index].packageName,
                                                pinnedApps[index].activityName,
                                                bounds
                                            )
                                        }
                                    )
                                }
                                index == MAX_PINNED_APPS -> {
                                    AllAppsIconButton(onClick = onAllAppsClick)
                                }
                                else -> {
                                    Spacer(modifier = Modifier.size(ICON_SIZE))
                                }
                            }
                        }
                    }
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(SETTINGS_AREA_HEIGHT)
                    .clickable { onSettingsClick() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.Settings,
                    contentDescription = null,
                    modifier = Modifier.size(SETTINGS_ICON_SIZE),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun ContentScope.AllAppsCard(
    allApps: List<AppInfo>,
    allAppsWidth: Dp,
    allAppsHeight: Dp,
    onAppClick: (packageName: String) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        shape = RoundedCornerShape(PANEL_CORNER_RADIUS),
        tonalElevation = 2.dp,
        modifier = Modifier
            .element(EdgeElements.AllAppsCard)
            .requiredWidth(allAppsWidth)
            .requiredHeight(allAppsHeight)
            .border(
                width = 0.5.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f),
                shape = RoundedCornerShape(PANEL_CORNER_RADIUS)
            )
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(HANDLE_AREA_HEIGHT)
                    .padding(start = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.ArrowBack,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    text = stringResource(R.string.edge_all_apps),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(start = 2.dp)
                )
            }

            LazyVerticalGrid(
                columns = GridCells.Fixed(5),
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                items(allApps, key = { it.packageName }) { app ->
                    AllAppsGridItem(
                        app = app,
                        onClick = { onAppClick(app.packageName) }
                    )
                }
            }
        }
    }
}

@Composable
private fun AllAppsGridItem(
    app: AppInfo,
    onClick: () -> Unit
) {
    val context = LocalContext.current
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp)
    ) {
        Image(
            painter = AppHelper.getAppPainter(context, app.packageName, app.icon),
            contentDescription = app.label,
            modifier = Modifier
                .size(ALL_APPS_ICON_SIZE)
                .clip(RoundedCornerShape(ALL_APPS_ICON_CORNER))
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = app.label,
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp),
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun AllAppsIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
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
            .size(ICON_SIZE)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(RoundedCornerShape(ICON_CORNER_RADIUS))
            .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f))
            .combinedClickable(
                interactionSource = interactionSource,
                indication = ripple(
                    bounded = true,
                    radius = ICON_SIZE / 2,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                ),
                onClick = onClick
            )
    ) {
        Icon(
            imageVector = Icons.Rounded.GridView,
            contentDescription = stringResource(R.string.edge_all_apps),
            modifier = Modifier.size(ICON_IMAGE_SIZE * 0.7f),
            tint = MaterialTheme.colorScheme.onPrimaryContainer
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
            .size(ICON_SIZE)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(RoundedCornerShape(ICON_CORNER_RADIUS))
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
                indication = ripple(
                    bounded = true,
                    radius = ICON_SIZE / 2,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                ),
                onClick = onClick,
                onLongClick = { anchorBounds?.let { onLongClick(it) } }
            )
    ) {
        Image(
            painter = AppHelper.getAppPainter(context, appInfo.packageName, appInfo.icon),
            contentDescription = appInfo.label,
            modifier = Modifier
                .size(ICON_IMAGE_SIZE)
                .clip(RoundedCornerShape(10.dp))
        )
    }
}

@Composable
private fun LaunchModePopup(
    anchorBounds: Rect,
    panelOnRight: Boolean,
    onDismiss: () -> Unit,
    onLaunchFull: () -> Unit,
    onLaunchFreeform: () -> Unit,
    onLaunchBubble: () -> Unit,
    bubbleSupported: Boolean
) {
    val density = LocalDensity.current

    val menuWidth = 210.dp
    val caretSize = 14.dp
    val caretHalf = caretSize / 2
    val gap = 4.dp
    val spacing = 1.dp
    val itemHeight = 52.dp
    val itemCount = if (bubbleSupported) 3 else 2
    val menuHeight = itemHeight * itemCount + spacing * (itemCount - 1)

    val anchorCenterYPx = (anchorBounds.top + anchorBounds.bottom) / 2f
    val menuHalfPx = with(density) { (menuHeight / 2).toPx() }
    val popupY = (anchorCenterYPx - menuHalfPx).roundToInt()

    val popupX = if (panelOnRight) {
        anchorBounds.left - with(density) { (menuWidth + gap + caretHalf).toPx() }.roundToInt()
    } else {
        anchorBounds.right + with(density) { (gap + caretHalf).toPx() }.roundToInt()
    }

    val arrowOffsetY = with(density) { (menuHeight / 2 - caretHalf).toPx() }.roundToInt()

    Popup(
        offset = IntOffset(popupX, popupY),
        onDismissRequest = onDismiss
    ) {
        Box {
            Surface(
                color = MaterialTheme.colorScheme.surfaceBright,
                shape = RoundedCornerShape(2.dp),
                modifier = Modifier
                    .size(caretSize)
                    .offset(
                        x = if (panelOnRight) menuWidth - caretHalf else 0.dp,
                        y = with(density) { arrowOffsetY.toDp() }
                    )
                    .graphicsLayer { rotationZ = 45f }
            ) {}

            Column(
                verticalArrangement = Arrangement.spacedBy(spacing),
                modifier = Modifier
                    .width(menuWidth)
                    .offset(x = if (panelOnRight) 0.dp else caretHalf)
                    .zIndex(1f)
            ) {
                PopupMenuItem(
                    icon = Icons.Rounded.OpenInFull,
                    label = stringResource(R.string.edge_popup_full_screen),
                    isFirst = true,
                    isLast = false,
                    onClick = onLaunchFull
                )
                PopupMenuItem(
                    icon = Icons.Rounded.OpenInBrowser,
                    label = stringResource(R.string.edge_popup_freeform),
                    isFirst = false,
                    isLast = !bubbleSupported,
                    onClick = onLaunchFreeform
                )
                if (bubbleSupported) {
                    PopupMenuItem(
                        icon = Icons.Rounded.ChatBubble,
                        label = stringResource(R.string.edge_popup_bubble),
                        isFirst = false,
                        isLast = true,
                        onClick = onLaunchBubble
                    )
                }
            }
        }
    }
}

@Composable
private fun PopupMenuItem(
    icon: ImageVector,
    label: String,
    isFirst: Boolean,
    isLast: Boolean,
    onClick: () -> Unit
) {
    val topRadius = if (isFirst) 16.dp else 4.dp
    val bottomRadius = if (isLast) 16.dp else 4.dp
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(
            topStart = topRadius,
            topEnd = topRadius,
            bottomStart = bottomRadius,
            bottomEnd = bottomRadius
        ),
        color = MaterialTheme.colorScheme.surfaceBright,
        shadowElevation = 4.dp,
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 12.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(12.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 12.sp),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1
            )
        }
    }
}

private data class PopupState(
    val packageName: String,
    val activityName: String,
    val anchorBounds: Rect
)
