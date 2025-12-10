/*
 * GameActivity.kt
 *
 * Copyright (C) 2017 Retrograde Project
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.swordfish.lemuroid.app.mobile.feature.game

import android.content.Intent
import android.content.SharedPreferences
import android.content.res.Configuration
import android.graphics.Rect
import android.os.Build
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.view.WindowInsets
import androidx.constraintlayout.widget.ConstraintSet
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import com.swordfish.lemuroid.R
import com.swordfish.lemuroid.app.mobile.feature.gamemenu.GameMenuActivity
import com.swordfish.lemuroid.app.mobile.feature.tilt.CrossTiltTracker
import com.swordfish.lemuroid.app.mobile.feature.tilt.StickTiltTracker
import com.swordfish.lemuroid.app.mobile.feature.tilt.TiltTracker
import com.swordfish.lemuroid.app.mobile.feature.tilt.TwoButtonsTiltTracker
import com.swordfish.lemuroid.app.shared.GameMenuContract
import com.swordfish.lemuroid.app.shared.game.BaseGameActivity
import com.swordfish.lemuroid.common.coroutines.batchWithTime
import com.swordfish.lemuroid.common.coroutines.launchOnState
import com.swordfish.lemuroid.common.coroutines.safeCollect
import com.swordfish.lemuroid.common.graphics.GraphicsUtils
import com.swordfish.lemuroid.common.kotlin.NTuple2
import com.swordfish.lemuroid.common.kotlin.NTuple3
import com.swordfish.lemuroid.common.kotlin.allTrue
import com.swordfish.lemuroid.common.math.linearInterpolation
import com.swordfish.lemuroid.lib.controller.ControllerConfig
import com.swordfish.lemuroid.lib.controller.TouchControllerCustomizer
import com.swordfish.lemuroid.lib.controller.TouchControllerSettingsManager
import com.swordfish.libretrodroid.GLRetroView
import com.swordfish.radialgamepad.library.RadialGamePad
import com.swordfish.radialgamepad.library.config.RadialGamePadTheme
import com.swordfish.radialgamepad.library.event.Event
import com.swordfish.radialgamepad.library.event.GestureType
import com.swordfish.radialgamepad.library.haptics.HapticConfig
import com.swordfish.touchinput.radial.LemuroidTouchConfigs
import com.swordfish.touchinput.radial.LemuroidTouchOverlayThemes
import com.swordfish.touchinput.radial.sensors.TiltSensor
import com.swordfish.lemuroid.app.mobile.feature.game.TouchConfigSplitter
import dagger.Lazy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.last
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.scan
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.takeWhile
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject
import kotlin.math.roundToInt

class GameActivity : BaseGameActivity() {
    @Inject
    lateinit var sharedPreferences: Lazy<SharedPreferences>

    private lateinit var horizontalDivider: View
    private lateinit var leftVerticalDivider: View
    private lateinit var rightVerticalDivider: View

    private var serviceController: GameService.GameServiceController? = null

    private lateinit var tiltSensor: TiltSensor
    private var currentTiltTracker: TiltTracker? = null

    private val touchPads = mutableMapOf<String, RadialGamePad>()

    private val touchControllerJobs = mutableSetOf<Job>()

    private val touchControllerSettingsState = MutableStateFlow<TouchControllerSettingsManager.Settings?>(null)
    private val insetsState = MutableStateFlow<Rect?>(null)
    private val orientationState = MutableStateFlow(Configuration.ORIENTATION_PORTRAIT)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        orientationState.value = getCurrentOrientation()

        tiltSensor = TiltSensor(applicationContext)

        horizontalDivider = findViewById(R.id.horizontaldividier)
        leftVerticalDivider = findViewById(R.id.leftverticaldivider)
        rightVerticalDivider = findViewById(R.id.rightverticaldivider)

        initializeInsetsState()

        startGameService()

        initializeFlows()
    }

    private fun initializeFlows() {
        launchOnState(Lifecycle.State.CREATED) {
            initializeTouchControllerFlow()
        }

        launchOnState(Lifecycle.State.CREATED) {
            initializeTiltSensitivityFlow()
        }

        launchOnState(Lifecycle.State.CREATED) {
            initializeTouchControllerVisibilityFlow()
        }

        launchOnState(Lifecycle.State.RESUMED) {
            initializeTiltEventsFlow()
        }
    }

    private suspend fun initializeTouchControllerVisibilityFlow() {
        isTouchControllerVisible()
            .safeCollect {
                leftGamePadContainer.isVisible = it
                rightGamePadContainer.isVisible = it
            }
    }

    private suspend fun initializeTiltEventsFlow() {
        tiltSensor
            .getTiltEvents()
            .safeCollect { sendTiltEvent(it) }
    }

    private suspend fun initializeTiltSensitivityFlow() {
        val sensitivity = settingsManager.tiltSensitivity()
        tiltSensor.setSensitivity(sensitivity)
    }

    private fun initializeInsetsState() {
        mainContainerLayout.setOnApplyWindowInsetsListener { _, windowInsets ->
            val result =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    val insets =
                        windowInsets.getInsetsIgnoringVisibility(
                            WindowInsets.Type.displayCutout(),
                        )
                    Rect(insets.left, insets.top, insets.right, insets.bottom)
                } else {
                    Rect(0, 0, 0, 0)
                }
            insetsState.value = result
            windowInsets
        }
    }

    private suspend fun initializeTouchControllerFlow() {
        val touchControllerFeatures =
            combine(getTouchControllerType(), orientationState, ::NTuple2)
                .onEach { (pad, orientation) -> setupController(pad, orientation) }

        val layoutFeatures =
            combine(
                isTouchControllerVisible(),
                touchControllerSettingsState.filterNotNull(),
                insetsState.filterNotNull(),
                ::NTuple3,
            )

        touchControllerFeatures.combine(layoutFeatures) { e1, e2 -> e1 + e2 }
            .safeCollect { (config, orientation, touchControllerVisible, padSettings, insets) ->
                LayoutHandler().updateLayout(config, padSettings, orientation, touchControllerVisible, insets)
            }
    }

    private fun getTouchControllerType() =
        getControllerType()
            .map { it[0] }
            .filterNotNull()
            .distinctUntilChanged()

    private suspend fun setupController(
        controllerConfig: ControllerConfig,
        orientation: Int,
    ) {
        val hapticFeedbackMode = settingsManager.hapticFeedbackMode()
        withContext(Dispatchers.Main) {
            setupTouchViews(controllerConfig, hapticFeedbackMode, orientation)
        }
        loadTouchControllerSettings(controllerConfig, orientation, touchPads.keys)
    }

    private fun isTouchControllerVisible(): Flow<Boolean> {
        return inputDeviceManager
            .getEnabledInputsObservable()
            .map { it.isEmpty() }
    }

    private fun getCurrentOrientation() = resources.configuration.orientation

    override fun getDialogClass() = GameMenuActivity::class.java

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        orientationState.value = newConfig.orientation
    }

    private fun setupTouchViews(
        controllerConfig: ControllerConfig,
        hapticFeedbackType: String,
        orientation: Int,
    ) {
        touchControllerJobs.forEach { it.cancel() }
        touchControllerJobs.clear()

        // Clear existing views from main container that we added (if any)
        // Note: Ideally we should use a dedicated container. For now, we clear the pads we track.
        touchPads.values.forEach { mainContainerLayout.removeView(it) }
        touchPads.clear()
        
        // Hide legacy containers
        leftGamePadContainer.isVisible = false
        rightGamePadContainer.isVisible = false

        val touchControllerConfig = controllerConfig.getTouchControllerConfig()

        val hapticConfig =
            when (hapticFeedbackType) {
                "none" -> HapticConfig.OFF
                "press" -> HapticConfig.PRESS
                "press_release" -> HapticConfig.PRESS_AND_RELEASE
                else -> HapticConfig.OFF
            }

        val theme = LemuroidTouchOverlayThemes.getGamePadTheme(mainContainerLayout)

        val leftBaseConfig = LemuroidTouchConfigs.getRadialGamePadConfig(touchControllerConfig.leftConfig, hapticConfig, theme)
        val rightBaseConfig = LemuroidTouchConfigs.getRadialGamePadConfig(touchControllerConfig.rightConfig, hapticConfig, theme)

        val groups = TouchConfigSplitter.split(leftBaseConfig, "left") + 
                     TouchConfigSplitter.split(rightBaseConfig, "right")

        val eventFlows = mutableListOf<Flow<Event>>()

        groups.forEach { group ->
            val pad = RadialGamePad(group.config, 0f, this) // 0f margin, we handle layout manually
            pad.id = View.generateViewId()
            // Store default offsets in tag or map for layout logic?
            // We can store them in a map in LayoutHandler or effectively just use the ID.
            pad.tag = group // Store the whole split group info
            
            mainContainerLayout.addView(pad)
            touchPads[group.id] = pad
            eventFlows.add(pad.events())
        }

        val touchControllerEvents = eventFlows.merge().shareIn(lifecycleScope, SharingStarted.Lazily)

        setupDefaultActions(touchControllerEvents)
        setupTiltActions(touchControllerEvents)
        setupTouchMenuActions(touchControllerEvents)
    }

    private fun setupDefaultActions(touchControllerEvents: Flow<Event>) {
        val job =
            lifecycleScope.launch {
                touchControllerEvents
                    .safeCollect {
                        when (it) {
                            is Event.Button -> {
                                handleGamePadButton(it)
                            }
                            is Event.Direction -> {
                                handleGamePadDirection(it)
                            }
                        }
                    }
            }
        touchControllerJobs.add(job)
    }

    private fun setupTiltActions(touchControllerEvents: Flow<Event>) {
        val job1 =
            lifecycleScope.launch {
                touchControllerEvents
                    .filterIsInstance<Event.Gesture>()
                    .filter { it.type == GestureType.TRIPLE_TAP }
                    .batchWithTime(500)
                    .filter { it.isNotEmpty() }
                    .safeCollect { events ->
                        handleTripleTaps(events)
                    }
            }

        val job2 =
            lifecycleScope.launch {
                touchControllerEvents
                    .filterIsInstance<Event.Gesture>()
                    .filter { it.type == GestureType.FIRST_TOUCH }
                    .safeCollect { event ->
                        currentTiltTracker?.let { tracker ->
                            if (event.id in tracker.trackedIds()) {
                                stopTrackingId(tracker)
                            }
                        }
                    }
            }

        touchControllerJobs.add(job1)
        touchControllerJobs.add(job2)
    }

    private fun setupTouchMenuActions(touchControllerEvents: Flow<Event>) {
        VirtualLongPressHandler.initializeTheme(this)

        val allMenuButtonEvents =
            touchControllerEvents
                .filterIsInstance<Event.Button>()
                .filter { it.id == KeyEvent.KEYCODE_BUTTON_MODE }
                .shareIn(lifecycleScope, SharingStarted.Lazily)

        val cancelMenuButtonEvents =
            allMenuButtonEvents
                .filter { it.action == KeyEvent.ACTION_UP }
                .map { Unit }

        val job =
            lifecycleScope.launch {
                allMenuButtonEvents
                    .filter { it.action == KeyEvent.ACTION_DOWN }
                    .map {
                        VirtualLongPressHandler.displayLoading(
                            this@GameActivity,
                            R.drawable.ic_menu,
                            cancelMenuButtonEvents,
                        )
                    }
                    .filter { it }
                    .safeCollect {
                        displayOptionsDialog()
                        simulateTouchControllerHaptic()
                    }
            }

        touchControllerJobs.add(job)
    }

    private fun handleTripleTaps(events: List<Event.Gesture>) {
        val eventsTracker =
            when (events.map { it.id }.toSet()) {
                setOf(LemuroidTouchConfigs.MOTION_SOURCE_LEFT_STICK) ->
                    StickTiltTracker(
                        LemuroidTouchConfigs.MOTION_SOURCE_LEFT_STICK,
                    )
                setOf(LemuroidTouchConfigs.MOTION_SOURCE_RIGHT_STICK) ->
                    StickTiltTracker(
                        LemuroidTouchConfigs.MOTION_SOURCE_RIGHT_STICK,
                    )
                setOf(LemuroidTouchConfigs.MOTION_SOURCE_DPAD) ->
                    CrossTiltTracker(
                        LemuroidTouchConfigs.MOTION_SOURCE_DPAD,
                    )
                setOf(LemuroidTouchConfigs.MOTION_SOURCE_DPAD_AND_LEFT_STICK) ->
                    CrossTiltTracker(
                        LemuroidTouchConfigs.MOTION_SOURCE_DPAD_AND_LEFT_STICK,
                    )
                setOf(LemuroidTouchConfigs.MOTION_SOURCE_RIGHT_DPAD) ->
                    CrossTiltTracker(
                        LemuroidTouchConfigs.MOTION_SOURCE_RIGHT_DPAD,
                    )
                setOf(
                    KeyEvent.KEYCODE_BUTTON_L1,
                    KeyEvent.KEYCODE_BUTTON_R1,
                ),
                ->
                    TwoButtonsTiltTracker(
                        KeyEvent.KEYCODE_BUTTON_L1,
                        KeyEvent.KEYCODE_BUTTON_R1,
                    )
                setOf(
                    KeyEvent.KEYCODE_BUTTON_L2,
                    KeyEvent.KEYCODE_BUTTON_R2,
                ),
                ->
                    TwoButtonsTiltTracker(
                        KeyEvent.KEYCODE_BUTTON_L2,
                        KeyEvent.KEYCODE_BUTTON_R2,
                    )
                else -> null
            }

        eventsTracker?.let { startTrackingId(eventsTracker) }
    }

    override fun onDestroy() {
        stopGameService()
        touchControllerJobs.clear()
        super.onDestroy()
    }

    private fun startGameService() {
        serviceController = GameService.startService(applicationContext, game)
    }

    private fun stopGameService() {
        serviceController = GameService.stopService(applicationContext, serviceController)
    }

    override fun onFinishTriggered() {
        super.onFinishTriggered()
        stopGameService()
    }

    private fun handleGamePadButton(it: Event.Button) {
        retroGameView?.sendKeyEvent(it.action, it.id)
    }

    private fun handleGamePadDirection(it: Event.Direction) {
        when (it.id) {
            LemuroidTouchConfigs.MOTION_SOURCE_DPAD -> {
                retroGameView?.sendMotionEvent(GLRetroView.MOTION_SOURCE_DPAD, it.xAxis, it.yAxis)
            }
            LemuroidTouchConfigs.MOTION_SOURCE_LEFT_STICK -> {
                retroGameView?.sendMotionEvent(
                    GLRetroView.MOTION_SOURCE_ANALOG_LEFT,
                    it.xAxis,
                    it.yAxis,
                )
            }
            LemuroidTouchConfigs.MOTION_SOURCE_RIGHT_STICK -> {
                retroGameView?.sendMotionEvent(
                    GLRetroView.MOTION_SOURCE_ANALOG_RIGHT,
                    it.xAxis,
                    it.yAxis,
                )
            }
            LemuroidTouchConfigs.MOTION_SOURCE_DPAD_AND_LEFT_STICK -> {
                retroGameView?.sendMotionEvent(
                    GLRetroView.MOTION_SOURCE_ANALOG_LEFT,
                    it.xAxis,
                    it.yAxis,
                )
                retroGameView?.sendMotionEvent(GLRetroView.MOTION_SOURCE_DPAD, it.xAxis, it.yAxis)
            }
            LemuroidTouchConfigs.MOTION_SOURCE_RIGHT_DPAD -> {
                retroGameView?.sendMotionEvent(
                    GLRetroView.MOTION_SOURCE_ANALOG_RIGHT,
                    it.xAxis,
                    it.yAxis,
                )
            }
        }
    }

    override fun onActivityResult(
        requestCode: Int,
        resultCode: Int,
        data: Intent?,
    ) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == DIALOG_REQUEST) {
            if (data?.getBooleanExtra(GameMenuContract.RESULT_EDIT_TOUCH_CONTROLS, false) == true) {
                lifecycleScope.launch {
                    displayCustomizationOptions()
                }
            }
        }
    }

    override fun onPause() {
        super.onPause()
        tiltSensor.isAllowedToRun = false
    }

    override fun onResume() {
        super.onResume()
        tiltSensor.isAllowedToRun = true
    }

    private fun sendTiltEvent(sensorValues: FloatArray) {
        currentTiltTracker?.let {
            val xTilt = (sensorValues[0] + 1f) / 2f
            val yTilt = (sensorValues[1] + 1f) / 2f
            it.updateTracking(xTilt, yTilt, touchPads.values.asSequence())
        }
    }

    private fun stopTrackingId(trackedEvent: TiltTracker) {
        currentTiltTracker = null
        tiltSensor.shouldRun = false
        trackedEvent.stopTracking(touchPads.values.asSequence())
    }

    private fun startTrackingId(trackedEvent: TiltTracker) {
        if (currentTiltTracker != trackedEvent) {
            currentTiltTracker?.let { stopTrackingId(it) }
            currentTiltTracker = trackedEvent
            tiltSensor.shouldRun = true
            simulateTouchControllerHaptic()
        }
    }

    private fun simulateTouchControllerHaptic() {
        touchPads.values.firstOrNull()?.performHapticFeedback()
    }

    private suspend fun storeTouchControllerSettings(
        controllerConfig: ControllerConfig,
        orientation: Int,
        settings: TouchControllerSettingsManager.Settings,
    ) {
        val settingsManager = getTouchControllerSettingsManager(controllerConfig, orientation)
        return settingsManager.storeSettings(settings)
    }

    private suspend fun loadTouchControllerSettings(
        controllerConfig: ControllerConfig,
        orientation: Int,
        elementIds: Set<String>
    ) {
        val settingsManager = getTouchControllerSettingsManager(controllerConfig, orientation)
        touchControllerSettingsState.value = settingsManager.retrieveSettings(elementIds)
    }

    private fun getTouchControllerSettingsManager(
        controllerConfig: ControllerConfig,
        orientation: Int,
    ): TouchControllerSettingsManager {
        val settingsOrientation =
            if (orientation == Configuration.ORIENTATION_PORTRAIT) {
                TouchControllerSettingsManager.Orientation.PORTRAIT
            } else {
                TouchControllerSettingsManager.Orientation.LANDSCAPE
            }

        return TouchControllerSettingsManager(
            applicationContext,
            controllerConfig.touchControllerID,
            sharedPreferences,
            settingsOrientation,
        )
    }

    private suspend fun displayCustomizationOptions() {
        findViewById<View>(R.id.editcontrolsdarkening).isVisible = true

        val customizer = TouchControllerCustomizer()

        val insets =
            insetsState
                .filterNotNull()
                .first()

        val touchControllerConfig =
            getTouchControllerType()
                .first()

        val padSettings =
            touchControllerSettingsState.filterNotNull()
                .first()

        val initialSettings =
            TouchControllerCustomizer.Settings(
                padSettings.scale,
                padSettings.rotation,
                padSettings.marginX,
                padSettings.marginY,
            )

        val finalSettings =
            customizer.displayCustomizationPopup(
                this@GameActivity,
                layoutInflater,
                mainContainerLayout,
                insets,
                initialSettings,
                touchPads
            )
                .takeWhile { it !is TouchControllerCustomizer.Event.Close }
                .scan(padSettings) { current, it ->
                    when (it) {
                        is TouchControllerCustomizer.Event.ElementChange -> {
                            val newElements = current.elements.toMutableMap()
                            newElements[it.id] = it.settings
                            current.copy(elements = newElements)
                        }
                        else -> current
                    }
                }
                .onEach { touchControllerSettingsState.value = it }
                .last()

        storeTouchControllerSettings(touchControllerConfig, orientationState.value, finalSettings)
        findViewById<View>(R.id.editcontrolsdarkening).isVisible = false
    }

    inner class LayoutHandler {
        private fun handleRetroViewLayout(
            constraintSet: ConstraintSet,
            controllerConfig: ControllerConfig,
            orientation: Int,
            touchControllerVisible: Boolean,
            insets: Rect,
        ) {
            if (!touchControllerVisible) {
                constraintSet.connect(
                    R.id.gamecontainer,
                    ConstraintSet.TOP,
                    ConstraintSet.PARENT_ID,
                    ConstraintSet.TOP,
                )
                constraintSet.connect(
                    R.id.gamecontainer,
                    ConstraintSet.LEFT,
                    ConstraintSet.PARENT_ID,
                    ConstraintSet.LEFT,
                )
                constraintSet.connect(
                    R.id.gamecontainer,
                    ConstraintSet.BOTTOM,
                    ConstraintSet.PARENT_ID,
                    ConstraintSet.BOTTOM,
                )
                constraintSet.connect(
                    R.id.gamecontainer,
                    ConstraintSet.RIGHT,
                    ConstraintSet.PARENT_ID,
                    ConstraintSet.RIGHT,
                )
                return
            }

            if (orientation == Configuration.ORIENTATION_PORTRAIT) {
                constraintSet.connect(
                    R.id.gamecontainer,
                    ConstraintSet.BOTTOM,
                    R.id.horizontaldividier,
                    ConstraintSet.TOP,
                )

                constraintSet.connect(
                    R.id.gamecontainer,
                    ConstraintSet.LEFT,
                    ConstraintSet.PARENT_ID,
                    ConstraintSet.LEFT,
                )

                constraintSet.connect(
                    R.id.gamecontainer,
                    ConstraintSet.RIGHT,
                    ConstraintSet.PARENT_ID,
                    ConstraintSet.RIGHT,
                )

                constraintSet.connect(
                    R.id.gamecontainer,
                    ConstraintSet.TOP,
                    ConstraintSet.PARENT_ID,
                    ConstraintSet.TOP,
                )
            } else {
                constraintSet.connect(
                    R.id.gamecontainer,
                    ConstraintSet.BOTTOM,
                    ConstraintSet.PARENT_ID,
                    ConstraintSet.BOTTOM,
                )

                constraintSet.connect(
                    R.id.gamecontainer,
                    ConstraintSet.TOP,
                    ConstraintSet.PARENT_ID,
                    ConstraintSet.TOP,
                )

                if (controllerConfig.allowTouchOverlay) {
                    constraintSet.connect(
                        R.id.gamecontainer,
                        ConstraintSet.LEFT,
                        ConstraintSet.PARENT_ID,
                        ConstraintSet.LEFT,
                    )

                    constraintSet.connect(
                        R.id.gamecontainer,
                        ConstraintSet.RIGHT,
                        ConstraintSet.PARENT_ID,
                        ConstraintSet.RIGHT,
                    )
                } else {
                    constraintSet.connect(
                        R.id.gamecontainer,
                        ConstraintSet.LEFT,
                        R.id.leftverticaldivider,
                        ConstraintSet.RIGHT,
                    )

                    constraintSet.connect(
                        R.id.gamecontainer,
                        ConstraintSet.RIGHT,
                        R.id.rightverticaldivider,
                        ConstraintSet.LEFT,
                    )
                }
            }

            constraintSet.constrainedWidth(R.id.gamecontainer, true)
            constraintSet.constrainedHeight(R.id.gamecontainer, true)

            constraintSet.setMargin(R.id.gamecontainer, ConstraintSet.TOP, insets.top)
        }

        private fun handleTouchControllerLayout(
            constraintSet: ConstraintSet,
            padSettings: TouchControllerSettingsManager.Settings,
            controllerConfig: ControllerConfig,
            orientation: Int,
            insets: Rect,
        ) {
            val touchControllerConfig = controllerConfig.getTouchControllerConfig()
            val minScale = TouchControllerSettingsManager.MIN_SCALE
            val maxScale = TouchControllerSettingsManager.MAX_SCALE

            // Calculate base scales from global setting (optional backing) or just 1.0
            val globalScaleLerp = linearInterpolation(padSettings.scale, minScale, maxScale)
            
            val activePads = touchPads.toMap() // Copy to avoid concurrent mods if any

            // Screen dimensions - we assume mainContainerLayout is laid out.
            // If 0, we might need waiting. LayoutHandler is usually called during layout pass or after measure.
            val width = mainContainerLayout.width.takeIf { it > 0 } ?: resources.displayMetrics.widthPixels
            val height = mainContainerLayout.height.takeIf { it > 0 } ?: resources.displayMetrics.heightPixels
            
            val safeWidth = width - insets.left - insets.right
            val safeHeight = height - insets.top - insets.bottom
            val leftOffset = insets.left
            val topOffset = insets.top

            activePads.forEach { (id, pad) ->
                val group = pad.tag as? TouchConfigSplitter.SplitGroup
                val elementSettings = padSettings.elements[id]
                
                // Scale
                val eScale = elementSettings?.scale ?: 1.0f
                // We combine global scale (legacy support) with element scale? 
                // Or just use element scale. User said "independent".
                // But keeping global scale influence might be nice for "Make Everything Bigger".
                // Let's multiply them.
                val finalScale = globalScaleLerp * eScale
                
                // Set scale on view
                pad.scaleX = finalScale
                pad.scaleY = finalScale
                
                // Position
                val (defX, defY) = calculateDefaultPosition(id, safeWidth, safeHeight, orientation)
                
                // Settings X/Y are Normalized (0..1). If -1, use Default.
                val setX = elementSettings?.x ?: -1f
                val setY = elementSettings?.y ?: -1f
                
                val targetX = if (setX >= 0f) (setX * safeWidth) else defX
                val targetY = if (setY >= 0f) (setY * safeHeight) else defY
                
                // Apply offsets from SplitGroup (e.g. slight shift for button vs stick)
                // These are usually 0 based on my splitter analysis, but good to include.
                val splitOffsetX = group?.defaultOffsetX ?: 0f
                val splitOffsetY = group?.defaultOffsetY ?: 0f

                // To center the view at targetX, subtract half width.
                // But view width might be 0 if not measured yet.
                // RadialGamePad usually measures to primaryDialMaxSizeDp + margins.
                // We should ensure it has a size. 
                // We can use estimated size or wait for layout.
                // For now, let's assume we position the TOP-LEFT of the view, or CENTER?
                // Standard game controls: Position usually refers to Center.
                
                // Since we can't easily know width before measure, we can use translation.
                // pad.x sets left position.
                // We want center.
                // Let's use translationX/Y relative to (0,0) of container.
                // And shift by -width/2 in onLayout? Or use Translation which is post-layout?
                
                // Using View.setX/Y sets Left/Top.
                // If we want to set Center, we need Width.
                // Hack: Set pivot to center (default) and just set position? No.
                
                // Let's set translation.
                // If width is 0, this is wrong.
                // We can use a OnLayoutChangeListener on the pad to correct centering?
                // Or we can just set X/Y and refine later.
                
                // Ideally: targetX is the CENTER.
                pad.x = leftOffset + targetX + splitOffsetX - (pad.width / 2f)
                pad.y = topOffset + targetY + splitOffsetY - (pad.height / 2f)
                
                // Logic to update when width changes:
                if (pad.width == 0) {
                     pad.post { 
                        pad.x = leftOffset + targetX + splitOffsetX - (pad.width / 2f)
                        pad.y = topOffset + targetY + splitOffsetY - (pad.height / 2f)
                     }
                }
            }
        }

        private fun calculateDefaultPosition(id: String, w: Int, h: Int, orientation: Int): Pair<Float, Float> {
            // Heuristic based on ID and Orientation (Portrait/Landscape)
            // Portrait: Controls usually bottom half.
            // Landscape: Controls on sides.
            
            val isPortrait = orientation == Configuration.ORIENTATION_PORTRAIT
            
            // Normalize ID
            val lowerId = id.lowercase()
            
            // Centers
            val cx = w / 2f
            val cy = h / 2f
            
            if (isPortrait) {
                // Portrait Layout
                return when {
                    "dpad" in lowerId -> Pair(w * 0.25f, h * 0.65f) // Left-Bottomish
                    "face" in lowerId -> Pair(w * 0.75f, h * 0.65f) // Right-Bottomish
                    "start" in lowerId -> Pair(w * 0.60f, h * 0.85f)
                    "select" in lowerId -> Pair(w * 0.40f, h * 0.85f)
                    "menu" in lowerId -> Pair(w * 0.50f, h * 0.90f)
                    "l" in lowerId || "l1" in lowerId -> Pair(w * 0.15f, h * 0.55f)
                    "r" in lowerId || "r1" in lowerId -> Pair(w * 0.85f, h * 0.55f)
                    "stick" in lowerId && "left" in lowerId -> Pair(w * 0.25f, h * 0.80f)
                    "stick" in lowerId && "right" in lowerId -> Pair(w * 0.75f, h * 0.80f)
                    else -> Pair(cx, cy)
                }
            } else {
                // Landscape Layout
                return when {
                    "dpad" in lowerId -> Pair(w * 0.15f, h * 0.60f)
                    "face" in lowerId -> Pair(w * 0.85f, h * 0.60f)
                    "start" in lowerId -> Pair(w * 0.85f, h * 0.85f) // Bottom Right corner
                    "select" in lowerId -> Pair(w * 0.15f, h * 0.85f) // Bottom Left corner
                    "menu" in lowerId -> Pair(w * 0.50f, h * 0.10f) // Top Center
                    "l" in lowerId || "l1" in lowerId -> Pair(w * 0.15f, h * 0.25f) // Top Left Shoulder
                    "r" in lowerId || "r1" in lowerId -> Pair(w * 0.85f, h * 0.25f) // Top Right Shoulder
                    "l2" in lowerId -> Pair(w * 0.15f, h * 0.15f) // Higher
                    "r2" in lowerId -> Pair(w * 0.85f, h * 0.15f)
                    "stick" in lowerId && "left" in lowerId -> Pair(w * 0.20f, h * 0.80f)
                    "stick" in lowerId && "right" in lowerId -> Pair(w * 0.80f, h * 0.80f)
                    else -> Pair(w * 0.5f, h * 0.8f) // Default center-bottom
                }
            }
        }
                    applicationContext,
                )

            if (orientation == Configuration.ORIENTATION_PORTRAIT) {
                setupMarginsForPortrait(
                    leftPad,
                    rightPad,
                    maxMargins,
                    padSettings,
                    baseVerticalMargin.roundToInt() + insets.bottom,
                )
            } else {
                setupMarginsForLandscape(
                    leftPad,
                    rightPad,
                    maxMargins,
                    padSettings,
                    baseVerticalMargin.roundToInt() + insets.bottom,
                    maxOf(insets.left, insets.right),
                )
            }

            leftPad.gravityY = 1f
            rightPad.gravityY = 1f

            leftPad.gravityX = -1f
            rightPad.gravityX = 1f

            leftPad.secondaryDialSpacing = 0.1f
            rightPad.secondaryDialSpacing = 0.1f

            val constrainHeight =
                if (orientation == Configuration.ORIENTATION_PORTRAIT) {
                    ConstraintSet.WRAP_CONTENT
                } else {
                    ConstraintSet.MATCH_CONSTRAINT
                }

            val constrainWidth =
                if (orientation == Configuration.ORIENTATION_PORTRAIT) {
                    ConstraintSet.MATCH_CONSTRAINT
                } else {
                    ConstraintSet.WRAP_CONTENT
                }

            constraintSet.constrainHeight(R.id.leftgamepad, constrainHeight)
            constraintSet.constrainHeight(R.id.rightgamepad, constrainHeight)
            constraintSet.constrainWidth(R.id.leftgamepad, constrainWidth)
            constraintSet.constrainWidth(R.id.rightgamepad, constrainWidth)

            if (controllerConfig.allowTouchRotation) {
                val maxRotation = TouchControllerSettingsManager.MAX_ROTATION
                leftPad.secondaryDialRotation = linearInterpolation(padSettings.rotation, 0f, maxRotation)
                rightPad.secondaryDialRotation = -linearInterpolation(padSettings.rotation, 0f, maxRotation)
            }
        }

        private fun setupMarginsForLandscape(
            leftPad: RadialGamePad,
            rightPad: RadialGamePad,
            maxMargins: Float,
            padSettings: TouchControllerSettingsManager.Settings,
            verticalSpacing: Int,
            horizontalSpacing: Int,
        ) {
            leftPad.spacingBottom = verticalSpacing
            leftPad.spacingLeft = linearInterpolation(
                padSettings.marginX,
                0f,
                maxMargins,
            ).roundToInt() + horizontalSpacing

            rightPad.spacingBottom = verticalSpacing
            rightPad.spacingRight = linearInterpolation(
                padSettings.marginX,
                0f,
                maxMargins,
            ).roundToInt() + horizontalSpacing

            leftPad.offsetX = 0f
            rightPad.offsetX = 0f

            leftPad.offsetY = -linearInterpolation(padSettings.marginY, 0f, maxMargins)
            rightPad.offsetY = -linearInterpolation(padSettings.marginY, 0f, maxMargins)
        }

        private fun setupMarginsForPortrait(
            leftPad: RadialGamePad,
            rightPad: RadialGamePad,
            maxMargins: Float,
            padSettings: TouchControllerSettingsManager.Settings,
            verticalSpacing: Int,
        ) {
            leftPad.spacingBottom = linearInterpolation(
                padSettings.marginY,
                0f,
                maxMargins,
            ).roundToInt() + verticalSpacing
            leftPad.spacingLeft = 0
            rightPad.spacingBottom = linearInterpolation(
                padSettings.marginY,
                0f,
                maxMargins,
            ).roundToInt() + verticalSpacing
            rightPad.spacingRight = 0

            leftPad.offsetX = linearInterpolation(padSettings.marginX, 0f, maxMargins)
            rightPad.offsetX = -linearInterpolation(padSettings.marginX, 0f, maxMargins)

            leftPad.offsetY = 0f
            rightPad.offsetY = 0f
        }

        private fun updateDividers(
            orientation: Int,
            controllerConfig: ControllerConfig,
            touchControllerVisible: Boolean,
        ) {
            val theme = LemuroidTouchOverlayThemes.getGamePadTheme(leftGamePadContainer)

            val displayHorizontalDivider =
                allTrue(
                    orientation == Configuration.ORIENTATION_PORTRAIT,
                    touchControllerVisible,
                )

            val displayVerticalDivider =
                allTrue(
                    orientation != Configuration.ORIENTATION_PORTRAIT,
                    !controllerConfig.allowTouchOverlay,
                    touchControllerVisible,
                )

            updateDivider(horizontalDivider, displayHorizontalDivider, theme)
            updateDivider(leftVerticalDivider, displayVerticalDivider, theme)
            updateDivider(rightVerticalDivider, displayVerticalDivider, theme)
        }

        private fun updateDivider(
            divider: View,
            visible: Boolean,
            theme: RadialGamePadTheme,
        ) {
            divider.isVisible = visible
            divider.setBackgroundColor(theme.backgroundStrokeColor)
        }

        fun updateLayout(
            config: ControllerConfig,
            padSettings: TouchControllerSettingsManager.Settings,
            orientation: Int,
            touchControllerVisible: Boolean,
            insets: Rect,
        ) {
            updateDividers(orientation, config, touchControllerVisible)

            val constraintSet = ConstraintSet()
            constraintSet.clone(mainContainerLayout)

            handleTouchControllerLayout(constraintSet, padSettings, config, orientation, insets)
            handleRetroViewLayout(constraintSet, config, orientation, touchControllerVisible, insets)

            constraintSet.applyTo(mainContainerLayout)

            mainContainerLayout.requestLayout()
            mainContainerLayout.invalidate()
        }
    }

    companion object {
        const val DEFAULT_MARGINS_DP = 8f
        const val DEFAULT_PRIMARY_DIAL_SIZE = 160f
    }
}
