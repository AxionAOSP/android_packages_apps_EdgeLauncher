/*
 * Copyright (C) 2025-2026 AxionOS
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
package com.android.edge.bar.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.SportsEsports
import androidx.compose.material.icons.rounded.ViewSidebar
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import android.provider.Settings
import com.android.axion.compose.preferences.ClickablePreference
import com.android.axion.compose.preferences.PreferenceGroup
import com.android.axion.compose.preferences.SecureSettingSwitch
import com.android.axion.compose.preferences.rememberSecureSettingBoolean
import com.android.axion.compose.scaffold.AxionScaffold
import com.android.edge.bar.EdgeService
import com.android.edge.bar.R

@Composable
fun MainSettingsScreen(
    onBack: () -> Unit,
    onNavigateToPinnedApps: () -> Unit,
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()
    val sidebarEnabled = rememberSecureSettingBoolean(EdgeService.SIDELINE, false)

    var showLaunchModeDialog by remember { mutableStateOf(false) }
    var currentLaunchMode by remember {
        mutableStateOf(
            Settings.Secure.getInt(context.contentResolver, EdgeService.LAUNCH_MODE, 0)
        )
    }

    val launchModeOptions = listOf(
        stringResource(R.string.edge_settings_launch_mode_full),
        stringResource(R.string.edge_settings_launch_mode_freeform),
        stringResource(R.string.edge_settings_launch_mode_bubble)
    )

    AxionScaffold(
        title = stringResource(R.string.edge_settings_title),
        onBackClick = onBack,
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(scrollState)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            PreferenceGroup(title = stringResource(R.string.edge_settings_group_general)) {
                item {
                    SecureSettingSwitch(
                        settingKey = EdgeService.SIDELINE,
                        title = stringResource(R.string.edge_settings_sidebar_title),
                        summary = stringResource(R.string.edge_settings_sidebar_summary),
                        icon = Icons.Rounded.ViewSidebar,
                    )
                }
                item {
                    ClickablePreference(
                        title = stringResource(R.string.edge_settings_pinned_title),
                        summary = stringResource(R.string.edge_settings_pinned_summary),
                        icon = Icons.Rounded.PushPin,
                        enabled = sidebarEnabled,
                        onClick = onNavigateToPinnedApps,
                    )
                }
            }

            PreferenceGroup(title = stringResource(R.string.edge_settings_group_gaming)) {
                item {
                    SecureSettingSwitch(
                        settingKey = EdgeService.HIDE_IN_GAMING_MODE,
                        title = stringResource(R.string.edge_settings_hide_in_gamespace_title),
                        summary = stringResource(R.string.edge_settings_hide_in_gamespace_summary),
                        icon = Icons.Rounded.SportsEsports,
                        defaultValue = true,
                    )
                }
            }

            PreferenceGroup(title = stringResource(R.string.edge_settings_launch_mode_title)) {
                item {
                    ClickablePreference(
                        title = stringResource(R.string.edge_settings_launch_mode_title),
                        summary = launchModeOptions.getOrElse(currentLaunchMode) { launchModeOptions[0] },
                        icon = Icons.Rounded.OpenInNew,
                        onClick = { showLaunchModeDialog = true }
                    )
                }
            }
        }
    }

    if (showLaunchModeDialog) {
        AlertDialog(
            onDismissRequest = { showLaunchModeDialog = false },
            title = { Text(text = stringResource(R.string.edge_settings_launch_mode_title)) },
            text = {
                Column {
                    launchModeOptions.forEachIndexed { index, label ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    currentLaunchMode = index
                                    Settings.Secure.putInt(
                                        context.contentResolver,
                                        EdgeService.LAUNCH_MODE,
                                        index
                                    )
                                    showLaunchModeDialog = false
                                }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = (index == currentLaunchMode),
                                onClick = null
                            )
                            Text(
                                text = label,
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.padding(start = 16.dp)
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showLaunchModeDialog = false }) {
                    Text(text = "Cancel")
                }
            }
        )
    }
}
