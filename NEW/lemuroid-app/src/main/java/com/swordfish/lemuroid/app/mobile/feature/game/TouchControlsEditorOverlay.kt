package com.swordfish.lemuroid.app.mobile.feature.game

import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
    var selectedId by remember { mutableStateOf<String?>(null) }
    var initialElementSettings by remember { mutableStateOf<TouchControllerSettingsManager.ElementSettings?>(null) }
    
    // Helper to find ID at point
    fun findIdAt(offset: Offset): String? {
        // Iterate map
        return boundsMap.entries.firstOrNull { (_, rect) ->
            rect.contains(offset)
        }?.key
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(settings) {
                detectDragGestures(
                    onDragStart = { offset ->
                        selectedId = findIdAt(offset)
                        if (selectedId != null) {
                            initialElementSettings = settings.elements[selectedId!!] ?: TouchControllerSettingsManager.ElementSettings()
                        }
                    },
                    onDrag = { change, dragAmount ->
                        val id = selectedId ?: return@detectDragGestures
                        val current = settings.elements[id] ?: TouchControllerSettingsManager.ElementSettings()
                        
                        // Convert dragAmount (px) to Normalized (0..1)
                        // Issue: We need container size. 
                        // pointerInput scope doesn't expose size directly?
                        // We can get size from `size` property of PointerInputScope? No.
                        // We can use a BoxWithConstraints wrapper or onGloballyPositioned on this overlay.
                        // Assuming this overlay fills the screen (Game Screen).
                        
                        // Using 'size' from PointerInputScope available in detectDragGestures? No.
                        // But 'size' is available in PointerInputScope.
                        val widthStr = size.width.toFloat()
                        val heightStr = size.height.toFloat()
                        
                        if (widthStr > 0 && heightStr > 0) {
                            var startX = current.x
                            var startY = current.y
                            
                            // If currently unset (-1), we need to resolve it.
                            // BUT 'current' comes from settings. If it's -1, we don't know where it IS visually
                            // unless we read the bound's center.
                            
                            if (startX < 0 || startY < 0) {
                                val rect = boundsMap[id]
                                if (rect != null) {
                                    startX = (rect.center.x / widthStr)
                                    startY = (rect.center.y / heightStr)
                                    // The ElementSettings stores CENTER position (implied by my calculation in IndependentRadialLayout).
                                    // In IndependentRadialLayout: x = (settings.x * w) - width/2. 
                                    // So settings.x IS the normalized center X.
                                }
                            }
                            
                            if (startX >= 0 && startY >= 0) {
                                val dx = dragAmount.x / widthStr
                                val dy = dragAmount.y / heightStr
                                
                                val newX = (startX + dx).coerceIn(0f, 1f)
                                val newY = (startY + dy).coerceIn(0f, 1f)
                                
                                val newElement = current.copy(x = newX, y = newY)
                                val newElements = settings.elements.toMutableMap()
                                newElements[id] = newElement
                                
                                onSettingsChanged(settings.copy(elements = newElements))
                            }
                        }
                        change.consume()
                    },
                    onDragEnd = {
                        selectedId = null
                        initialElementSettings = null
                    },
                    onDragCancel = {
                        selectedId = null
                        initialElementSettings = null
                    }
                )
            }
            // Separate detector for scaling? 
            // detectTransformGestures handles both. But distinguishing drag vs scale on specific item?
            // User wants "Pinch to resize".
            // If I use detectTransformGestures, I get centroid, pan, zoom, rotation.
            // I should use that INSTEAD of detectDragGestures.
            .pointerInput(settings) {
                detectTransformGestures { centroid, pan, zoom, rotation ->
                     // If no selection, try to select at centroid
                     if (selectedId == null) {
                         selectedId = findIdAt(centroid)
                     }
                     val id = selectedId ?: return@detectTransformGestures
                     
                     val current = settings.elements[id] ?: TouchControllerSettingsManager.ElementSettings()
                     
                     // Handle Zoom
                     if (zoom != 1f) {
                         val newScale = (current.scale * zoom).coerceIn(0.5f, 2.0f) // Limits
                         val newElement = current.copy(scale = newScale)
                         val newElements = settings.elements.toMutableMap()
                         newElements[id] = newElement
                         onSettingsChanged(settings.copy(elements = newElements))
                     }
                     
                     // Handle Pan (Move)
                     if (pan != Offset.Zero) {
                         val widthStr = size.width.toFloat()
                         val heightStr = size.height.toFloat()
                         if (widthStr > 0 && heightStr > 0) {
                            var startX = current.x
                            var startY = current.y
                            
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
                                val newX = (startX + dx).coerceIn(0f, 1f)
                                val newY = (startY + dy).coerceIn(0f, 1f)
                                
                                val newElement = settings.elements[id]?.copy(x = newX, y = newY) ?: current.copy(x = newX, y = newY)
                                val newElements = settings.elements.toMutableMap()
                                newElements[id] = newElement
                                onSettingsChanged(settings.copy(elements = newElements))
                            }
                         }
                     }
                }
            }
    ) {
        // Visuals for editor? Maybe draw boxes around detected elements?
        // Optional but nice.
    }
}
