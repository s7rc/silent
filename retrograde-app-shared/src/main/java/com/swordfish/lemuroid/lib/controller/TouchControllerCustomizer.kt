package com.swordfish.lemuroid.lib.controller

import android.app.Activity
import android.content.pm.ActivityInfo
import android.graphics.Rect
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.PopupWindow
import androidx.core.math.MathUtils
import com.swordfish.lemuroid.common.graphics.GraphicsUtils
import com.swordfish.touchinput.controller.R
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onSubscription

class TouchControllerCustomizer {
    private lateinit var touchDetector: MultiTouchGestureDetector
    private var editControlsWindow: PopupWindow? = null

    sealed class Event {
        data class ElementChange(val id: String, val settings: TouchControllerSettingsManager.ElementSettings) : Event()

        object Save : Event()

        object Close : Event()

        object Init : Event()
    }

    // We reuse TouchControllerSettingsManager.Settings now


    private fun getEvents(
        activity: Activity,
        layoutInflater: LayoutInflater,
        containerView: View,
        settings: TouchControllerSettingsManager.Settings,
        insets: Rect,
        touchPads: Map<String, View>
    ): SharedFlow<Event> {
        val events = MutableStateFlow<Event>(Event.Init)

        // Local copy of elements settings
        val currentElements = settings.elements.toMutableMap()
        var globalScale = settings.scale 

        val contentView = layoutInflater.inflate(R.layout.layout_edit_touch_controls, null)
        editControlsWindow =
            PopupWindow(
                contentView,
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
                true,
            )
        
        editControlsWindow?.contentView?.findViewById<Button>(R.id.edit_control_reset)
            ?.setOnClickListener {
                // Reset to defaults
                // We emit global scale reset and maybe clear element overrides?
                events.value = Event.ElementChange("RESET_ALL", TouchControllerSettingsManager.ElementSettings()) // handled by consumer
                // Or implementing reset logic here is complex. 
                // Let's assume consumer handles RESET_ALL magic string or we loop.
                touchPads.keys.forEach { id ->
                     events.value = Event.ElementChange(id, TouchControllerSettingsManager.ElementSettings(x = -1f, y = -1f, scale = 1f))
                }
            }
        editControlsWindow?.contentView?.findViewById<Button>(R.id.edit_control_done)
            ?.setOnClickListener {
                events.value = Event.Save
                hideCustomizationOptions()
                events.value = Event.Close
            }

        touchDetector =
            MultiTouchGestureDetector(
                activity,
                object : MultiTouchGestureDetector.SimpleOnMultiTouchGestureListener() {
                    
                    var selectedId: String? = null
                    var initialElementState: TouchControllerSettingsManager.ElementSettings? = null

                    override fun onBegin(detector: MultiTouchGestureDetector): Boolean {
                        val x = detector.focusX
                        val y = detector.focusY
                        
                        // Find touched view
                        // Search in reverse z-order or just iterate? Map iteration order is undefined.
                        selectedId = null
                        
                        // Check if hitting a pad
                        // We need global coords of pads.
                        val location = IntArray(2)
                        
                        // Iterate and take the closest or first hit? 
                        // First hit is fine.
                        for ((id, view) in touchPads) {
                            view.getLocationOnScreen(location)
                            val rect = Rect(location[0], location[1], location[0] + view.width, location[1] + view.height)
                            // We need to account for the fact that popup might be offset relative to screen?
                            // But motion event is relative to popup content view.
                            // If popup is full screen, coords match.
                            
                            // Hit test
                            if (rect.contains(x.toInt(), y.toInt())) {
                                selectedId = id
                                break
                            }
                        }
                        
                        if (selectedId != null) {
                            // Load initial state for this gesture
                             initialElementState = currentElements[selectedId] ?: 
                                 TouchControllerSettingsManager.ElementSettings(x = -1f, y = -1f, scale = 1f)
                        } else {
                            // Maybe hitting background? Allow global scale?
                            // For now, only element manipulation.
                        }
                        
                        return super.onBegin(detector)
                    }

                    override fun onScale(detector: MultiTouchGestureDetector) {
                        val id = selectedId ?: return
                        val current = currentElements[id] ?: TouchControllerSettingsManager.ElementSettings(x = -1f, y = -1f, scale = 1f)
                        
                        val newScale = MathUtils.clamp(
                            current.scale * detector.scale, 
                            TouchControllerSettingsManager.MIN_SCALE, 
                            TouchControllerSettingsManager.MAX_SCALE
                        )
                        
                        val newSettings = current.copy(scale = newScale)
                        currentElements[id] = newSettings
                        events.value = Event.ElementChange(id, newSettings)
                    }

                    override fun onMove(detector: MultiTouchGestureDetector) {
                        val id = selectedId ?: return
                        val current = currentElements[id] ?: TouchControllerSettingsManager.ElementSettings(x = -1f, y = -1f, scale = 1f)
                        
                        // Convert move (pixels) to normalized (0..1)
                        // If current X is -1 (default), we need to resolve it first?
                        // If it's -1, we can't increment it easily without knowing where it WAS.
                        // BUT: logic in GameActivity resolves -1.
                        // Here we don't know the resolved position unless we read from View?
                        // YES. We should read View.x/y, convert to normalized, then apply delta.
                        
                        val view = touchPads[id] ?: return
                        val parentW = (view.parent as? View)?.width ?: containerView.width
                        val parentH = (view.parent as? View)?.height ?: containerView.height
                        
                        if (parentW == 0 || parentH == 0) return
                        
                        // Get current visual X/Y (view.x is Left)
                        // Normalize it?
                        // View.x = (normX * parentW) -> normX = View.x / parentW
                        // We want to update normX.
                        
                        // Better: Just maintain 'current normalized' in memory?
                        // If it started as -1, we snap to current view position on first move?
                        var startX = current.x
                        var startY = current.y
                        
                        // If uninitialized settings, derive from view
                        if (startX < 0) startX = view.x / parentW.toFloat()
                        if (startY < 0) startY = view.y / parentH.toFloat()
                        
                        val deltaX = detector.moveX / parentW.toFloat()
                        val deltaY = detector.moveY / parentH.toFloat()
                        
                        val nextX = MathUtils.clamp(startX + deltaX, 0f, 1f)
                        val nextY = MathUtils.clamp(startY + deltaY, 0f, 1f)
                        
                        val newSettings = current.copy(x = nextX, y = nextY)
                        currentElements[id] = newSettings
                        events.value = Event.ElementChange(id, newSettings)
                    }

                    override fun onRotate(detector: MultiTouchGestureDetector) {
                         // Rotation not per-element yet? Or is it? 
                         // Implementation plan said position and resize.
                         // But if user rotates fingers, maybe?
                         // ElementSettings doesn't have rotation field?
                         // Wait, TouchControllerSettingsManager.Settings has 'rotation' (Global).
                         // ElementSettings has NO rotation.
                         // So we ignore rotation for now or make it global?
                         // User said "resize and move".
                         // I'll skip rotation for individual elements to verify complexity.
                    }
                },
            )

        editControlsWindow?.setOnDismissListener { events.value = Event.Close }

        editControlsWindow?.contentView?.setOnTouchListener { _, event ->
            touchDetector.onTouchEvent(event)
        }
        editControlsWindow?.isFocusable = false
        editControlsWindow?.showAtLocation(containerView, Gravity.CENTER, 0, 0)
        return events
    }

    fun displayCustomizationPopup(
        activity: Activity,
        layoutInflater: LayoutInflater,
        view: View,
        insets: Rect,
        settings: TouchControllerSettingsManager.Settings,
        touchPads: Map<String, View>
    ): Flow<Event> {
        val originalRequestedOrientation = activity.requestedOrientation
        return getEvents(activity, layoutInflater, view, settings, insets, touchPads)
            .onSubscription { activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LOCKED }
            .onCompletion { activity.requestedOrientation = originalRequestedOrientation }
            .onCompletion { hideCustomizationOptions() }
    }

    private fun hideCustomizationOptions() {
        editControlsWindow?.dismiss()
        editControlsWindow?.contentView?.setOnTouchListener(null)
        editControlsWindow = null
    }
}
