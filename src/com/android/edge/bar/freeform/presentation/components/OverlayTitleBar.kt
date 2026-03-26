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

import androidx.compose.animation.*
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.PictureInPictureAlt
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.android.edge.bar.freeform.domain.FreeformConstants.OVERLAY_TITLE_BAR_HEIGHT_DP
import com.android.edge.bar.freeform.domain.FreeformConstants.OVERLAY_PILL_ICON_SIZE_DP
import kotlinx.coroutines.delay

@Composable
fun OverlayTitleBar(
    onClose: () -> Unit,
    onMinimize: () -> Unit,
    onBubble: () -> Unit,
    onMaximizeFullscreen: () -> Unit,
    onDrag: (Float, Float) -> Unit,
    onDragStart: () -> Unit = {},
    onDragEnd: () -> Unit = {},
    isContentLight: Boolean,
    isMenuExpanded: Boolean,
    onMenuExpandedChange: (Boolean) -> Unit,
    onBack: () -> Unit,
    showEducation: Boolean = false,
    onEducationDismissed: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    
    val handleColor = if (isContentLight) {
        Color(0xFF1C1C1E) 
    } else {
        Color.White
    }
    
    val expandedBackgroundColor = if (isContentLight) {
        Color.Black.copy(alpha = 0.75f)
    } else {
        Color.White.copy(alpha = 0.9f)
    }
    
    val expandedContentColor = if (isContentLight) {
        Color.White
    } else {
        Color(0xFF1C1C1E) 
    }

    val handleWidth by animateDpAsState(
        targetValue = if (isMenuExpanded) 24.dp else 40.dp,
        animationSpec = tween(200),
        label = "handle_width"
    )
    
    val handleAreaHeight by animateDpAsState(
        targetValue = if (isMenuExpanded) 8.dp else OVERLAY_TITLE_BAR_HEIGHT_DP.dp,
        animationSpec = tween(200),
        label = "handle_area_height"
    )

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        AnimatedVisibility(
            visible = !isMenuExpanded,
            enter = fadeIn(animationSpec = tween(150)) + expandVertically(animationSpec = tween(150)),
            exit = fadeOut(animationSpec = tween(100)) + shrinkVertically(animationSpec = tween(100))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(OVERLAY_TITLE_BAR_HEIGHT_DP.dp),
                contentAlignment = Alignment.TopCenter
            ) {
                Box(
                    modifier = Modifier
                        .size(width = 120.dp, height = 32.dp)
                        .pointerInput(Unit) {
                            detectDragGestures(
                                onDragStart = { onDragStart() },
                                onDragEnd = { onDragEnd() },
                                onDrag = { change, dragAmount ->
                                    change.consume()
                                    onDrag(dragAmount.x, dragAmount.y)
                                }
                            )
                        }
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) {
                            onMenuExpandedChange(true)
                        },
                    contentAlignment = Alignment.TopCenter
                ) {
                    Box(
                        modifier = Modifier
                            .width(handleWidth)
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(handleColor.copy(alpha = 0.5f))
                    )
                }
            }
        }
        
        AnimatedVisibility(
            visible = showEducation && !isMenuExpanded,
            enter = fadeIn(animationSpec = tween(300)) + 
                    expandVertically(expandFrom = Alignment.Top, animationSpec = tween(300)),
            exit = fadeOut(animationSpec = tween(200)) + 
                   shrinkVertically(shrinkTowards = Alignment.Top, animationSpec = tween(200))
        ) {
            LaunchedEffect(Unit) {
                delay(3000)
                onEducationDismissed()
            }
            
            Box(
                modifier = Modifier
                    .padding(top = 4.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(expandedBackgroundColor)
                    .clickable { 
                        onEducationDismissed()
                        onMenuExpandedChange(true)
                    }
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text(
                    text = "Tap drag handle to show menu",
                    color = expandedContentColor,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
        
        AnimatedVisibility(
            visible = isMenuExpanded,
            enter = fadeIn(animationSpec = tween(200)) + 
                    expandVertically(expandFrom = Alignment.Top, animationSpec = tween(200)) +
                    scaleIn(initialScale = 0.9f, animationSpec = tween(200)),
            exit = fadeOut(animationSpec = tween(150)) + 
                   shrinkVertically(shrinkTowards = Alignment.Top, animationSpec = tween(200)) +
                   scaleOut(targetScale = 0.9f, animationSpec = tween(150))
        ) {
            MenuPill(
                onBack = {
                    onMenuExpandedChange(false)
                    onBack()
                },
                onClose = {

                    onMenuExpandedChange(false)
                    onClose()
                },
                onMinimize = {
                    onMenuExpandedChange(false)
                    onMinimize()
                },
                onBubble = {
                    onMenuExpandedChange(false)
                    onBubble()
                },
                onMaximizeFullscreen = {
                    onMenuExpandedChange(false)
                    onMaximizeFullscreen()
                },
                onCollapse = {
                    onMenuExpandedChange(false)
                },
                backgroundColor = expandedBackgroundColor,
                contentColor = expandedContentColor,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }
}

@Composable
private fun MenuPill(
    onBack: () -> Unit,
    onClose: () -> Unit,
    onMinimize: () -> Unit,
    onBubble: () -> Unit,
    onMaximizeFullscreen: () -> Unit,
    onCollapse: () -> Unit,
    backgroundColor: Color,
    contentColor: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = backgroundColor,
        tonalElevation = 3.dp,
        shadowElevation = 4.dp,
        modifier = modifier.height(40.dp)
    ) {
        Row(
            modifier = Modifier
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            PillIconButton(
                icon = Icons.Rounded.KeyboardArrowUp,
                contentDescription = "Collapse",
                onClick = onCollapse,
                tint = contentColor
            )

            PillIconButton(
                icon = Icons.Rounded.ArrowBack,
                contentDescription = "Back",
                onClick = onBack,
                tint = contentColor
            )
            
            PillIconButton(
                icon = Icons.Rounded.Remove,
                contentDescription = "Minimize",
                onClick = onMinimize,
                tint = contentColor
            )
            
            PillIconButton(
                icon = Icons.Rounded.PictureInPictureAlt,
                contentDescription = "Bubble",
                onClick = onBubble,
                tint = contentColor
            )
            
            PillIconButton(
                icon = Icons.Rounded.OpenInNew,
                contentDescription = "Fullscreen",
                onClick = onMaximizeFullscreen,
                tint = contentColor
            )
            
            PillIconButton(
                icon = Icons.Rounded.Close,
                contentDescription = "Close",
                onClick = onClose,
                tint = contentColor
            )
        }
    }
}

@Composable
private fun PillIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    tint: Color
) {
    IconButton(
        onClick = onClick,
        modifier = Modifier.size(OVERLAY_PILL_ICON_SIZE_DP.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = tint,
            modifier = Modifier.size(20.dp)
        )
    }
}
