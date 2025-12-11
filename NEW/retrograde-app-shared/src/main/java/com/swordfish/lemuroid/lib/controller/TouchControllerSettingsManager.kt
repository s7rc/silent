package com.swordfish.lemuroid.lib.controller

import android.content.Context
import android.content.SharedPreferences

import dagger.Lazy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

class TouchControllerSettingsManager(
    private val context: Context,
    private val controllerID: TouchControllerID,
    private val sharedPreferences: Lazy<SharedPreferences>,
    private val orientation: Orientation,
) {
    enum class Orientation {
        PORTRAIT,
        LANDSCAPE,
    }

    data class Settings(
        val opacity: Float = DEFAULT_OPACITY,
        val scale: Float = DEFAULT_SCALE,
        val rotation: Float = DEFAULT_ROTATION,
        val marginX: Float = DEFAULT_MARGIN_X,
        val marginY: Float = DEFAULT_MARGIN_Y,
        val elements: Map<String, ElementSettings> = emptyMap(),
    ) : java.io.Serializable

    data class ElementSettings(
        val x: Float = 0f,
        val y: Float = 0f,
        val scale: Float = 1f,
    ) : java.io.Serializable

    suspend fun retrieveSettings(elementIds: Set<String> = emptySet()): Settings =
        withContext(Dispatchers.IO) {
            val sp = sharedPreferences.get()
            val elements = elementIds.associateWith { retrieveElementSettings(it) }

            Settings(
                opacity =
                    indexToFloat(
                        sp.getInt(
                            "virtual_pad_opacity_${controllerID}_${orientation.ordinal}",
                            floatToIndex(DEFAULT_OPACITY),
                        ),
                    ),
                scale =
                    indexToFloat(
                        sp.getInt(
                            "virtual_pad_scale_${controllerID}_${orientation.ordinal}",
                            floatToIndex(DEFAULT_SCALE),
                        ),
                    ),
                rotation =
                    indexToFloat(
                        sp.getInt(
                            "virtual_pad_rotation_${controllerID}_${orientation.ordinal}",
                            floatToIndex(DEFAULT_ROTATION),
                        ),
                    ),
                marginX =
                    indexToFloat(
                        sp.getInt(
                            "virtual_pad_margin_x_${controllerID}_${orientation.ordinal}",
                            floatToIndex(DEFAULT_MARGIN_X),
                        ),
                    ),
                marginY =
                    indexToFloat(
                        sp.getInt(
                            "virtual_pad_margin_y_${controllerID}_${orientation.ordinal}",
                            floatToIndex(DEFAULT_MARGIN_Y),
                        ),
                    ),
                elements = elements
            )
        }

    suspend fun storeSettings(settings: Settings): Unit =
        withContext(Dispatchers.IO) {
            val editor = sharedPreferences.get().edit()
            editor.putInt(
                "virtual_pad_opacity_${controllerID}_${orientation.ordinal}",
                floatToIndex(settings.opacity),
            )
            editor.putInt(
                "virtual_pad_scale_${controllerID}_${orientation.ordinal}",
                floatToIndex(settings.scale),
            )
            editor.putInt(
                "virtual_pad_rotation_${controllerID}_${orientation.ordinal}",
                floatToIndex(settings.rotation),
            )
            editor.putInt(
                "virtual_pad_margin_x_${controllerID}_${orientation.ordinal}",
                floatToIndex(settings.marginX),
            )
            editor.putInt(
                "virtual_pad_margin_y_${controllerID}_${orientation.ordinal}",
                floatToIndex(settings.marginY),
            )
            editor.apply()
            
            settings.elements.forEach { (id, elementSettings) ->
                storeElementSettings(id, elementSettings)
            }
        }

    suspend fun retrieveElementSettings(elementId: String): ElementSettings =
        withContext(Dispatchers.IO) {
            val sp = sharedPreferences.get()
            ElementSettings(
                x =
                    indexToFloat(
                        sp.getInt(getElementKey(elementId, "x"), floatToIndex(DEFAULT_ELEMENT_X)),
                    ),
                y =
                    indexToFloat(
                        sp.getInt(getElementKey(elementId, "y"), floatToIndex(DEFAULT_ELEMENT_Y)),
                    ),
                scale =
                    indexToFloat(
                        sp.getInt(getElementKey(elementId, "scale"), floatToIndex(DEFAULT_ELEMENT_SCALE)),
                    ),
            )
        }

    suspend fun storeElementSettings(
        elementId: String,
        settings: ElementSettings,
    ) = withContext(Dispatchers.IO) {
        sharedPreferences.get().edit().apply {
            putInt(getElementKey(elementId, "x"), floatToIndex(settings.x))
            putInt(getElementKey(elementId, "y"), floatToIndex(settings.y))
            putInt(getElementKey(elementId, "scale"), floatToIndex(settings.scale))
        }.apply()
    }

    private fun getElementKey(
        elementId: String,
        property: String,
    ): String {
        return "element_${elementId}_${property}_${controllerID}_${orientation.ordinal}"
    }

    private fun indexToFloat(index: Int): Float = index / 100f

    private fun floatToIndex(value: Float): Int = (value * 100).roundToInt()

    companion object {
        const val DEFAULT_OPACITY = 1.0f
        const val DEFAULT_SCALE = 0.5f
        const val DEFAULT_ROTATION = 0.0f
        const val DEFAULT_MARGIN_X = 0.0f
        const val DEFAULT_MARGIN_Y = 0.0f
        
        const val DEFAULT_ELEMENT_SCALE = 1.0f 
        const val DEFAULT_ELEMENT_X = -1.0f
        const val DEFAULT_ELEMENT_Y = -1.0f

        const val MAX_ROTATION = 45f
        const val MIN_SCALE = 0.75f
        const val MAX_SCALE = 1.5f

        const val MAX_MARGINS = 96f
    }
}
