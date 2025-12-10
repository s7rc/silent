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
import com.swordfish.touchinput.radial.settings.TouchControllerSettingsManager
import kotlin.math.cos
import kotlin.math.sin

val LocalTouchElementBounds = compositionLocalOf { mutableMapOf<String, Rect>() }

private data class RadialParentData(val degrees: Float)

interface LemuroidRadialScope {
    fun Modifier.radialPosition(degrees: Float): Modifier
    fun Modifier.radialScale(scale: Float): Modifier
}

private class IndependentSecondaryDialsScope : LemuroidRadialScope {
    override fun Modifier.radialPosition(degrees: Float): Modifier {
        return this.then(
            object : androidx.compose.ui.layout.ParentDataModifier {
                override fun androidx.compose.ui.unit.Density.modifyParentData(parentData: Any?): Any {
                    return RadialParentData(degrees)
                }
            }
        )
    }

    override fun Modifier.radialScale(scale: Float): Modifier {
         return this
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
            ) {
                primaryDial()
            }

            // Secondary Dials l
            val scope = IndependentSecondaryDialsScope()
            scope.secondaryDials()
        }
    ) { measurables, constraints ->
        // Identify Primary
        val primaryMeasurable = measurables.find { it.layoutId == "primary" }
        val secondaryMeasurables = measurables.filter { it.layoutId != "primary" }

        val placeables = mutableListOf<Placeable>()
        val positions = mutableListOf<Pair<Int, Int>>()
        var primaryPlaceable: Placeable? = null

        // Use incoming constraints but relax minimums so children aren't forced to fill screen
        val childConstraints = constraints.copy(minWidth = 0, minHeight = 0)

        // 1. Measure Primary
        if (primaryMeasurable != null) {
            val p = primaryMeasurable.measure(childConstraints)
            primaryPlaceable = p
            placeables.add(p)
            
            // Calc Position
            val id = "${prefix}_primary"
            val elementSettings = settings.elements[id]
            
            val x: Int
            val y: Int
            
            if (elementSettings != null && elementSettings.x >= 0) {
                // Absolute Override (0..1) relative to Container Size
                x = (elementSettings.x * constraints.maxWidth).toInt() - p.width / 2
                y = (elementSettings.y * constraints.maxHeight).toInt() - p.height / 2
            } else {
                // Default: Bottom-Left (or Right) corner
                x = 0
                y = 0 
            }
            positions.add(x to y)
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
                // Default Radial Logic
                val parentData = measurable.parentData as? RadialParentData
                val degrees = parentData?.degrees ?: 0f
                
                val radiusPx = primaryDialMaxSize.toPx() * 0.8f // Heuristic
                
                val angleRad = Math.toRadians((secondaryDialsBaseRotationInDegrees + degrees).toDouble())
                
                val cx = (primaryPlaceable?.width ?: 0) / 2
                val cy = (primaryPlaceable?.height ?: 0) / 2
                
                x = cx + (radiusPx * cos(angleRad)).toInt() - p.width / 2
                y = cy - (radiusPx * sin(angleRad)).toInt() - p.height / 2 // Compose Y is down
                
                // Note: sin/cos direction might need tuning.
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
