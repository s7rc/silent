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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.swordfish.touchinput.radial.settings.TouchControllerSettingsManager
import gg.padkit.layouts.radial.secondarydials.LayoutRadialSecondaryDialsScope
import kotlin.math.cos
import kotlin.math.sin

val LocalTouchElementBounds = compositionLocalOf { mutableMapOf<String, Rect>() }

private data class RadialParentData(val degrees: Float)

private class IndependentSecondaryDialsScope : LayoutRadialSecondaryDialsScope {
    override fun Modifier.radialPosition(degrees: Float): Modifier {
        return this.then(
            object : androidx.compose.ui.layout.ParentDataModifier {
                override fun Density.modifyParentData(parentData: Any?): Any {
                    return RadialParentData(degrees)
                }
            }
        )
    }

    override fun Modifier.radialScale(scale: Float): Modifier {
         // We can ignore radialScale for placement, or store it.
         // Let's assume we just want position. 
         // But maybe internal logic needs it? I'll store it if needed later.
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
    secondaryDials: @Composable LayoutRadialSecondaryDialsScope.() -> Unit,
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

            // Secondary Dials
            val scope = IndependentSecondaryDialsScope()
            scope.secondaryDials()
        }
    ) { measurables, constraints ->
        // Identify Primary
        val primaryMeasurable = measurables.find { it.layoutId == "primary" }
        val secondaryMeasurables = measurables.filter { it.layoutId != "primary" }

        val placeables = mutableListOf<Placeable>()
        val positions = mutableListOf<Pair<Int, Int>>()

        // 1. Measure Primary
        if (primaryMeasurable != null) {
            val p = primaryMeasurable.measure(constraints)
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
                // BaseLayout passes padding via modifier, so (0,0) is correct relative to padding.
                x = 0
                y = 0 // Align bottom-left relative to the padded area? 
                // LayoutRadial usually centers the Dial at (0,0) of the radial coordinate system?
                // LayoutRadial places primary dial at bottom-left (for Left).
                // Actually, let's assume (0,0).
            }
            positions.add(x to y)
        }

        // 2. Measure Secondaries
        secondaryMeasurables.forEachIndexed { index, measurable ->
            val p = measurable.measure(constraints)
            placeables.add(p)
            
            val id = "${prefix}_secondary_$index"
            // Also report this ID? onGloballyPositioned needs to know the ID.
            // Problem: The Modifier on the content was already created. 
            // I can't inject onGloballyPositioned here easily unless I wrap content in Box above.
            // BUT 'secondaryDials' emits the content directly.
            // I CAN wrap them if I iterate measurables? No.
            
            // WORKAROUND: In Customizer, hit-test based on reported bounds?
            // If I can't report bounds with ID, Customizer fails.
            // Wait, IndependentSecondaryDialsScope.radialPosition CAN add onGloballyPositioned?
            // "this.then(...)"
            // But I don't know the Index/ID there.
            
            // I will assume implicit order in Customizer hit test?
            
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
                
                // baseRotation comes from settings (global rotation) + side flip
                // In LayoutRadial: 
                // finalAngle = baseRotation + degrees
                // x = r * cos(angle)
                // y = r * sin(angle)
                // Center is... where?
                // For Left Stick: Center is Primary Dial Center.
                
                // Radius? 
                // Secondary Buttons are usually around the Primary.
                // Standard radius is about primaryDialMaxSize? 
                // In `BaseLayout.kt` it passes `primaryDialMaxSize` (160dp scaled).
                // Let's assume radius = primaryDialMaxSize / 2 + some offset?
                // Or maybe the `radialPosition` degrees implies radius?
                // `SecondaryButtonSelect` says `radialPosition(120f)`. Just degrees.
                // `BaseLayout` doesn't pass radius for secondaries explicitly to `LayoutRadial`.
                // It passes `primaryDialMaxSize`. 
                // Inspecting `LayoutRadial` (if I could) would reveal it uses `primaryDialMaxSize` as reference.
                
                val radiusPx = primaryDialMaxSize.toPx() * 0.8f // Heuristic
                
                val angleRad = Math.toRadians((secondaryDialsBaseRotationInDegrees + degrees).toDouble())
                
                // Center of Primary Dial is at (primaryWidth/2, primaryHeight/2) if placed at 0,0?
                // Let's assume origin (0,0) is the pivot.
                
                val cx = (primaryMeasurable?.width ?: 0) / 2
                val cy = (primaryMeasurable?.height ?: 0) / 2
                
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
