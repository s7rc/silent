package com.swordfish.lemuroid.app.mobile.feature.game

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
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
    
    // Helper to find ID at point using LATEST bounds map
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
                awaitEachGesture {
                    // 1. Wait for Down and CONSUME IT IMMEDIATELY to prevent Game Input
                    val down = awaitFirstDown(requireUnconsumed = false)
                    down.consume()
                    
                    // 2. Initial Hit Test
                    var activeId = findIdAt(down.position)
                    
                    // 3. Snapshot State
                    var initialElement: TouchControllerSettingsManager.ElementSettings? = null
                    if (activeId != null) {
                        initialElement = currentSettings.elements[activeId]
                    }
                    
                    var accumulatedPan = Offset.Zero
                    var accumulatedZoom = 1f

                    // 4. Gesture Loop
                    do {
                        val event = awaitPointerEvent()
                        
                        // Calculate transformations BEFORE consuming
                        val zoomChange = event.calculateZoom()
                        val panChange = event.calculatePan()
                        
                        // CONSUME ALL EVENTS to prevent pass-through
                        event.changes.forEach { it.consume() }

                        if (activeId != null && initialElement != null) {
                            accumulatedZoom *= zoomChange
                            accumulatedPan += panChange
                            
                            val startState = initialElement!!
                            var changed = false
                            
                            // Calc Scale
                            val newScale = (startState.scale * accumulatedZoom).coerceIn(0.5f, 2.5f)
                            if (newScale != startState.scale) changed = true
                            
                            // Calc Position
                            var newX = startState.x
                            var newY = startState.y
                            
                            val widthStr = size.width.toFloat()
                            val heightStr = size.height.toFloat()
                            
                            if (widthStr > 0 && heightStr > 0) {
                                if (startState.x < 0 || startState.y < 0) {
                                    val rect = boundsMap[activeId]
                                    if (rect != null) {
                                        val resolvedX = rect.center.x / widthStr
                                        val resolvedY = rect.center.y / heightStr
                                        initialElement = startState.copy(x = resolvedX, y = resolvedY)
                                        // Re-calc with resolved base (next iteration will be cleaner, but do this one now)
                                        newX = resolvedX + (accumulatedPan.x / widthStr)
                                        newY = resolvedY + (accumulatedPan.y / heightStr)
                                    }
                                } else {
                                     newX = startState.x + (accumulatedPan.x / widthStr)
                                     newY = startState.y + (accumulatedPan.y / heightStr)
                                }
                                
                                newX = newX.coerceIn(0f, 1f)
                                newY = newY.coerceIn(0f, 1f)
                                changed = true
                            }
                            
                            if (changed) {
                                val newElement = startState.copy(x = newX, y = newY, scale = newScale)
                                val newElements = currentSettings.elements.toMutableMap()
                                newElements[activeId!!] = newElement
                                onSettingsChanged(currentSettings.copy(elements = newElements))
                            }
                        }
                    } while (event.changes.any { it.pressed })
                }
            }
    ) {
        // Visuals for editor? Maybe draw boxes around detected elements?
        // Optional but nice.
    }
}
