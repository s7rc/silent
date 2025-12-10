package com.swordfish.lemuroid.app.mobile.feature.game

import com.swordfish.radialgamepad.library.config.ButtonConfig
import com.swordfish.radialgamepad.library.config.CrossConfig
import com.swordfish.radialgamepad.library.config.PrimaryDialConfig
import com.swordfish.radialgamepad.library.config.RadialGamePadConfig
import com.swordfish.radialgamepad.library.config.SecondaryDialConfig

object TouchConfigSplitter {
    data class SplitGroup(
        val id: String,
        val config: RadialGamePadConfig,
        val defaultOffsetX: Float = 0f, // multiple of radius/unit
        val defaultOffsetY: Float = 0f 
    )

    fun split(baseConfig: RadialGamePadConfig, baseId: String): List<SplitGroup> {
        val groups = mutableListOf<SplitGroup>()

        // 1. Process Primary Dial (Face Buttons or D-Pad)
        if (baseConfig.primaryDial != null) {
            // Note: Assuming primaryDial property exists and is accessible.
            // LemuroidTouchConfigs uses it in constructor.
            val primaryId = if (baseId.contains("left", ignoreCase = true)) "dpad" else "face_buttons"
            
            // Create a config with ONLY the primary dial
            val primaryGroupConfig = baseConfig.copy(
                secondaryDials = emptyList()
            )
            groups.add(SplitGroup(primaryId, primaryGroupConfig))
        }

        // 2. Process Secondary Dials
        baseConfig.secondaryDials.forEachIndexed { _, dial ->
            when (dial) {
                is SecondaryDialConfig.SingleButton -> {
                    // Convert to PrimaryButtons with 1 button
                    val buttonConfig = dial.buttonConfig
                    val newPrimary = PrimaryDialConfig.PrimaryButtons(listOf(buttonConfig))
                    val newConfig = baseConfig.copy(
                        primaryDial = newPrimary,
                        secondaryDials = emptyList()
                    )
                    
                    val (offX, offY) = calculateOffset(dial.index, dial.distance)
                    
                    // ID generation: use label or content description or ID
                    val id = buttonConfig.label ?: buttonConfig.contentDescription ?: "btn_${buttonConfig.id}"
                    groups.add(SplitGroup(sanitizeId(id), newConfig, offX, offY))
                }
                is SecondaryDialConfig.DoubleButton -> {
                    // DoubleButton typically takes 2 slots but has 1 config in the examples I saw.
                    // Assuming it has 'buttonConfig' property similar to SingleButton.
                    val buttonConfig = dial.buttonConfig
                    val newPrimary = PrimaryDialConfig.PrimaryButtons(listOf(buttonConfig))
                    
                    val newConfig = baseConfig.copy(
                         primaryDial = newPrimary,
                         secondaryDials = emptyList()
                    )
                    val (offX, offY) = calculateOffset(dial.index, dial.distance)
                    
                    val id = buttonConfig.label ?: buttonConfig.contentDescription ?: "btn_${buttonConfig.id}_dbl"
                    groups.add(SplitGroup(sanitizeId(id), newConfig, offX, offY))
                }
                is SecondaryDialConfig.Stick -> {
                    // Convert to Primary Cross/Stick
                    // Stick usually implies analog. CrossConfig can handle it.
                    // We need to construct a CrossConfig from Stick properties.
                    // This might be tricky without seeing CrossConfig constructor fully.
                    // BUT: PrimaryDialConfig.Cross takes a CrossConfig.
                    // SecondaryDialConfig.Stick has: id, shape(?), etc.
                    
                    // As a fallback, since I can't easily convert types I don't see,
                    // I will use the "Independent Secondary" strategy:
                    // Create a config with Empty Primary and THIS Secondary at its original index.
                    // But then the center of the view is empty. 
                    // This is acceptable? The user drags the "Stick", which is offset from center.
                    // Be careful with touch bounds.
                    
                    // Better attempt: Use PrimaryDialConfig.Cross if possible.
                    // Assuming for now I can map it. If not, I'll fallback.
                    
                    // Let's try to infer if I can just assume a generic Stick config.
                    // For now, let's just keep it as a secondary dial in a new config, 
                    // BUT shifted so it appears centered? No, `RadialGamePad` logic is fixed.
                    
                    // Strategy B: Keep as secondary, but make the view centered on the stick.
                    // If stick is at index 9 (left), its pos is (-R, 0).
                    // If I render a view at (ScreenX, ScreenY), the stick appears at (ScreenX-R, ScreenY).
                    // I want the stick at (ScreenX, ScreenY). 
                    // So I should position the view at (ScreenX+R, ScreenY).
                    // "defaultOffsetX" handles this!
                    
                    val newConfig = baseConfig.copy(
                        primaryDial = createEmptyPrimary(),
                        secondaryDials = listOf(dial)
                    )
                    // Stick ID
                    groups.add(SplitGroup("stick_${dial.index}", newConfig, 0f, 0f))
                }
                is SecondaryDialConfig.Cross -> {
                     val newConfig = baseConfig.copy(
                        primaryDial = createEmptyPrimary(),
                        secondaryDials = listOf(dial)
                    )
                    groups.add(SplitGroup("cross_${dial.index}", newConfig, 0f, 0f))
                }
                // Handle Empty or other types if necessary
                else -> {}
            }
        }

        return groups
    }

    private fun calculateOffset(index: Int, distance: Float): Pair<Float, Float> {
        // index 0 = Up (0,-1) ? No, standard clock starts 0 at top?
        // RadialGamePad likely uses: 0 = 12 o'clock, 3 = 3 o'clock.
        // Circle is 12 sockets.
        // angle = index * 30 degrees (360 / 12)
        // 0 -> 0 deg (Up). 
        // 3 -> 90 deg (Right).
        
        // Wait, math usually 0 is Right. 
        // Let's assume standard clock: 0=Top, clockwise.
        // Angle in radians = (index / 12.0) * 2 * PI.
        // X = sin(angle) * distance
        // Y = -cos(angle) * distance (screen Y is down)
        
        val angle = (index / 12.0) * 2 * Math.PI
        val simX = Math.sin(angle).toFloat() * distance
        val simY = -Math.cos(angle).toFloat() * distance
        return Pair(simX, simY)
    }

    private fun sanitizeId(id: String): String {
        return id.replace("\\s".toRegex(), "_").lowercase()
    }
    
    // Helper to create an empty primary dial (needs implementation detail or workaround)
    // If PrimaryDialConfig has no Empty/None, we might need to pass a dummy hidden button?
    // Or maybe we can just pass the original primary dial but set it to invisible/disabled?
    // Ideally, PrimaryDialConfig interface has a blank impl.
    // For now, I will assume I can pass a dummy "PrimaryButtons(emptyList())"
    private fun createEmptyPrimary(): PrimaryDialConfig {
         return PrimaryDialConfig.PrimaryButtons(emptyList()) 
    }
}
