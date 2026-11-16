package com.mash.deck.util

import android.app.Activity
import android.content.pm.ActivityInfo

enum class ScreenOrientationOption(val displayName: String) {
    PORTRAIT("Fixed Portrait"),
    LANDSCAPE("Fixed Landscape"),
    SENSOR("Auto Sensor")
}

object OrientationHelper {
    fun applyOrientation(activity: Activity, option: ScreenOrientationOption) {
        activity.requestedOrientation = when (option) {
            ScreenOrientationOption.PORTRAIT -> ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            ScreenOrientationOption.LANDSCAPE -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            ScreenOrientationOption.SENSOR -> ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR
        }
    }
}
