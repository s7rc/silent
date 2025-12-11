package com.swordfish.lemuroid.app.mobile.feature.gamemenu

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.swordfish.lemuroid.R
import com.swordfish.lemuroid.app.shared.GameMenuContract
import com.swordfish.lemuroid.app.utils.android.settings.LemuroidSettingsMenuLink
import com.swordfish.lemuroid.app.utils.android.settings.LemuroidSettingsSlider
import com.swordfish.lemuroid.lib.controller.TouchControllerSettingsManager
import com.swordfish.touchinput.radial.settings.TouchControllerSettingsManager.Companion.DEFAULT_SCALE
import kotlin.math.roundToInt
import kotlin.reflect.KFunction1

@Composable
fun GameMenuLayoutScreen(
    touchSettings: TouchControllerSettingsManager.Settings?,
    onResult: KFunction1<Intent.() -> Unit, Unit>,
) {
    if (touchSettings == null) {
        // Fallback if no settings available (e.g. controller not active)
        Text("No touch settings available.")
        return
    }

    // Local mutable state for the slider
    // We send updates immediately on change or just on unmount?
    // MobileGameScreen uses a callback to update settings continuously.
    // Here we are in a separate activity. We can send RESULT on every change?
    // Intent passing is cheap enough for a slider drag? Maybe throttled?
    // User expects "Live" preview? No, the game is covered by the menu.
    // So distinct updates are fine.
    
    val currentScale = remember { mutableStateOf(touchSettings.scale) }
    val currentOpacity = remember { mutableStateOf(touchSettings.opacity) }

    Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
        
        // Opacity Slider
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Opacity: ${(currentOpacity.value * 100).roundToInt()}%",
                style = androidx.compose.material3.MaterialTheme.typography.titleMedium
            )
            androidx.compose.material3.Slider(
                value = currentOpacity.value,
                onValueChange = { newValue ->
                    currentOpacity.value = newValue
                },
                valueRange = 0.1f..1.0f
            )
        }

        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Global Scale: ${(currentScale.value * 100).roundToInt()}%",
                style = androidx.compose.material3.MaterialTheme.typography.titleMedium
            )
            androidx.compose.material3.Slider(
                value = currentScale.value,
                onValueChange = { newValue ->
                    currentScale.value = newValue
                },
                valueRange = 0.2f..1.5f
            )
        }

        // Save Button
        LemuroidSettingsMenuLink(
            title = { Text(text = stringResource(id = R.string.touch_customize_button_done)) },
            onClick = {
                val newSettings = touchSettings.copy(scale = currentScale.value, opacity = currentOpacity.value)
                onResult {
                     putExtra(GameMenuContract.RESULT_UPDATE_TOUCH_SETTINGS, newSettings)
                }
            }
        )

        // Reset Button
        LemuroidSettingsMenuLink(
            title = { Text(text = stringResource(id = R.string.touch_customize_button_reset)) },
            onClick = {
                val defaultSettings = TouchControllerSettingsManager.Settings() 
                
                // Update local state
                currentScale.value = defaultSettings.scale
                currentOpacity.value = defaultSettings.opacity
                
                onResult { 
                    putExtra(GameMenuContract.RESULT_UPDATE_TOUCH_SETTINGS, defaultSettings)
                }
            }
        )
    }
}
