package com.mash.deck.util

import android.content.Context
import com.mash.deck.model.DeckBackground
import com.mash.deck.model.DeckConfig
import com.mash.deck.model.DeckPreset
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

    private fun profilePrefix(name: String): String {
        val safeKey = name.trim().lowercase().replace(Regex("[^a-z0-9_]"), "_")
        return "cfg_${safeKey}_"
    }

    fun getProfileConfig(context: Context, profileName: String): DeckConfig {
        val clean = profileName.trim().ifEmpty { DEFAULT_DEVICE_NAME }
        val p = prefs(context)
        val prefix = profilePrefix(clean)

        if (!p.contains("${prefix}preset") && !p.contains("${prefix}is_custom")) {
            // If default slot has legacy global prefs, migrate from mash_deck_prefs
            val legacy = context.getSharedPreferences("mash_deck_prefs", Context.MODE_PRIVATE)
            val legacyPreset = legacy.getString("deck_preset", DeckPreset.CLASSIC.name)
            val preset = runCatching { DeckPreset.valueOf(legacyPreset ?: DeckPreset.CLASSIC.name) }
                .getOrDefault(DeckPreset.CLASSIC)
            val isCustom = legacy.getBoolean("is_custom", false)
            val customCols = legacy.getInt("custom_cols", 5)
            val customRows = legacy.getInt("custom_rows", 3)
            val bgName = legacy.getString("deck_background", DeckBackground.DEEP_INDIGO.name)
            val bg = runCatching { DeckBackground.valueOf(bgName ?: DeckBackground.DEEP_INDIGO.name) }
                .getOrDefault(DeckBackground.DEEP_INDIGO)

            val initialConfig = DeckConfig(
                preset = preset,
                isCustom = isCustom,
                customColumns = customCols,
                customRows = customRows,
                background = bg
            )
            saveProfileConfig(context, clean, initialConfig)
            return initialConfig
        }

        val presetName = p.getString("${prefix}preset", DeckPreset.CLASSIC.name)
        val preset = runCatching { DeckPreset.valueOf(presetName ?: DeckPreset.CLASSIC.name) }
            .getOrDefault(DeckPreset.CLASSIC)
        val isCustom = p.getBoolean("${prefix}is_custom", false)
        val customCols = p.getInt("${prefix}custom_cols", 5)
        val customRows = p.getInt("${prefix}custom_rows", 3)
        val bgName = p.getString("${prefix}background", DeckBackground.DEEP_INDIGO.name)
        val background = runCatching { DeckBackground.valueOf(bgName ?: DeckBackground.DEEP_INDIGO.name) }
            .getOrDefault(DeckBackground.DEEP_INDIGO)

        return DeckConfig(
            preset = preset,
            isCustom = isCustom,
            customColumns = customCols,
            customRows = customRows,
            background = background
        )
    }

    fun saveProfileConfig(context: Context, profileName: String, config: DeckConfig) {
        val clean = profileName.trim().ifEmpty { DEFAULT_DEVICE_NAME }
        val prefix = profilePrefix(clean)
        prefs(context).edit()
            .putString("${prefix}preset", config.preset.name)
            .putBoolean("${prefix}is_custom", config.isCustom)
            .putInt("${prefix}custom_cols", config.customColumns)
            .putInt("${prefix}custom_rows", config.customRows)
            .putString("${prefix}background", config.background.name)
            .apply()
    }
}
