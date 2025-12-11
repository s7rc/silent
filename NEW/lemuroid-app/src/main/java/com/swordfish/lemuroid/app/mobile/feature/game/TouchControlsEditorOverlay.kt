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
    onSettingsChanged: (TouchControllerSettingsManager.Settings) -> Unit,
    onExit: () -> Unit
) {
    val boundsMap = LocalTouchElementBounds.current
    val currentSettings by rememberUpdatedState(settings)
    var activeId by remember { mutableStateOf<String?>(null) }
    
    // Track our own global offset and Exit Button bounds
    var overlayOffset by remember { mutableStateOf(Offset.Zero) }
    var exitButtonBounds by remember { mutableStateOf(Rect.Zero) }

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
                            val down = changes.first { it.pressed }
                            val globalTouch = down.position + overlayOffset
                            
                            // CHECK EXIT BUTTON: If touching the button, LET IT PASS (Do not consume)
                            // This allows the standard onClick to fire.
                            if (exitButtonBounds.contains(globalTouch)) {
                                continue
                            }
                            
                            // ... (Rest of logic: Hit Test, Drag Loop) ...
                            // 3. Coordinate Logic
                            var currentActiveId = findIdAt(globalTouch)
                            
                            // Fuzzy Search (Easier Grabbing)
                            if (currentActiveId == null) {
                                currentActiveId = boundsMap.entries.firstOrNull { (_, rect) ->
                                    rect.inflate(150f).contains(globalTouch)
                                }?.key
                            }
                            
                            activeId = currentActiveId
                            
                            // 4. Drag / Scale Loop
                            val startState = if (currentActiveId != null) {
                                currentSettings.elements[currentActiveId] ?: TouchControllerSettingsManager.ElementSettings()
                            } else null
                            
                            if (currentActiveId != null && startState != null) {
                                var accumulatedPan = Offset.Zero
                                var accumulatedZoom = 1f
                                
                                while (true) {
                                    val dragEvent = awaitPointerEvent(androidx.compose.ui.input.pointer.PointerEventPass.Initial)
                                    
                                    if (dragEvent.changes.all { !it.pressed }) {
                                        break
                                    }

                                    // A. Calculate Delta
                                    val zoomChange = dragEvent.calculateZoom()
                                    val panChange = dragEvent.calculatePan()
                                    
                                    // B. CONSUME
                                    dragEvent.changes.forEach { it.consume() }
                                    
                                    accumulatedZoom *= zoomChange
                                    accumulatedPan += panChange
                                    
                                    var newScale = (startState.scale * accumulatedZoom)
                                        .coerceIn(0.5f, 2.5f) // Sane limits
                                    
                                    // ...
                                    val newSettings = currentSettings.copy(
                                        elements = currentSettings.elements + (currentActiveId to startState.copy(
                                            x = (startState.x + newXDelta).coerceIn(0f, 1f), // Normalized
                                            y = (startState.y + newYDelta).coerceIn(0f, 1f),
                                            scale = newScale
                                        ))
                                    )
                                    onSettingsChanged(newSettings)
                                }
                            } else {
                                // Block Game
                                while (true) {
                                     val dragEvent = awaitPointerEvent(androidx.compose.ui.input.pointer.PointerEventPass.Initial)
                                     dragEvent.changes.forEach { it.consume() }
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
        // VISUALS
        // ... (Existing DrawRect logic for selection) ...
        androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
            activeId?.let { id ->
                boundsMap[id]?.let { rect ->
                    // Draw Selection Box relative to Overlay
                    val localTopLeft = rect.topLeft - overlayOffset
                    drawRect(
                        color = androidx.compose.ui.graphics.Color.Green,
                        topLeft = localTopLeft,
                        size = androidx.compose.ui.geometry.Size(rect.width, rect.height),
                        style = androidx.compose.ui.graphics.drawscope.Stroke(width = 4.dp.toPx())
                    )
                }
            }
        }
        
        // EXIT BUTTON
        androidx.compose.material3.ExtendedFloatingActionButton(
            onClick = onExit,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(16.dp)
                .onGloballyPositioned { exitButtonBounds = it.boundsInRoot() },
            icon = { 
                androidx.compose.material3.Icon(
                    androidx.compose.material.icons.Icons.Default.Check, 
                    contentDescription = "Done"
                ) 
            },
            text = { androidx.compose.material3.Text(text = "Done") }
        )
    }
}hanged = false
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
