package com.swordfish.lemuroid.app.mobile.feature.game

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.pointer.pointerInput
import com.swordfish.touchinput.radial.layouts.LocalTouchElementBounds
import com.swordfish.touchinput.radial.settings.TouchControllerSettingsManager

@Composable
fun TouchControlsEditorOverlay(
    modifier: Modifier = Modifier,
    settings: TouchControllerSettingsManager.Settings,
    onSettingsChanged: (TouchControllerSettingsManager.Settings) -> Unit
) {
    val boundsMap = LocalTouchElementBounds.current
    val currentSettings by rememberUpdatedState(settings)
    // We need to track the active selection across the gesture
    var activeId by remember { mutableStateOf<String?>(null) }
    
    // HelperS to find ID at point using LATEST bounds map
    // Note: boundsMap is mutable, so we read it directly.
    fun findIdAt(offset: Offset): String? {
        return boundsMap.entries.firstOrNull { (_, rect) ->
            rect.contains(offset)
        }?.key
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                // Track gesture accumulation to prevent stutter/lag
                var initialElement: TouchControllerSettingsManager.ElementSettings? = null
                var accumulatedPan = Offset.Zero
                var accumulatedZoom = 1f

                detectTransformGestures { centroid, pan, zoom, _ ->
                    // 1. Acquire Selection if needed
                    if (activeId == null) {
                        activeId = findIdAt(centroid)
                        // Snapshot state on start
                        if (activeId != null) {
                            initialElement = currentSettings.elements[activeId]
                            accumulatedPan = Offset.Zero
                            accumulatedZoom = 1f
                        }
                    }
                    val id = activeId ?: return@detectTransformGestures
                    val startState = initialElement ?: return@detectTransformGestures
                    
                    // 2. Accumulate changes
                    accumulatedPan += pan
                    accumulatedZoom *= zoom

                    var changed = false
                    
                    // 3. Calc Scale (Relative to Initial)
                    val newScale = (startState.scale * accumulatedZoom).coerceIn(0.5f, 2.5f)
                    if (newScale != startState.scale) changed = true
                    
                    // 4. Calc Position (Relative to Initial)
                    var newX = startState.x
                    var newY = startState.y
                    
                    val widthStr = size.width.toFloat()
                    val heightStr = size.height.toFloat()
                    
                    if (widthStr > 0 && heightStr > 0) {
                        // Current logic assumes startState.x is valid (0..1)
                        // If -1, we need to resolve from bounds ONCE at start?
                        // If startState.x < 0, we can't easily drag relatively without resolving center.
                        // Handling uninitialized positions:
                        if (startState.x < 0 || startState.y < 0) {
                            val rect = boundsMap[id]
                            if (rect != null) {
                                // Update 'startState' to have resolved position so we can offset from it?
                                // No, 'startState' is immutable.
                                // We need a 'resolvedStartX' separate var.
                                // Let's simplify: if x<0, try to resolve it now and use it as base.
                                val resolvedX = rect.center.x / widthStr
                                val resolvedY = rect.center.y / heightStr
                                // Update our snapshot so future deltas work
                                initialElement = startState.copy(x = resolvedX, y = resolvedY)
                                // Next loop will use this. This frame might skip? No, retry calculation:
                                newX = resolvedX + (accumulatedPan.x / widthStr)
                                newY = resolvedY + (accumulatedPan.y / heightStr)
                            }
                        } else {
                             // Normal case
                             newX = startState.x + (accumulatedPan.x / widthStr)
                             newY = startState.y + (accumulatedPan.y / heightStr)
                        }
                        
                        newX = newX.coerceIn(0f, 1f)
                        newY = newY.coerceIn(0f, 1f)
                        changed = true
                    }
                    
                    // 5. Apply
                    // Note: We always apply "New Absolute" based on "Initial + Delta"
                    if (changed) {
                        val newElement = startState.copy(x = newX, y = newY, scale = newScale)
                        val newElements = currentSettings.elements.toMutableMap()
                        newElements[id] = newElement
                        onSettingsChanged(currentSettings.copy(elements = newElements))
                    }
                }
            }
            .pointerInput(Unit) {
                 // Reset selection when all fingers lift
                 awaitEachGesture {
                      awaitFirstDown(requireUnconsumed = false)
                      do {
                          val event = awaitPointerEvent()
                      } while (event.changes.any { it.pressed })
                      activeId = null
                 }
            }
            .pointerInput(Unit) {
                // Consume taps so they don't click the game buttons underneath
                detectTapGestures { } 
            }
    ) {
        // Visuals for editor? Maybe draw boxes around detected elements?
        // Optional but nice.
    }
}
