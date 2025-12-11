package com.swordfish.touchinput.radial.layouts

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.Placeable
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.layoutId
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.draw.scale
import com.swordfish.touchinput.radial.settings.TouchControllerSettingsManager
import kotlin.math.cos
import kotlin.math.sin

val LocalTouchElementBounds = compositionLocalOf { mutableMapOf<String, Rect>() }

private data class RadialParentData(val degrees: Float)

interface LemuroidRadialScope {
    fun Modifier.radialPosition(degrees: Float): Modifier
    fun Modifier.radialScale(scale: Float): Modifier
}

private class IndependentSecondaryDialsScope(
    private val prefix: String,
    private val boundsMap: MutableMap<String, Rect>,
    private val settings: TouchControllerSettingsManager.Settings
) : LemuroidRadialScope {
    private var index = 0

    override fun Modifier.radialPosition(degrees: Float): Modifier {
        val currentId = "${prefix}_secondary_${index++}"
        val elementScale = settings.elements[currentId]?.scale ?: 0.5f
        
        return this
            .onGloballyPositioned {
                boundsMap[currentId] = it.boundsInRoot()
            }
            .then(
                object : androidx.compose.ui.layout.ParentDataModifier {
                    override fun androidx.compose.ui.unit.Density.modifyParentData(parentData: Any?): Any {
                        return RadialParentData(degrees)
                    }
                }
            )
            .scale(elementScale)
    }

    override fun Modifier.radialScale(scale: Float): Modifier {
         return this.scale(scale)
    }
}

@Composable
fun IndependentRadialLayout(
    modifier: Modifier = Modifier,
    settings: TouchControllerSettingsManager.Settings,
    primaryDialMaxSize: Dp,
    secondaryDialsBaseRotationInDegrees: Float,
    isLeft: Boolean,
    primaryDial: @Composable () -> Unit,
    secondaryDials: @Composable LemuroidRadialScope.() -> Unit,
) {
    val boundsMap = LocalTouchElementBounds.current
    val prefix = if (isLeft) "left" else "right"

    Layout(
        modifier = modifier,
        content = {
            // ID: primary
            Box(
                Modifier
                    .layoutId("primary")
                    .onGloballyPositioned {
                        val id = "${prefix}_primary"
                        boundsMap[id] = it.boundsInRoot()
                    }
                    .scale(settings.elements["${prefix}_primary"]?.scale ?: 0.5f)
            ) {
                primaryDial()
            }

            // Secondary Dials
            val scope = IndependentSecondaryDialsScope(prefix, boundsMap, settings)
            scope.secondaryDials()
        }
    ) { measurables, constraints ->
        // Identify Primary
        val primaryMeasurable = measurables.find { it.layoutId == "primary" }
        val secondaryMeasurables = measurables.filter { it.layoutId != "primary" }

        val placeables = mutableListOf<Placeable>()
        val positions = mutableListOf<Pair<Int, Int>>()
        var primaryPlaceable: Placeable? = null

        // FORCE-CAP the size. This prevents buttons from stretching to screen width (Stripe Bug).
        // primaryDialMaxSize is the correct "Container Size" for these elements.
        val maxChildSize = primaryDialMaxSize.roundToPx()
        val childConstraints = Constraints(
            minWidth = 0,
            minHeight = 0,
            maxWidth = maxChildSize,
            maxHeight = maxChildSize
        )

        // 1. Measure Primary
        // Local vars to share position with secondaries
        var primaryX = 0
        var primaryY = 0

        // 1. Measure Primary
        if (primaryMeasurable != null) {
            val p = primaryMeasurable.measure(childConstraints)
            primaryPlaceable = p
            placeables.add(p)
            
            // Calc Position
            val id = "${prefix}_primary"
            val elementSettings = settings.elements[id]
            
            if (elementSettings != null && elementSettings.x >= 0) {
                // Absolute Override (0..1) relative to Container Size
                primaryX = (elementSettings.x * constraints.maxWidth).toInt() - p.width / 2
                primaryY = (elementSettings.y * constraints.maxHeight).toInt() - p.height / 2
            } else {
                // SMART DEFAULTS (Fix for "Crammed Buttons")
                // Left: 15% from left, 65% down
                // Right: 85% from left, 65% down
                val centerX = if (isLeft) (constraints.maxWidth * 0.15f) else (constraints.maxWidth * 0.85f)
                val centerY = (constraints.maxHeight * 0.65f)
                
                primaryX = centerX.toInt() - p.width / 2
                primaryY = centerY.toInt() - p.height / 2
            }
            positions.add(primaryX to primaryY)
        }

        // 2. Measure Secondaries
        secondaryMeasurables.forEachIndexed { index, measurable ->
            val p = measurable.measure(childConstraints)
            placeables.add(p)
            
            val id = "${prefix}_secondary_$index"
            val elementSettings = settings.elements[id]
            var x: Int
            var y: Int
            
            if (elementSettings != null && elementSettings.x >= 0) {
                 x = (elementSettings.x * constraints.maxWidth).toInt() - p.width / 2
                 y = (elementSettings.y * constraints.maxHeight).toInt() - p.height / 2
            } else {
                // Default Radial Logic -> Orbit the Primary Dial
                val parentData = measurable.parentData as? RadialParentData
                val degrees = parentData?.degrees ?: 0f
                
                val radiusPx = primaryDialMaxSize.toPx() * 0.8f 
                
                val angleRad = Math.toRadians((secondaryDialsBaseRotationInDegrees + degrees).toDouble())
                
                // ANCHOR to the Primary's actual position
                val cx = primaryX + (primaryPlaceable?.width ?: 0) / 2
                val cy = primaryY + (primaryPlaceable?.height ?: 0) / 2
                
                x = cx + (radiusPx * cos(angleRad)).toInt() - p.width / 2
                y = cy - (radiusPx * sin(angleRad)).toInt() - p.height / 2 
            }
            positions.add(x to y)
        }

        layout(constraints.maxWidth, constraints.maxHeight) {
            placeables.forEachIndexed { i, placeable ->
                val (x, y) = positions[i]
                placeable.place(x, y)
            }
        }
    }
}
