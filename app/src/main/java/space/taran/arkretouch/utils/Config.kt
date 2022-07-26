package space.taran.arkretouch.utils

import android.content.Context
import com.simplemobiletools.commons.helpers.BaseConfig

class Config(context: Context) : BaseConfig(context) {
    companion object {
        fun newInstance(context: Context) = Config(context)
    }

    var lastEditorCropAspectRatio: Int
        get() = prefs.getInt(LAST_EDITOR_CROP_ASPECT_RATIO, ASPECT_RATIO_FREE)
        set(lastEditorCropAspectRatio) = prefs.edit()
            .putInt(LAST_EDITOR_CROP_ASPECT_RATIO, lastEditorCropAspectRatio).apply()

    var lastEditorCropOtherAspectRatioX: Float
        get() = prefs.getFloat(LAST_EDITOR_CROP_OTHER_ASPECT_RATIO_X, 2f)
        set(lastEditorCropOtherAspectRatioX) = prefs.edit()
            .putFloat(LAST_EDITOR_CROP_OTHER_ASPECT_RATIO_X, lastEditorCropOtherAspectRatioX)
            .apply()

    var lastEditorCropOtherAspectRatioY: Float
        get() = prefs.getFloat(LAST_EDITOR_CROP_OTHER_ASPECT_RATIO_Y, 1f)
        set(lastEditorCropOtherAspectRatioY) = prefs.edit()
            .putFloat(LAST_EDITOR_CROP_OTHER_ASPECT_RATIO_Y, lastEditorCropOtherAspectRatioY)
            .apply()

    var lastEditorDrawColor: Int
        get() = prefs.getInt(LAST_EDITOR_DRAW_COLOR, primaryColor)
        set(lastEditorDrawColor) = prefs.edit().putInt(LAST_EDITOR_DRAW_COLOR, lastEditorDrawColor)
            .apply()

    var lastEditorBrushSize: Int
        get() = prefs.getInt(LAST_EDITOR_BRUSH_SIZE, 50)
        set(lastEditorBrushSize) = prefs.edit().putInt(LAST_EDITOR_BRUSH_SIZE, lastEditorBrushSize)
            .apply()

    var crashReport:Boolean
        get() = prefs.getBoolean(CRASH_REPORT_ENABLE, true)
        set(isEnable) = prefs.edit().putBoolean(CRASH_REPORT_ENABLE, isEnable)
            .apply()

    var lastEditorColorAlpha:Int
        get() = prefs.getInt(LAST_EDITOR_COLOR_ALPHA,255)
        set(alpha) = prefs.edit().putInt(LAST_EDITOR_COLOR_ALPHA, alpha)
            .apply()
}
