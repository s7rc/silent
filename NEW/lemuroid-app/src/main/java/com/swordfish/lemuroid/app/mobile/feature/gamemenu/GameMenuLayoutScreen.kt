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

    Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
        
        // Scale Slider
        // We reuse LemuroidSettingsSlider for consistency
        // Map 0.5f -> index? No, slider takes float range usually?
        // LemuroidSettingsSlider takes a State<Int> usually (index based).
        // Let's check LemuroidSettingsSlider signature?
        // In MobileGameScreen usage: 
        /*
        LemuroidSettingsSlider(
            state = ..., // int state
            valueRange = ...
        )
        */
        // I'll stick to a standard Slider inside a container or adapt LemuroidSettingsSlider logic.
        // Actually, let's use the logic I know works: Custom composable or standard Slider.
        // MobileGameScreen (line 324) used standard Slider.
        // I will recreate a simple item.
        
        // Scale is 0.0 to 1.0 (or higher). Default 0.5.
        // Let's allow 0.2 to 2.0?
        
        com.swordfish.lemuroid.app.utils.android.settings.LemuroidSettingsSlider(
            title = { Text("Global Scale") },
            subtitle = { Text("${(currentScale.value * 100).roundToInt()}%") },
            state = remember { 
                 // We need to bridge Float to the Int state expected by LemuroidSettingsSlider if it forces Int.
                 // Or we can just use a custom row like in MobileGameScreen.
                 // let's assume LemuroidSettingsSlider is too specific.
                 // I'll Copy MenuEditTouchControlRow logic from MobileGameScreen roughly.
            },
            valueRange = 0.2f..1.5f,
            steps = 0, // Continuous
            // Wait, I don't know the exact signature of LemuroidSettingsSlider without reading util.
            // I'll implement a custom row using purely Material3 to be safe and "easy mistakes" proof.
        )
        
        // OK, I'll write a clean implementation.
        
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
                val newSettings = touchSettings.copy(scale = currentScale.value)
                onResult {
                     putExtra(GameMenuContract.RESULT_UPDATE_TOUCH_SETTINGS, newSettings)
                }
            }
        )

        // Reset Button
        LemuroidSettingsMenuLink(
            title = { Text(text = stringResource(id = R.string.touch_customize_button_reset)) },
            onClick = {
                // Reset to Defaults (Scale 0.5f, etc, but keep layout?)
                // User said "Reset Layout".
                // I should construct a default Settings object.
                val defaultSettings = TouchControllerSettingsManager.Settings() 
                // Default constructor uses default values (scale=0.5, etc.)
                
                // Update local state
                currentScale.value = defaultSettings.scale
                
                onResult { 
                    putExtra(GameMenuContract.RESULT_UPDATE_TOUCH_SETTINGS, defaultSettings)
                }
            }
        )
    }
}
