package com.swordfish.lemuroid.app.mobile.feature.game

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.Canvas
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
    var activeId by remember { mutableStateOf<String?>(null) }
    
    // Track our own global offset to align coordinates
    var overlayOffset by remember { mutableStateOf(Offset.Zero) }

    // Helper to find ID at point (Global Coordinates)
    fun findIdAt(globalOffset: Offset): String? {
        return boundsMap.entries.firstOrNull { (_, rect) ->
            rect.contains(globalOffset)
        }?.key
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .onGloballyPositioned { 
                 overlayOffset = it.boundsInRoot().topLeft 
            }
            .pointerInput(Unit) {
                awaitEachGesture {
                    // 1. Wait for Down and CONSUME IT IMMEDIATELY to prevent Game Input
                    val down = awaitFirstDown(requireUnconsumed = false)
                    down.consume()
                    
                    // 2. Initial Hit Test (Global Coords)
                    val globalTouch = down.position + overlayOffset
                    var currentActiveId = findIdAt(globalTouch)
                    
                    // Fuzzy Search (Expand hit area by ~50px if missed)
                    if (currentActiveId == null) {
                        currentActiveId = boundsMap.entries.firstOrNull { (_, rect) ->
                            rect.inflate(50f).contains(globalTouch)
                        }?.key
                    }
                    
                    activeId = currentActiveId
                    
                    // 3. Snapshot State
                    var initialElement: TouchControllerSettingsManager.ElementSettings? = null
                    if (currentActiveId != null) {
                        initialElement = currentSettings.elements[currentActiveId]
                    }
                    
                    var accumulatedPan = Offset.Zero
                    var accumulatedZoom = 1f

                    // 4. Gesture Loop
                    do {
                        val event = awaitPointerEvent()
                        val zoomChange = event.calculateZoom()
                        val panChange = event.calculatePan()
                        
                        // CRITICAL: Consume ALL events to prevent them falling through to the game
                        event.changes.forEach { it.consume() }

                        if (currentActiveId != null && initialElement != null) {
                            accumulatedZoom *= zoomChange
                            accumulatedPan += panChange
                            
                            val startState = initialElement!!
                            var changed = false
                            
                            // Scale logic
                            val newScale = (startState.scale * accumulatedZoom).coerceIn(0.5f, 2.5f)
                            if (newScale != startState.scale) changed = true
                            
                            // Position logic
                            val widthStr = size.width.toFloat()
                            val heightStr = size.height.toFloat()
                            
                            var newX = startState.x
                            var newY = startState.y
                            
                            if (widthStr > 0 && heightStr > 0) {
                                // Logic: Position is 0..1 relative to container.
                                // We need to handle resolving absolute positions if they haven't been set yet (negative values)
                                
                                if (startState.x < 0 || startState.y < 0) {
                                    val rect = boundsMap[currentActiveId]
                                    if (rect != null) {
                                        // Resolve based on center relative to THIS overlay
                                        val localCenter = rect.center - overlayOffset
                                        val resolvedX = localCenter.x / widthStr
                                        val resolvedY = localCenter.y / heightStr
                                        
                                        initialElement = startState.copy(x = resolvedX, y = resolvedY)
                                        // Update accumulation base
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
                                newElements[currentActiveId!!] = newElement
                                onSettingsChanged(currentSettings.copy(elements = newElements))
                            }
                        }
                    } while (event.changes.any { it.pressed })
                    
                    activeId = null
                }
            }
    ) {
        // Visual Feedback
        Canvas(modifier = Modifier.fillMaxSize()) {
            boundsMap.forEach { (key, rect) ->
                // Convert Global Rect to Local Rect for drawing
                val localTopLeft = rect.topLeft - overlayOffset
                val localSize = rect.size
                
                val isSelected = (key == activeId)
                val color = if (isSelected) androidx.compose.ui.graphics.Color.Green else androidx.compose.ui.graphics.Color.White.copy(alpha = 0.5f)
                val strokeWidth = if (isSelected) 4.dp.toPx() else 2.dp.toPx()
                
                drawRect(
                    color = color,
                    topLeft = localTopLeft,
                    size = localSize,
                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = strokeWidth)
                )
            }
        }
    }
}
