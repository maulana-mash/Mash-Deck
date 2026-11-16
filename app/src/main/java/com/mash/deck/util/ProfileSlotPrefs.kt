package com.mash.deck.util

import android.content.Context
import org.json.JSONArray

object ProfileSlotPrefs {
    private const val PREFS_NAME = "mash_deck_profiles"
    private const val KEY_ACTIVE_DEVICE = "active_virtual_device"
    private const val KEY_DEVICE_SLOTS = "virtual_device_slots"
    const val DEFAULT_DEVICE_NAME = "Mash Stream Deck"

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getActiveDevice(context: Context): String =
        prefs(context).getString(KEY_ACTIVE_DEVICE, DEFAULT_DEVICE_NAME)
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?: DEFAULT_DEVICE_NAME

    fun setActiveDevice(context: Context, name: String) {
        val clean = name.trim().ifEmpty { DEFAULT_DEVICE_NAME }
        prefs(context).edit().putString(KEY_ACTIVE_DEVICE, clean).apply()
        addOrMergeSlots(context, listOf(clean))
    }

    fun getSlots(context: Context): List<String> {
        val raw = prefs(context).getString(KEY_DEVICE_SLOTS, null)
        val result = linkedSetOf(DEFAULT_DEVICE_NAME)
        if (!raw.isNullOrEmpty()) {
            runCatching {
                val arr = JSONArray(raw)
                for (i in 0 until arr.length()) {
                    val item = arr.optString(i).trim()
                    if (item.isNotEmpty()) result.add(item)
                }
            }
        }
        result.add(getActiveDevice(context))
        return result.toList()
    }

    fun addOrMergeSlots(context: Context, newNames: Collection<String>): List<String> {
        val current = getSlots(context).toMutableList()
        for (candidate in newNames) {
            val clean = candidate.trim()
            if (clean.isNotEmpty() && current.none { it.equals(clean, ignoreCase = true) }) {
                current.add(clean)
            }
        }
        saveSlots(context, current)
        return current
    }

    fun removeSlot(context: Context, name: String): List<String> {
        val clean = name.trim()
        val current = getSlots(context).filterNot { it.equals(clean, ignoreCase = true) }
        val safeList = current.ifEmpty { listOf(DEFAULT_DEVICE_NAME) }
        saveSlots(context, safeList)
        if (getActiveDevice(context).equals(clean, ignoreCase = true)) {
            setActiveDevice(context, safeList.first())
        }
        return safeList
    }

    private fun saveSlots(context: Context, slots: List<String>) {
        val arr = JSONArray()
        slots.forEach { arr.put(it) }
        prefs(context).edit().putString(KEY_DEVICE_SLOTS, arr.toString()).apply()
    }
}
