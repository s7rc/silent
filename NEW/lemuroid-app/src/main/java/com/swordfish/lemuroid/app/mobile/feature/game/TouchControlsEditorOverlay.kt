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
                awaitPointerEventScope {
                    while (true) {
                        // 1. Wait for ANY touch event in INITIAL pass (God Mode Priority)
                        val event = awaitPointerEvent(androidx.compose.ui.input.pointer.PointerEventPass.Initial)
                        val changes = event.changes
                        
                        // If any finger is down, we must check if we should intercept
                        if (changes.any { it.pressed }) {
                            
                            // 2. Hit Test immediately
                            val down = changes.first { it.pressed } // Take the first pressed pointer
                            
                            // Always consume to BLOCK GAME INPUT regardless of hit.
                            // However, we consume AFTER logic in the loop below to allow calculation.
                            // But here (initial contact), we want to consume to claim ownership?
                            // No, if we consume here, the loop below sees consumed events.
                            
                            // Strategy: We loop until lift. IN the loop, we calc then consume.
                            
                            // 3. Coordinate Logic
                            val globalTouch = down.position + overlayOffset
                            var currentActiveId = findIdAt(globalTouch)
                            
                            // Fuzzy Search (Easier Grabbing)
                            if (currentActiveId == null) {
                                currentActiveId = boundsMap.entries.firstOrNull { (_, rect) ->
                                    rect.inflate(150f).contains(globalTouch)
                                }?.key
                            }
                            
                            activeId = currentActiveId
                            
                            // 4. Drag / Scale Loop
                            // We stay in this loop until all fingers lift.
                            // If settings are missing (first time), default to empty so we can resolve them.
                            val startState = if (currentActiveId != null) {
                                currentSettings.elements[currentActiveId] ?: TouchControllerSettingsManager.ElementSettings()
                            } else null
                            
                            if (currentActiveId != null && startState != null) {
                                var accumulatedPan = Offset.Zero
                                var accumulatedZoom = 1f
                                
                                while (true) {
                                    val dragEvent = awaitPointerEvent(androidx.compose.ui.input.pointer.PointerEventPass.Initial)
                                    
                                    // Check if we finished (all fingers up)
                                    if (dragEvent.changes.all { !it.pressed }) {
                                        break
                                    }

                                    // A. Calculate Delta FIRST (Use unconsumed events)
                                    val zoomChange = dragEvent.calculateZoom()
                                    val panChange = dragEvent.calculatePan() // This requires unconsumed events
                                    
                                    // B. CONSUME EVERYTHING IMMEDIATELY AFTER CALC
                                    // This prevents the game from seeing the move.
                                    dragEvent.changes.forEach { it.consume() }
                                    
                                    accumulatedZoom *= zoomChange
                                    accumulatedPan += panChange
                                    
                                    var changed = false
                                    val newScale = (startState.scale * accumulatedZoom).coerceIn(0.5f, 2.5f)
                                    if (newScale != startState.scale) changed = true
                                    
                                    var newX = startState.x
                                    var newY = startState.y
                                    
                                    val widthStr = size.width.toFloat()
                                    val heightStr = size.height.toFloat()
                                    
                                    if (widthStr > 0 && heightStr > 0) {
                                        if (startState.x < 0 || startState.y < 0) {
                                            val rect = boundsMap[currentActiveId]
                                            if (rect != null) {
                                                val localCenter = rect.center - overlayOffset
                                                val resolvedX = localCenter.x / widthStr
                                                val resolvedY = localCenter.y / heightStr
                                                
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
                            } else {
                                // Missed hit or no settings: Just block interaction until lift.
                                while (true) {
                                     val dragEvent = awaitPointerEvent(androidx.compose.ui.input.pointer.PointerEventPass.Initial)
                                     dragEvent.changes.forEach { it.consume() } // Block Game
                                     if (dragEvent.changes.all { !it.pressed }) {
                                         break
                                     }
                                }
                            }
                            
                            activeId = null
                        }
                    }
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
