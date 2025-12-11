package com.swordfish.lemuroid.lib.controller

import android.content.Context
import android.content.SharedPreferences
import com.swordfish.touchinput.controller.R
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
        val scale: Float = DEFAULT_SCALE,
        val rotation: Float = DEFAULT_ROTATION,
        val marginX: Float = DEFAULT_MARGIN_X,
        val marginY: Float = DEFAULT_MARGIN_Y,
        val elements: Map<String, ElementSettings> = emptyMap(),
    ) : java.io.Serializable

    /* ... */

    data class ElementSettings(
        val x: Float = 0f,
        val y: Float = 0f,
        val scale: Float = 1f,
    ) : java.io.Serializable

    suspend fun retrieveSettings(elementIds: Set<String> = emptySet()): Settings =
        withContext(Dispatchers.IO) {
            val sharedPreferences = sharedPreferences.get()
            val elementSettings = elementIds.associateWith { id ->
                 retrieveElementSettings(id) // helper we added earlier should work here if it uses SP
                 // Wait, retrieveElementSettings created new SP access.
                 // Optimization: reuse SP instance.
                 // But helper is suspend. 
                 // I'll inline the logic or rely on helper. Helper reads SP again. It's fine for now (IO dispatcher).
                 // Actually I can call retrieveElementSettings(id) directly.
                 // BUT I need to make sure I am not inside another coroutine that blocks?
                 // `withContext` is fine.
            }
            // Actually reusing logic:
            val elements = elementIds.associateWith { retrieveElementSettings(it) }

            Settings(
                scale =
                    indexToFloat(
                        sharedPreferences.getInt(
                            getPreferenceString(R.string.pref_key_virtual_pad_scale, orientation),
                            floatToIndex(DEFAULT_SCALE),
                        ),
                    ),
                rotation =
                    indexToFloat(
                        sharedPreferences.getInt(
                            getPreferenceString(R.string.pref_key_virtual_pad_rotation, orientation),
                            floatToIndex(DEFAULT_ROTATION),
                        ),
                    ),
                marginX =
                    indexToFloat(
                        sharedPreferences.getInt(
                            getPreferenceString(R.string.pref_key_virtual_pad_margin_x, orientation),
                            floatToIndex(DEFAULT_MARGIN_X),
                        ),
                    ),
                marginY =
                    indexToFloat(
                        sharedPreferences.getInt(
                            getPreferenceString(R.string.pref_key_virtual_pad_margin_y, orientation),
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
                getPreferenceString(R.string.pref_key_virtual_pad_scale, orientation),
                floatToIndex(settings.scale),
            )
            editor.putInt(
                getPreferenceString(R.string.pref_key_virtual_pad_rotation, orientation),
                floatToIndex(settings.rotation),
            )
            editor.putInt(
                getPreferenceString(R.string.pref_key_virtual_pad_margin_x, orientation),
                floatToIndex(settings.marginX),
            )
            editor.putInt(
                getPreferenceString(R.string.pref_key_virtual_pad_margin_y, orientation),
                floatToIndex(settings.marginY),
            )
            editor.apply()
            
            // Store elements
            settings.elements.forEach { (id, elementSettings) ->
                storeElementSettings(id, elementSettings)
            }
        }

    private fun indexToFloat(index: Int): Float = index / 100f

    private fun floatToIndex(value: Float): Int = (value * 100).roundToInt()

    private fun getPreferenceString(
        preferenceStringId: Int,
        orientation: Orientation,
    ): String {
        return "${context.getString(preferenceStringId)}_${controllerID}_${orientation.ordinal}"
    }

    /* Element Settings (Per-Button) Support */

    data class ElementSettings(
        val x: Float = 0f,
        val y: Float = 0f,
        val scale: Float = 1f,
    )

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

    companion object {
        const val DEFAULT_SCALE = 0.5f
        const val DEFAULT_ROTATION = 0.0f
        const val DEFAULT_MARGIN_X = 0.0f
        const val DEFAULT_MARGIN_Y = 0.0f

        // I'll use 0f as default for offset.
        
        const val DEFAULT_ELEMENT_SCALE = 1.0f 
        const val DEFAULT_ELEMENT_X = -1.0f
        const val DEFAULT_ELEMENT_Y = -1.0f

        const val MAX_ROTATION = 45f
        const val MIN_SCALE = 0.75f
        const val MAX_SCALE = 1.5f

        const val MAX_MARGINS = 96f
    }
}
