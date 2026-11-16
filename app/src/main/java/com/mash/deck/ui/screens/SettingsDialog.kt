package com.mash.deck.ui.screens

import android.os.Build
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.mash.deck.model.DeckBackground
import com.mash.deck.model.DeckConfig
import com.mash.deck.model.DeckPreset
import com.mash.deck.ui.theme.*
import com.mash.deck.util.ScreenOrientationOption
import kotlinx.coroutines.launch

private enum class SubSheet {
    LAYOUT,
    ORIENTATION,
    FACEPLATE,
    PROFILE_SLOT
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsDialog(
    config: DeckConfig,
    currentOrientation: ScreenOrientationOption,
    connectedServerName: String,
    isConnected: Boolean,
    isMasterFocusActive: Boolean,
    isKeepScreenOn: Boolean,
    isPureFullscreen: Boolean,
    isLockTaskActive: Boolean,
    isDndActive: Boolean,
    hasDndPermission: Boolean,
    profileSlots: List<String> = listOf("Mash Stream Deck"),
    activeProfile: String = "Mash Stream Deck",
    onConfigChange: (DeckConfig) -> Unit,
    onOrientationChange: (ScreenOrientationOption) -> Unit,
    onToggleMasterFocus: (Boolean) -> Unit,
    onToggleKeepScreenOn: (Boolean) -> Unit,
    onTogglePureFullscreen: (Boolean) -> Unit,
    onToggleLockTask: (Boolean) -> Unit,
    onToggleDnd: (Boolean) -> Unit,
    onRequestDndPermission: () -> Unit,
    onSelectProfile: (String) -> Unit = {},
    onAddProfileSlot: (String) -> Unit = {},
    onRemoveProfileSlot: (String) -> Unit = {},
    onDisconnect: () -> Unit,
    onDismiss: () -> Unit
) {
    var activeSubSheet by remember { mutableStateOf<SubSheet?>(null) }
    var isCustom by remember { mutableStateOf(config.isCustom) }
    var cols by remember { mutableStateOf(config.customColumns.toFloat()) }
    var rows by remember { mutableStateOf(config.customRows.toFloat()) }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val coroutineScope = rememberCoroutineScope()

    fun closeSheet() {
        coroutineScope.launch {
            sheetState.hide()
        }.invokeOnCompletion {
            if (!sheetState.isVisible) {
                onDismiss()
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color(0xFF141416),
        scrimColor = Color.Black.copy(alpha = 0.65f),
        dragHandle = {
            BottomSheetDefaults.DragHandle(
                color = Color(0xFF52525B),
                width = 44.dp,
                height = 5.dp
            )
        },
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 4.dp)
            ) {
                // Header: Down Arrow & Centered Title
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp)
                ) {
                    IconButton(
                        onClick = { closeSheet() },
                        modifier = Modifier
                            .size(36.dp)
                            .align(Alignment.CenterStart)
                    ) {
                        Icon(
                            imageVector = Icons.Default.KeyboardArrowDown,
                            contentDescription = "Close Settings",
                            tint = Color(0xFF6B86A4),
                            modifier = Modifier.size(28.dp)
                        )
                    }

                    Text(
                        text = "Settings",
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.align(Alignment.Center)
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Group 1: Device Profile (Akun Virtual Device) at the very top
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color(0xFF1C1C1E))
                        .border(1.dp, Color(0xFF2C2C2E), RoundedCornerShape(16.dp))
                        .clickable { activeSubSheet = SubSheet.PROFILE_SLOT }
                        .padding(horizontal = 18.dp, vertical = 14.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color(0xFF2C2C2E)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Dns,
                                contentDescription = "Device Profile",
                                tint = Color(0xFF8BAAC9),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Device Profile",
                                color = Color(0xFFA1A1AA),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = activeProfile,
                                color = Color.White,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Icon(
                            imageVector = Icons.Default.ChevronRight,
                            contentDescription = "Switch Profile",
                            tint = Color(0xFF71717A),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Main Device Card
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color(0xFF1C1C1E))
                        .border(1.dp, Color(0xFF2C2C2E), RoundedCornerShape(16.dp))
                        .padding(18.dp)
                ) {
                    Column {
                        // Title & Host
                        Text(
                            text = activeProfile,
                            color = Color.White,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold
                        )
                                    Spacer(modifier = Modifier.height(3.dp))
                                    Text(
                                        text = connectedServerName,
                                        color = Color(0xFFA1A1AA),
                                        fontSize = 13.sp
                                    )

                                    Spacer(modifier = Modifier.height(14.dp))
                                    HorizontalDivider(color = Color(0xFF2C2C2E), thickness = 0.8.dp)

                                    // Row 1: Layout
                                    SettingsItemRow(
                                        icon = Icons.Default.GridView,
                                        label = "Layout",
                                        value = if (config.isCustom) "${config.customColumns} × ${config.customRows}" else "${config.preset.columns} × ${config.preset.rows}",
                                        onClick = { activeSubSheet = SubSheet.LAYOUT }
                                    )

                                    HorizontalDivider(color = Color(0xFF2C2C2E), thickness = 0.8.dp)

                                    // Row 2: Orientation
                                    SettingsItemRow(
                                        icon = Icons.Default.ScreenRotation,
                                        label = "Orientation",
                                        value = when (currentOrientation) {
                                            ScreenOrientationOption.PORTRAIT -> "Fixed Portrait"
                                            ScreenOrientationOption.LANDSCAPE -> "Fixed Landscape"
                                            ScreenOrientationOption.SENSOR -> "Auto Sensor"
                                        },
                                        onClick = { activeSubSheet = SubSheet.ORIENTATION }
                                    )

                                    HorizontalDivider(color = Color(0xFF2C2C2E), thickness = 0.8.dp)

                                    // Row 3: Appearance (Dark pill)
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Palette,
                                            contentDescription = "Appearance",
                                            tint = Color(0xFFA1A1AA),
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Spacer(modifier = Modifier.width(14.dp))
                                        Text(
                                            text = "Appearance",
                                            color = Color.White,
                                            fontSize = 15.sp,
                                            modifier = Modifier.weight(1f)
                                        )
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(Color(0xFF2C2C2E))
                                                .border(1.dp, Color(0xFF3F3F46), RoundedCornerShape(8.dp))
                                                .padding(horizontal = 12.dp, vertical = 6.dp)
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Text(
                                                    text = "Dark",
                                                    color = Color.White,
                                                    fontSize = 13.sp,
                                                    fontWeight = FontWeight.Medium
                                                )
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Icon(
                                                    imageVector = Icons.Default.KeyboardArrowDown,
                                                    contentDescription = null,
                                                    tint = Color(0xFFA1A1AA),
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            }
                                        }
                                    }

                                    HorizontalDivider(color = Color(0xFF2C2C2E), thickness = 0.8.dp)

                                    // Row 4: Faceplate (Themes / Backgrounds)
                                    SettingsItemRow(
                                        icon = Icons.Default.Image,
                                        label = "Faceplate",
                                        value = config.background.displayName,
                                        onClick = { activeSubSheet = SubSheet.FACEPLATE }
                                    )

                                    if (isConnected) {
                                        Spacer(modifier = Modifier.height(16.dp))
                                        Button(
                                            onClick = onDisconnect,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(44.dp),
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = Color(0xFF27272A),
                                                contentColor = Color.White
                                            ),
                                            shape = RoundedCornerShape(10.dp)
                                        ) {
                                            Text(
                                                text = "Disconnect",
                                                fontSize = 14.sp,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                        }
                                    }
                                }
                            }

                            // Section 2: App & Fokus Kontroler
                            Spacer(modifier = Modifier.height(26.dp))
                            Text(
                                text = "App & Fokus Kontroler",
                                color = Color(0xFFA1A1AA),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
                            )

                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(Color(0xFF1C1C1E))
                                    .border(1.dp, if (isMasterFocusActive) Color(0xFF5B7898) else Color(0xFF2C2C2E), RoundedCornerShape(16.dp))
                                    .padding(horizontal = 18.dp, vertical = 8.dp)
                            ) {
                                Column {
                                    // 1. MASTER SWITCH: Mode Fokus Kontroler
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = "Mode Fokus Kontroler",
                                                color = if (isMasterFocusActive) Color(0xFF6B86A4) else Color.White,
                                                fontSize = 15.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Text(
                                                text = "Sakelar utama: aktifkan seluruh optimasi kontroler sekaligus",
                                                color = Color(0xFFA1A1AA),
                                                fontSize = 11.sp
                                            )
                                        }
                                        Switch(
                                            checked = isMasterFocusActive,
                                            onCheckedChange = onToggleMasterFocus,
                                            colors = SwitchDefaults.colors(
                                                checkedThumbColor = Color.White,
                                                checkedTrackColor = Color(0xFF405870),
                                                uncheckedThumbColor = Color(0xFFA1A1AA),
                                                uncheckedTrackColor = Color(0xFF27272A)
                                            )
                                        )
                                    }

                                    HorizontalDivider(color = Color(0xFF2C2C2E), thickness = 0.8.dp)

                                    // 2. Sub-Item: Layar Selalu Menyala (Keep Screen On)
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = "Layar Selalu Menyala (Anti-Sleep)",
                                                color = Color.White,
                                                fontSize = 14.sp
                                            )
                                            Text(
                                                text = "Cegah layar HP meredup atau mati selama sesi aktif",
                                                color = Color(0xFFA1A1AA),
                                                fontSize = 11.sp
                                            )
                                        }
                                        Switch(
                                            checked = isKeepScreenOn,
                                            onCheckedChange = onToggleKeepScreenOn,
                                            colors = SwitchDefaults.colors(checkedThumbColor = Color(0xFF6B86A4))
                                        )
                                    }

                                    HorizontalDivider(color = Color(0xFF2C2C2E), thickness = 0.8.dp)

                                    // 3. Sub-Item: Layar Penuh Murni (Pure Fullscreen)
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = "Layar Penuh Murni (Edge-to-Edge)",
                                                color = Color.White,
                                                fontSize = 14.sp
                                            )
                                            Text(
                                                text = "Sembunyikan status bar dan isi kanvas tombol ke tepi atas",
                                                color = Color(0xFFA1A1AA),
                                                fontSize = 11.sp
                                            )
                                        }
                                        Switch(
                                            checked = isPureFullscreen,
                                            onCheckedChange = onTogglePureFullscreen,
                                            colors = SwitchDefaults.colors(checkedThumbColor = Color(0xFF6B86A4))
                                        )
                                    }

                                    HorizontalDivider(color = Color(0xFF2C2C2E), thickness = 0.8.dp)

                                    // 4. Sub-Item: Lock Task (Kunci Tirai Status Bar)
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = "Kunci Tirai Status Bar (Anti-Drop)",
                                                color = Color.White,
                                                fontSize = 14.sp
                                            )
                                            Text(
                                                text = "Cegah panel notifikasi melorot saat menekan tombol baris atas",
                                                color = Color(0xFFA1A1AA),
                                                fontSize = 11.sp
                                            )
                                        }
                                        Switch(
                                            checked = isLockTaskActive,
                                            onCheckedChange = onToggleLockTask,
                                            colors = SwitchDefaults.colors(checkedThumbColor = Color(0xFF6B86A4))
                                        )
                                    }

                                    HorizontalDivider(color = Color(0xFF2C2C2E), thickness = 0.8.dp)

                                    // 5. Sub-Item: DND (Blokir Pop-up Notifikasi)
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = "Blokir Pop-up Notifikasi (DND)",
                                                color = Color.White,
                                                fontSize = 14.sp
                                            )
                                            Text(
                                                text = "Bisukan chat / pesan mengambang saat sesi kontroler aktif",
                                                color = Color(0xFFA1A1AA),
                                                fontSize = 11.sp
                                            )
                                            if (!hasDndPermission) {
                                                TextButton(
                                                    onClick = onRequestDndPermission,
                                                    contentPadding = PaddingValues(0.dp)
                                                ) {
                                                    Text("Beri Izin Akses DND", color = Color(0xFF6B86A4), fontSize = 11.sp)
                                                }
                                            }
                                        }
                                        Switch(
                                            checked = isDndActive,
                                            onCheckedChange = onToggleDnd,
                                            enabled = hasDndPermission,
                                            colors = SwitchDefaults.colors(checkedThumbColor = Color(0xFF6B86A4))
                                        )
                                    }
                                }
                            }

                            // Footer App & Device Info
                            Spacer(modifier = Modifier.height(32.dp))
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = "Mash Deck Mobile 2.1.2 (117)",
                                    color = Color(0xFF71717A),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Spacer(modifier = Modifier.height(3.dp))
                                Text(
                                    text = "${Build.MODEL}, Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})",
                                    color = Color(0xFF52525B),
                                    fontSize = 11.sp
                                )
                            }

                            Spacer(modifier = Modifier.height(24.dp))
                        }

                        // Sub-Sheet Modal Dialogs
                        when (activeSubSheet) {
                            SubSheet.LAYOUT -> {
                                SubSheetDialog(
                                    title = "Pilih Layout Grid",
                                    onDismiss = { activeSubSheet = null }
                                ) {
                                    listOf(
                                        DeckPreset.MINI to "3 × 2 (Mini - 6 Tombol)",
                                        DeckPreset.CLASSIC to "5 × 3 (Classic - 15 Tombol)",
                                        DeckPreset.XL to "8 × 4 (XL - 32 Tombol)"
                                    ).forEach { (preset, label) ->
                                        val isSelected = !isCustom && config.preset == preset
                                        SelectionRow(
                                            label = label,
                                            isSelected = isSelected,
                                            onClick = {
                                                isCustom = false
                                                onConfigChange(config.copy(preset = preset, isCustom = false))
                                                activeSubSheet = null
                                            }
                                        )
                                    }

                                    Spacer(modifier = Modifier.height(8.dp))
                                    HorizontalDivider(color = Color(0xFF2C2C2E), thickness = 0.8.dp)
                                    Spacer(modifier = Modifier.height(8.dp))

                                    // Custom Grid Option
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = "Custom Grid Bebas",
                                            color = Color.White,
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        Switch(
                                            checked = isCustom,
                                            onCheckedChange = {
                                                isCustom = it
                                                onConfigChange(
                                                    config.copy(
                                                        isCustom = isCustom,
                                                        customColumns = cols.toInt(),
                                                        customRows = rows.toInt()
                                                    )
                                                )
                                            },
                                            colors = SwitchDefaults.colors(checkedThumbColor = Color(0xFF6B86A4))
                                        )
                                    }

                                    AnimatedVisibility(
                                        visible = isCustom,
                                        enter = expandVertically(),
                                        exit = shrinkVertically()
                                    ) {
                                        Column(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                                            Text(
                                                text = "Kolom: ${cols.toInt()}",
                                                color = Color(0xFFA1A1AA),
                                                fontSize = 12.sp
                                            )
                                            Slider(
                                                value = cols,
                                                onValueChange = {
                                                    cols = it
                                                    onConfigChange(config.copy(isCustom = true, customColumns = cols.toInt(), customRows = rows.toInt()))
                                                },
                                                valueRange = 1f..10f,
                                                steps = 8
                                            )

                                            Text(
                                                text = "Baris: ${rows.toInt()}",
                                                color = Color(0xFFA1A1AA),
                                                fontSize = 12.sp
                                            )
                                            Slider(
                                                value = rows,
                                                onValueChange = {
                                                    rows = it
                                                    onConfigChange(config.copy(isCustom = true, customColumns = cols.toInt(), customRows = rows.toInt()))
                                                },
                                                valueRange = 1f..10f,
                                                steps = 8
                                            )
                                        }
                                    }
                                }
                            }

                            SubSheet.ORIENTATION -> {
                                SubSheetDialog(
                                    title = "Pilih Orientasi Layar",
                                    onDismiss = { activeSubSheet = null }
                                ) {
                                    listOf(
                                        ScreenOrientationOption.PORTRAIT to "Fixed Portrait (Tegak)",
                                        ScreenOrientationOption.LANDSCAPE to "Fixed Landscape (Mendatar)",
                                        ScreenOrientationOption.SENSOR to "Auto Sensor (Rotasi Otomatis)"
                                    ).forEach { (orient, label) ->
                                        val isSelected = currentOrientation == orient
                                        SelectionRow(
                                            label = label,
                                            isSelected = isSelected,
                                            onClick = {
                                                onOrientationChange(orient)
                                                activeSubSheet = null
                                            }
                                        )
                                    }
                                }
                            }

                            SubSheet.FACEPLATE -> {
                                SubSheetDialog(
                                    title = "Pilih Faceplate (Tema Latar)",
                                    onDismiss = { activeSubSheet = null }
                                ) {
                                    listOf(
                                        DeckBackground.DEEP_INDIGO to "Deep Indigo Mesh (Neon Studio)",
                                        DeckBackground.CARBON_WEAVE to "Carbon Weave (Tactical Kevlar)",
                                        DeckBackground.AURORA_GLOW to "Aurora Glow (OLED Minimalist)",
                                        DeckBackground.BRUSHED_TITANIUM to "Brushed Titanium (Obsidian)",
                                        DeckBackground.SOLID_DARK to "Solid Obsidian (Klasik Polos)"
                                    ).forEach { (bg, label) ->
                                        val isSelected = config.background == bg
                                        SelectionRow(
                                            label = label,
                                            isSelected = isSelected,
                                            onClick = {
                                                onConfigChange(config.copy(background = bg))
                                                activeSubSheet = null
                                            }
                                        )
                                    }
                                }
                            }

                            SubSheet.PROFILE_SLOT -> {
                                var newSlotInput by remember { mutableStateOf("") }
                                SubSheetDialog(
                                    title = "Profil Perangkat Virtual",
                                    onDismiss = { activeSubSheet = null }
                                ) {
                                    Text(
                                        text = "Setiap profil dibaca sebagai Stream Deck independen di PC dengan susunan tombol tersendiri.",
                                        color = Color(0xFFA1A1AA),
                                        fontSize = 12.sp,
                                        modifier = Modifier.padding(bottom = 12.dp)
                                    )

                                    profileSlots.forEach { slotName ->
                                        val isSelected = slotName.equals(activeProfile, ignoreCase = true)
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(8.dp))
                                                .clickable {
                                                    onSelectProfile(slotName)
                                                    activeSubSheet = null
                                                }
                                                .padding(vertical = 10.dp, horizontal = 8.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                modifier = Modifier.weight(1f)
                                            ) {
                                                Icon(
                                                    imageVector = if (isSelected) Icons.Default.RadioButtonChecked else Icons.Default.RadioButtonUnchecked,
                                                    contentDescription = null,
                                                    tint = if (isSelected) Color(0xFF6B86A4) else Color(0xFF71717A),
                                                    modifier = Modifier.size(18.dp)
                                                )
                                                Spacer(modifier = Modifier.width(10.dp))
                                                Text(
                                                    text = slotName,
                                                    color = if (isSelected) Color(0xFF6B86A4) else Color.White,
                                                    fontSize = 14.sp,
                                                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                                                )
                                            }

                                            if (!isSelected && profileSlots.size > 1 && !slotName.equals("Mash Stream Deck", ignoreCase = true)) {
                                                IconButton(
                                                    onClick = { onRemoveProfileSlot(slotName) },
                                                    modifier = Modifier.size(28.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.Close,
                                                        contentDescription = "Hapus Slot",
                                                        tint = Color(0xFF71717A),
                                                        modifier = Modifier.size(16.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(14.dp))
                                    HorizontalDivider(color = Color(0xFF2C2C2E), thickness = 0.8.dp)
                                    Spacer(modifier = Modifier.height(14.dp))

                                    // Input Slot Baru
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        OutlinedTextField(
                                            value = newSlotInput,
                                            onValueChange = { newSlotInput = it },
                                            placeholder = {
                                                Text(
                                                    "Nama profil baru",
                                                    fontSize = 12.sp,
                                                    color = Color(0xFF71717A)
                                                )
                                            },
                                            singleLine = true,
                                            modifier = Modifier.weight(1f),
                                            colors = OutlinedTextFieldDefaults.colors(
                                                focusedBorderColor = Color(0xFF6B86A4),
                                                unfocusedBorderColor = Color(0xFF3F3F46),
                                                focusedTextColor = Color.White,
                                                unfocusedTextColor = Color.White
                                            )
                                        )

                                        Button(
                                            enabled = newSlotInput.trim().isNotEmpty(),
                                            onClick = {
                                                val trimmed = newSlotInput.trim()
                                                if (trimmed.isNotEmpty()) {
                                                    onAddProfileSlot(trimmed)
                                                    newSlotInput = ""
                                                    activeSubSheet = null
                                                }
                                            },
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = Color(0xFF5B7898),
                                                disabledContainerColor = Color(0xFF2C2C2E)
                                            ),
                                            shape = RoundedCornerShape(8.dp),
                                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp)
                                        ) {
                                            Text(
                                                text = "Tambah",
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                color = Color.White
                                            )
                                        }
                                    }
                                }
                            }

                            null -> {}
                        }
                    }
                }
            }


@Composable
private fun SettingsItemRow(
    icon: ImageVector,
    label: String,
    value: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = Color(0xFFA1A1AA),
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(14.dp))
        Text(
            text = label,
            color = Color.White,
            fontSize = 15.sp,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = value,
            color = Color(0xFF6B86A4), // Slate Blue value accent!
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium
        )
        Spacer(modifier = Modifier.width(6.dp))
        Icon(
            imageVector = Icons.Default.ChevronRight,
            contentDescription = null,
            tint = Color(0xFF71717A),
            modifier = Modifier.size(18.dp)
        )
    }
}

@Composable
private fun SubSheetDialog(
    title: String,
    onDismiss: () -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF1C1C1E),
        shape = RoundedCornerShape(16.dp),
        title = {
            Text(
                text = title,
                color = Color.White,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                content()
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Tutup", color = Color(0xFF6B86A4), fontWeight = FontWeight.SemiBold)
            }
        }
    )
}

@Composable
private fun SelectionRow(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp, horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            color = if (isSelected) Color(0xFF6B86A4) else Color.White,
            fontSize = 14.sp,
            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
        )
        if (isSelected) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = "Selected",
                tint = Color(0xFF6B86A4),
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

