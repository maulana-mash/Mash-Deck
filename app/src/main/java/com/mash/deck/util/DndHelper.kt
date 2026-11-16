package com.mash.deck.util

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings

object DndHelper {
    fun isDndAccessGranted(context: Context): Boolean {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        return manager?.isNotificationPolicyAccessGranted == true
    }

    fun openDndSettings(context: Context) {
        val intent = Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(intent)
    }

    fun setDndEnabled(context: Context, enabled: Boolean) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
        if (!manager.isNotificationPolicyAccessGranted) return

        try {
            if (enabled) {
                // Suppress floating notifications (priority only or alarms only)
                manager.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_PRIORITY)
            } else {
                manager.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_ALL)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
