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

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.android.axion.compose.theme.AxionTheme

class SettingsActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            var currentScreen by remember { mutableStateOf<SettingsScreen>(SettingsScreen.Main) }

            AxionTheme {
                when (currentScreen) {
                    is SettingsScreen.Main -> {
                        MainSettingsScreen(
                            onBack = { finish() },
                            onNavigateToPinnedApps = { currentScreen = SettingsScreen.PinnedApps },
                        )
                    }
                    is SettingsScreen.PinnedApps -> {
                        PinnedAppsScreen(
                            onBack = { currentScreen = SettingsScreen.Main },
                        )
                    }
                }
            }
        }
    }
}

sealed class SettingsScreen {
    object Main : SettingsScreen()
    object PinnedApps : SettingsScreen()
}
