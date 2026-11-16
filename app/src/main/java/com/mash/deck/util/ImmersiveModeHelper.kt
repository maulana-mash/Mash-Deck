package com.mash.deck.util

import android.app.Activity
import android.os.Build
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

object ImmersiveModeHelper {
    /**
     * Enables true native fullscreen.
     * System bars (status & nav) are hidden and transient on swipe.
     */
    fun enableImmersiveMode(activity: Activity) {
        val window = activity.window
        WindowCompat.setDecorFitsSystemWindows(window, false)

        val controller = WindowCompat.getInsetsController(window, window.decorView)
        controller.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller.hide(WindowInsetsCompat.Type.systemBars())
    }

    fun disableImmersiveMode(activity: Activity) {
        val window = activity.window
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        controller.show(WindowInsetsCompat.Type.systemBars())
    }

    /**
     * Starts native Android Screen Pinning / Lock Task Mode.
     * In this mode, system notifications, home, recents, and status bar
     * are completely locked down by Android OS.
     */
    fun startLockTask(activity: Activity): Boolean {
        return try {
            activity.startLockTask()
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    fun stopLockTask(activity: Activity): Boolean {
        return try {
            activity.stopLockTask()
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
}
