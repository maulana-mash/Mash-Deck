package com.mash.deck.model

import android.graphics.Bitmap

enum class DeckPreset(val displayName: String, val columns: Int, val rows: Int) {
    MINI("Mini (6 Keys)", 3, 2),
    CLASSIC("Classic (15 Keys)", 5, 3),
    XL("XL (32 Keys)", 8, 4),
    CUSTOM("Custom", 5, 3);

    val totalKeys: Int get() = columns * rows
}

enum class DeckBackground(val displayName: String) {
    DEEP_INDIGO("Indigo Mesh"),
    CARBON_WEAVE("Carbon Weave"),
    AURORA_GLOW("Aurora Glow"),
    BRUSHED_TITANIUM("Brushed Titanium"),
    SOLID_DARK("Solid Obsidian")
}

data class DeckConfig(
    val preset: DeckPreset = DeckPreset.CLASSIC,
    val customColumns: Int = 5,
    val customRows: Int = 3,
    val isCustom: Boolean = false,
    val background: DeckBackground = DeckBackground.DEEP_INDIGO
) {
    val activeColumns: Int get() = if (isCustom) customColumns else preset.columns
    val activeRows: Int get() = if (isCustom) customRows else preset.rows
    val totalKeys: Int get() = activeColumns * activeRows
}

data class KeySlot(
    val index: Int,
    val bitmap: Bitmap? = null,
    val title: String? = null,
    val isPressed: Boolean = false
)
