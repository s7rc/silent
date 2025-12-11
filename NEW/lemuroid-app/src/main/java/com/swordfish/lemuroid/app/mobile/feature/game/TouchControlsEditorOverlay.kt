package com.swordfish.lemuroid.app.mobile.feature.game

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
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
    
    // Track our own global offset and size
    var overlayOffset by remember { mutableStateOf(Offset.Zero) }
    var overlaySize by remember { mutableStateOf(IntSize.Zero) }
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
                 overlaySize = it.size
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
                                    
                                    val newScale = (startState.scale * accumulatedZoom)
                                        .coerceIn(0.2f, 3.0f) 

                                    // Calc normalized position
                                    val width = overlaySize.width.toFloat()
                                    val height = overlaySize.height.toFloat()
                                    
                                    val newX: Float
                                    val newY: Float
                                    
                                    if (width > 0 && height > 0) {
                                        // If startState.x is negative (default/unset), we need to resolve it first?
                                        // Actually, if it's default, we want to set it now.
                                        // But startState came from `settings.elements[...]` which might be null -> default ElementSettings() -> x=-1f.
                                        
                                        val startX = if (startState.x < 0) {
                                            // Resolve logical default position from bounds if available?
                                            // Or just use 0.5?
                                            // If key exists in boundsMap, we can reverse engineer current pos.
                                            val rect = boundsMap[currentActiveId]
                                            if (rect != null) {
                                                ((rect.center.x - overlayOffset.x) / width)
                                            } else 0.5f // Fallback
                                        } else startState.x
                                        
                                        val startY = if (startState.y < 0) {
                                            val rect = boundsMap[currentActiveId]
                                            if (rect != null) {
                                                ((rect.center.y - overlayOffset.y) / height)
                                            } else 0.5f
                                        } else startState.y

                                        newX = (startX + (accumulatedPan.x / width)).coerceIn(0f, 1f)
                                        newY = (startY + (accumulatedPan.y / height)).coerceIn(0f, 1f)
                                    } else {
                                        newX = startState.x
                                        newY = startState.y
                                    }
                                    
                                    // ...
                                    val newSettings = currentSettings.copy(
                                        elements = currentSettings.elements + (currentActiveId to startState.copy(
                                            x = newX,
                                            y = newY,
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
        Canvas(modifier = Modifier.fillMaxSize()) {
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
        ExtendedFloatingActionButton(
            onClick = onExit,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(16.dp)
                .onGloballyPositioned { exitButtonBounds = it.boundsInRoot() },
            icon = { 
                Icon(
                    Icons.Default.Check, 
                    contentDescription = "Done"
                ) 
            },
            text = { Text(text = "Done") }
        )
    }
}
