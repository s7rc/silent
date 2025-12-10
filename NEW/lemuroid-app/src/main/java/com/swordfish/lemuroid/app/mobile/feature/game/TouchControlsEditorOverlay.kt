package com.swordfish.lemuroid.app.mobile.feature.game

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
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
                detectTransformGestures { centroid, pan, zoom, _ ->
                    // 1. Acquire Selection if needed
                    if (activeId == null) {
                        activeId = findIdAt(centroid)
                    }
                    val id = activeId ?: return@detectTransformGestures
                    
                    // 2. Get current state
                    val safeSettings = currentSettings
                    val currentElement = safeSettings.elements[id] ?: TouchControllerSettingsManager.ElementSettings()
                    
                    var changed = false
                    var newScale = currentElement.scale
                    var newX = currentElement.x
                    var newY = currentElement.y

                    // 3. Handle Zoom
                    if (zoom != 1f) {
                         newScale = (currentElement.scale * zoom).coerceIn(0.5f, 2.5f)
                         changed = true
                    }
                    
                    // 4. Handle Pan (Move)
                    if (pan != Offset.Zero) {
                         val widthStr = size.width.toFloat()
                         val heightStr = size.height.toFloat()
                         
                         if (widthStr > 0 && heightStr > 0) {
                            var startX = currentElement.x
                            var startY = currentElement.y
                            
                            // Initialize position from bounds if not set
                            if (startX < 0 || startY < 0) {
                                val rect = boundsMap[id]
                                if (rect != null) {
                                    startX = (rect.center.x / widthStr)
                                    startY = (rect.center.y / heightStr)
                                }
                            }
                            
                            if (startX >= 0 && startY >= 0) {
                                val dx = pan.x / widthStr
                                val dy = pan.y / heightStr
                                // We use the *accumulated* position approach?
                                // No, detectTransformGestures gives relative 'pan' delta.
                                // So new = old + delta.
                                // BUT 'old' is 'currentElement.x', which is state.
                                // This works fine.
                                
                                newX = (startX + dx).coerceIn(0f, 1f)
                                newY = (startY + dy).coerceIn(0f, 1f)
                                changed = true
                            }
                         }
                    }
                    
                    if (changed) {
                        val newElement = currentElement.copy(x = newX, y = newY, scale = newScale)
                        val newElements = safeSettings.elements.toMutableMap()
                        newElements[id] = newElement
                        onSettingsChanged(safeSettings.copy(elements = newElements))
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
    ) {
        // Visuals for editor? Maybe draw boxes around detected elements?
        // Optional but nice.
    }
}
