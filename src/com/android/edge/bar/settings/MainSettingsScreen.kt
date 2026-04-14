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

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.SportsEsports
import androidx.compose.material.icons.rounded.ViewSidebar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
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
        }
    }
}
