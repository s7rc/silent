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
                            // Always consume to BLOCK GAME INPUT regardless of hit
                            changes.forEach { it.consume() }

                            // 3. Coordinate Logic
                            val globalTouch = down.position + overlayOffset
                            var currentActiveId = findIdAt(globalTouch)
                            
                            if (currentActiveId == null) {
                                currentActiveId = boundsMap.entries.firstOrNull { (_, rect) ->
                                    // Increased tolerance to 150f (~50dp) to make grabbing easier
                                    rect.inflate(150f).contains(globalTouch)
                                }?.key
                            }
                            
                            activeId = currentActiveId
                            
                            // 4. Drag / Scale Loop
                            // We stay in this loop until all fingers lift.
                            // We use Initial pass everywhere to maintain blockade.
                            if (currentActiveId != null) {
                                
                                var initialElement: TouchControllerSettingsManager.ElementSettings? = currentSettings.elements[currentActiveId]
                                var accumulatedPan = Offset.Zero
                                var accumulatedZoom = 1f
                                
                                val startState = initialElement
                                
                                if (startState != null) {
                                    while (true) {
                                        val dragEvent = awaitPointerEvent(androidx.compose.ui.input.pointer.PointerEventPass.Initial)
                                    while (true) {
                                        val dragEvent = awaitPointerEvent(androidx.compose.ui.input.pointer.PointerEventPass.Initial)
                                        
                                        // Check if we finished
                                        if (dragEvent.changes.all { !it.pressed }) {
                                            break
                                        }

                                        // 1. Calculate Delta FIRST (while event is unconsumed)
                                        val zoomChange = dragEvent.calculateZoom()
                                        val panChange = dragEvent.calculatePan()
                                        
                                        // 2. CONSUME EVERYTHING (to block game)
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
                                                    
                                                    // Re-base start state ?? No, startState is constant.
                                                    // We need to bake the resolved base into the calc.
                                                    
                                                    // This logic was slightly fragile in previous loop.
                                                    // Simpler: Just track absolute value from resolved base.
                                                    
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
                                     // Found ID but no settings? Just block loop.
                                     while (awaitPointerEvent(androidx.compose.ui.input.pointer.PointerEventPass.Initial).changes.any { it.pressed }) {
                                         // drain
                                     }
                                }
                            } else {
                                // missed hit: just block interactions until lift
                                 while (awaitPointerEvent(androidx.compose.ui.input.pointer.PointerEventPass.Initial).changes.any { it.pressed }) {
                                     // drain
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
