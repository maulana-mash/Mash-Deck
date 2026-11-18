package com.mash.deck

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Base64
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.mash.deck.crypto.CryptoBox
import com.mash.deck.discovery.DiscoveredServer
import com.mash.deck.discovery.MdnsDiscovery
import com.mash.deck.discovery.SavedServer
import com.mash.deck.model.DeckConfig
import com.mash.deck.model.DeckPreset
import com.mash.deck.network.ConnectionState
import com.mash.deck.network.Vsd2Client
import com.mash.deck.sensor.GyroLaserManager
import com.mash.deck.ui.screens.*
import com.mash.deck.ui.theme.BackgroundDark
import com.mash.deck.ui.theme.MashDeckTheme
import com.mash.deck.util.*

enum class AppScreen {
    SPLASH,
    DISCOVERY,
    DECK,
    QR_SCAN
}

class MainActivity : ComponentActivity() {
    private lateinit var mdnsDiscovery: MdnsDiscovery
    private lateinit var vsdClient: Vsd2Client
    private lateinit var gyroLaserManager: GyroLaserManager

    private var currentScreen by mutableStateOf(AppScreen.SPLASH)
    private var connectedServerName by mutableStateOf("MASH-OLED23")
    private var connectingHost by mutableStateOf<String?>(null)
    private var savedServer by mutableStateOf<SavedServer?>(null)
    private var isAutoConnectEnabled by mutableStateOf(true)

    private var currentDeckConfig by mutableStateOf(DeckConfig())
    private var currentOrientation by mutableStateOf(ScreenOrientationOption.PORTRAIT)
    private var isMasterFocusActive by mutableStateOf(false)
    private var isKeepScreenOn by mutableStateOf(false)
    private var isPureFullscreen by mutableStateOf(false)
    private var isLockTaskActive by mutableStateOf(false)
    private var isDndActive by mutableStateOf(false)
    private var showSettings by mutableStateOf(false)
    private var activeProfileName by mutableStateOf(ProfileSlotPrefs.DEFAULT_DEVICE_NAME)
    private var profileSlots by mutableStateOf<List<String>>(listOf(ProfileSlotPrefs.DEFAULT_DEVICE_NAME))

    private fun applyKeepScreenOn(enabled: Boolean) {
        if (enabled) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    private val cameraPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            currentScreen = AppScreen.QR_SCAN
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Load saved preferences
        loadFocusPreferences()
        currentOrientation = getSavedOrientation()
        activeProfileName = ProfileSlotPrefs.getActiveDevice(this)
        profileSlots = ProfileSlotPrefs.getSlots(this)
        currentDeckConfig = ProfileSlotPrefs.getProfileConfig(this, activeProfileName)
        savedServer = loadSavedServer()
        isAutoConnectEnabled = loadSavedAutoConnect()

        // Landing / Discovery page always starts in Portrait
        OrientationHelper.applyOrientation(this, ScreenOrientationOption.PORTRAIT)

        val cryptoBox = getOrCreateCryptoBox()
        mdnsDiscovery = MdnsDiscovery(this, lifecycleScope)
        vsdClient = Vsd2Client(lifecycleScope, cryptoBox)
        vsdClient.targetDeviceName = activeProfileName
        gyroLaserManager = GyroLaserManager(this, lifecycleScope)

        // Start dual discovery: mDNS + active TCP probe to saved server
        mdnsDiscovery.startDiscovery(
            savedHost = savedServer?.host,
            savedPort = savedServer?.port ?: 28198,
            savedName = savedServer?.name ?: "MASH-OLED23"
        )

        setContent {
            MashDeckTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = BackgroundDark
                ) {
                    val servers by mdnsDiscovery.servers.collectAsState()
                    val connectionState by vsdClient.connectionState.collectAsState()
                    val fingerprint by vsdClient.fingerprint.collectAsState()
                    val buttonBitmaps by vsdClient.buttonBitmaps.collectAsState()
                    val currentPage by vsdClient.currentPage.collectAsState()
                    val totalPages by vsdClient.totalPages.collectAsState()
                    val errorMessage by vsdClient.errorMessage.collectAsState()
                    val pcDiscoveredDevices by vsdClient.discoveredDevices.collectAsState()

                    LaunchedEffect(pcDiscoveredDevices) {
                        if (pcDiscoveredDevices.isNotEmpty()) {
                            profileSlots = ProfileSlotPrefs.addOrMergeSlots(this@MainActivity, pcDiscoveredDevices)
                        }
                    }

                    // Navigate automatically to DeckScreen once connected
                    LaunchedEffect(connectionState) {
                        if (connectionState == ConnectionState.CONNECTED) {
                            currentScreen = AppScreen.DECK
                            connectingHost = null
                            // Save active server details on successful handshake
                            savedServer?.let {
                                saveConnectedServer(it.host, it.port, connectedServerName)
                            }

                            // Apply user's selected orientation (Portrait by default)
                            OrientationHelper.applyOrientation(this@MainActivity, currentOrientation)
                            if (isPureFullscreen) {
                                ImmersiveModeHelper.enableImmersiveMode(this@MainActivity)
                            }
                            if (isKeepScreenOn) {
                                applyKeepScreenOn(true)
                            }
                            if (isLockTaskActive) {
                                ImmersiveModeHelper.startLockTask(this@MainActivity)
                            }
                            if (isDndActive) {
                                DndHelper.setDndEnabled(this@MainActivity, true)
                            }
                        } else if (connectionState == ConnectionState.DISCONNECTED || connectionState == ConnectionState.ERROR) {
                            connectingHost = null
                            if (currentScreen == AppScreen.DECK) {
                                currentScreen = AppScreen.DISCOVERY
                                applyKeepScreenOn(false)
                                if (isLockTaskActive) {
                                    ImmersiveModeHelper.stopLockTask(this@MainActivity)
                                }
                                if (isDndActive) {
                                    DndHelper.setDndEnabled(this@MainActivity, false)
                                }
                                // Revert to Portrait on landing page
                                OrientationHelper.applyOrientation(this@MainActivity, ScreenOrientationOption.PORTRAIT)
                            }
                        }
                    }

                    when (currentScreen) {
                        AppScreen.SPLASH -> {
                            SplashScreen(
                                background = currentDeckConfig.background,
                                onFinished = {
                                    currentScreen = AppScreen.DISCOVERY
                                    // Auto-connect to last PC immediately if enabled!
                                    if (isAutoConnectEnabled && savedServer != null) {
                                        val s = savedServer!!
                                        connectedServerName = s.name
                                        connectingHost = s.host
                                        vsdClient.connect(s.host, s.port, currentDeckConfig)
                                    }
                                }
                            )
                        }

                        AppScreen.DISCOVERY -> {
                            DiscoveryScreen(
                                discoveredServers = servers,
                                savedServer = savedServer,
                                connectionState = connectionState,
                                errorMessage = errorMessage,
                                connectingHost = connectingHost,
                                isAutoConnectEnabled = isAutoConnectEnabled,
                                onToggleAutoConnect = { enabled ->
                                    isAutoConnectEnabled = enabled
                                    saveAutoConnectEnabled(enabled)
                                },
                                onSelectServer = { host, port, name ->
                                    connectedServerName = name
                                    connectingHost = host
                                    saveConnectedServer(host, port, name)
                                    savedServer = SavedServer(name, host, port)
                                    vsdClient.connect(host, port, currentDeckConfig)
                                },
                                onRefresh = {
                                    mdnsDiscovery.startDiscovery(
                                        savedHost = savedServer?.host,
                                        savedPort = savedServer?.port ?: 28198,
                                        savedName = savedServer?.name ?: "MASH-OLED23"
                                    )
                                },
                                onOpenQrScanner = {
                                    val hasCamera = ContextCompat.checkSelfPermission(
                                        this@MainActivity,
                                        Manifest.permission.CAMERA
                                    ) == PackageManager.PERMISSION_GRANTED
                                    if (hasCamera) {
                                        currentScreen = AppScreen.QR_SCAN
                                    } else {
                                        cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                                    }
                                },
                                onOpenSettings = {
                                    showSettings = true
                                },
                                activeProfileName = activeProfileName
                            )
                        }

                        AppScreen.DECK -> {
                            DeckScreen(
                                serverName = connectedServerName,
                                connectionState = connectionState,
                                config = currentDeckConfig,
                                buttonBitmaps = buttonBitmaps,
                                currentPage = currentPage,
                                totalPages = totalPages,
                                isPureFullscreen = isPureFullscreen,
                                onKeyPress = { index, pressed ->
                                    vsdClient.sendKeyPress(index, pressed)
                                    if (pressed) {
                                        val host = vsdClient.connectedHost ?: savedServer?.host ?: connectingHost ?: ""
                                        gyroLaserManager.startTracking(host)
                                    } else {
                                        gyroLaserManager.stopTracking()
                                    }
                                },
                                onNextPage = { vsdClient.nextPage() },
                                onPrevPage = { vsdClient.previousPage() },
                                onOpenSettings = { showSettings = true },
                                onToggleFullscreen = {
                                    ImmersiveModeHelper.enableImmersiveMode(this@MainActivity)
                                },
                                onDisconnect = {
                                    vsdClient.disconnect()
                                    currentScreen = AppScreen.DISCOVERY
                                    applyKeepScreenOn(false)
                                    if (isLockTaskActive) ImmersiveModeHelper.stopLockTask(this@MainActivity)
                                    if (isDndActive) DndHelper.setDndEnabled(this@MainActivity, false)
                                    OrientationHelper.applyOrientation(this@MainActivity, ScreenOrientationOption.PORTRAIT)
                                }
                            )
                        }

                        AppScreen.QR_SCAN -> {
                            QrScanScreen(
                                onQrCodeDetected = { host, port ->
                                    currentScreen = AppScreen.DISCOVERY
                                    val serverName = if (host == "10.214.158.2") "MASH-OLED23" else "Stream Deck PC"
                                    connectedServerName = serverName
                                    connectingHost = host
                                    saveConnectedServer(host, port, serverName)
                                    savedServer = SavedServer(serverName, host, port)
                                    vsdClient.connect(host, port, currentDeckConfig)
                                },
                                onBack = {
                                    currentScreen = AppScreen.DISCOVERY
                                }
                            )
                        }
                    }

                    // Pairing Verification Dialog (BLAKE2b 6-digit hex code)
                    if (connectionState == ConnectionState.WAITING_FOR_PAIRING && fingerprint != null) {
                        PairingDialog(
                            fingerprint = fingerprint!!,
                            onCancel = {
                                vsdClient.disconnect()
                            }
                        )
                    }

                    // Settings Dialog
                    if (showSettings) {
                        val hasDnd = DndHelper.isDndAccessGranted(this@MainActivity)
                        SettingsDialog(
                            config = currentDeckConfig,
                            currentOrientation = currentOrientation,
                            connectedServerName = if (connectionState == ConnectionState.CONNECTED) connectedServerName else "Belum Terhubung",
                            isConnected = connectionState == ConnectionState.CONNECTED,
                            isMasterFocusActive = isMasterFocusActive,
                            isKeepScreenOn = isKeepScreenOn,
                            isPureFullscreen = isPureFullscreen,
                            isLockTaskActive = isLockTaskActive,
                            isDndActive = isDndActive,
                            hasDndPermission = hasDnd,
                            profileSlots = profileSlots,
                            activeProfile = activeProfileName,
                            onSelectProfile = { selected ->
                                ProfileSlotPrefs.setActiveDevice(this@MainActivity, selected)
                                activeProfileName = selected
                                profileSlots = ProfileSlotPrefs.getSlots(this@MainActivity)
                                val slotConfig = ProfileSlotPrefs.getProfileConfig(this@MainActivity, selected)
                                currentDeckConfig = slotConfig
                                vsdClient.switchTargetDevice(selected, slotConfig)
                            },
                            onAddProfileSlot = { newSlot ->
                                val clean = newSlot.trim()
                                if (clean.isNotEmpty()) {
                                    ProfileSlotPrefs.setActiveDevice(this@MainActivity, clean)
                                    profileSlots = ProfileSlotPrefs.addOrMergeSlots(this@MainActivity, listOf(clean))
                                    activeProfileName = clean
                                    val slotConfig = ProfileSlotPrefs.getProfileConfig(this@MainActivity, clean)
                                    currentDeckConfig = slotConfig
                                    vsdClient.switchTargetDevice(clean, slotConfig)
                                }
                            },
                            onRemoveProfileSlot = { removed ->
                                profileSlots = ProfileSlotPrefs.removeSlot(this@MainActivity, removed)
                                activeProfileName = ProfileSlotPrefs.getActiveDevice(this@MainActivity)
                                val slotConfig = ProfileSlotPrefs.getProfileConfig(this@MainActivity, activeProfileName)
                                currentDeckConfig = slotConfig
                                vsdClient.switchTargetDevice(activeProfileName, slotConfig)
                            },
                            onConfigChange = { newConfig ->
                                currentDeckConfig = newConfig
                                ProfileSlotPrefs.saveProfileConfig(this@MainActivity, activeProfileName, newConfig)
                                saveDeckConfig(newConfig)
                                vsdClient.updateGridConfig(newConfig)
                            },
                            onOrientationChange = { newOrient ->
                                currentOrientation = newOrient
                                saveOrientation(newOrient)
                                // If currently on deck screen, apply orientation immediately
                                if (currentScreen == AppScreen.DECK) {
                                    OrientationHelper.applyOrientation(this@MainActivity, newOrient)
                                }
                            },
                            onToggleMasterFocus = { enable ->
                                isMasterFocusActive = enable
                                isKeepScreenOn = enable
                                isPureFullscreen = enable
                                isLockTaskActive = enable
                                isDndActive = enable
                                saveFocusPreferences()
                                if (currentScreen == AppScreen.DECK) {
                                    applyKeepScreenOn(enable)
                                    if (enable) {
                                        ImmersiveModeHelper.enableImmersiveMode(this@MainActivity)
                                        ImmersiveModeHelper.startLockTask(this@MainActivity)
                                        DndHelper.setDndEnabled(this@MainActivity, true)
                                    } else {
                                        ImmersiveModeHelper.stopLockTask(this@MainActivity)
                                        DndHelper.setDndEnabled(this@MainActivity, false)
                                    }
                                }
                            },
                            onToggleKeepScreenOn = { enable ->
                                isKeepScreenOn = enable
                                if (!enable) isMasterFocusActive = false
                                else if (isPureFullscreen && isLockTaskActive && isDndActive) isMasterFocusActive = true
                                saveFocusPreferences()
                                if (currentScreen == AppScreen.DECK) {
                                    applyKeepScreenOn(enable)
                                }
                            },
                            onTogglePureFullscreen = { enable ->
                                isPureFullscreen = enable
                                if (!enable) isMasterFocusActive = false
                                else if (isKeepScreenOn && isLockTaskActive && isDndActive) isMasterFocusActive = true
                                saveFocusPreferences()
                                if (currentScreen == AppScreen.DECK) {
                                    if (enable) ImmersiveModeHelper.enableImmersiveMode(this@MainActivity)
                                    else ImmersiveModeHelper.disableImmersiveMode(this@MainActivity)
                                }
                            },
                            onToggleLockTask = { enable ->
                                if (enable) {
                                    val success = ImmersiveModeHelper.startLockTask(this@MainActivity)
                                    isLockTaskActive = success
                                } else {
                                    val success = ImmersiveModeHelper.stopLockTask(this@MainActivity)
                                    isLockTaskActive = !success
                                }
                                if (!isLockTaskActive) isMasterFocusActive = false
                                else if (isKeepScreenOn && isPureFullscreen && isDndActive) isMasterFocusActive = true
                                saveFocusPreferences()
                            },
                            onToggleDnd = { enable ->
                                isDndActive = enable
                                if (!enable) isMasterFocusActive = false
                                else if (isKeepScreenOn && isPureFullscreen && isLockTaskActive) isMasterFocusActive = true
                                saveFocusPreferences()
                                if (currentScreen == AppScreen.DECK) {
                                    DndHelper.setDndEnabled(this@MainActivity, enable)
                                }
                            },
                            onRequestDndPermission = {
                                DndHelper.openDndSettings(this@MainActivity)
                            },
                            onDisconnect = {
                                showSettings = false
                                vsdClient.disconnect()
                                currentScreen = AppScreen.DISCOVERY
                                applyKeepScreenOn(false)
                                if (isLockTaskActive) ImmersiveModeHelper.stopLockTask(this@MainActivity)
                                if (isDndActive) DndHelper.setDndEnabled(this@MainActivity, false)
                                OrientationHelper.applyOrientation(this@MainActivity, ScreenOrientationOption.PORTRAIT)
                            },
                            onDismiss = { showSettings = false }
                        )
                    }
                }
            }
        }
    }

    private fun loadSavedServer(): SavedServer {
        val prefs = getSharedPreferences("mash_deck_prefs", Context.MODE_PRIVATE)
        val host = prefs.getString("last_server_host", null) ?: "10.214.158.2"
        val port = prefs.getInt("last_server_port", 28198)
        val name = prefs.getString("last_server_name", null) ?: "MASH-OLED23"
        return SavedServer(name, host, port)
    }

    private fun saveConnectedServer(host: String, port: Int, name: String) {
        val prefs = getSharedPreferences("mash_deck_prefs", Context.MODE_PRIVATE)
        prefs.edit()
            .putString("last_server_host", host)
            .putInt("last_server_port", port)
            .putString("last_server_name", name)
            .putLong("last_server_time", System.currentTimeMillis())
            .apply()
    }

    private fun loadSavedAutoConnect(): Boolean {
        val prefs = getSharedPreferences("mash_deck_prefs", Context.MODE_PRIVATE)
        return prefs.getBoolean("auto_connect_enabled", true)
    }

    private fun saveAutoConnectEnabled(enabled: Boolean) {
        val prefs = getSharedPreferences("mash_deck_prefs", Context.MODE_PRIVATE)
        prefs.edit().putBoolean("auto_connect_enabled", enabled).apply()
    }

    private fun getSavedOrientation(): ScreenOrientationOption {
        val prefs = getSharedPreferences("mash_deck_prefs", Context.MODE_PRIVATE)
        val name = prefs.getString("screen_orientation_v2", null)
        return when (name) {
            ScreenOrientationOption.LANDSCAPE.name -> ScreenOrientationOption.LANDSCAPE
            ScreenOrientationOption.SENSOR.name -> ScreenOrientationOption.SENSOR
            else -> ScreenOrientationOption.PORTRAIT
        }
    }

    private fun saveOrientation(option: ScreenOrientationOption) {
        val prefs = getSharedPreferences("mash_deck_prefs", Context.MODE_PRIVATE)
        prefs.edit()
            .putString("screen_orientation_v2", option.name)
            .putString("screen_orientation", option.name)
            .apply()
    }

    private fun getSavedDeckConfig(): DeckConfig {
        val prefs = getSharedPreferences("mash_deck_prefs", Context.MODE_PRIVATE)
        val presetName = prefs.getString("deck_preset", DeckPreset.CLASSIC.name)
        val preset = try {
            DeckPreset.valueOf(presetName ?: DeckPreset.CLASSIC.name)
        } catch (_: Exception) {
            DeckPreset.CLASSIC
        }
        val isCustom = prefs.getBoolean("is_custom", false)
        val customCols = prefs.getInt("custom_cols", 5)
        val customRows = prefs.getInt("custom_rows", 3)
        val bgName = prefs.getString("deck_background", com.mash.deck.model.DeckBackground.DEEP_INDIGO.name)
        val background = try {
            com.mash.deck.model.DeckBackground.valueOf(bgName ?: com.mash.deck.model.DeckBackground.DEEP_INDIGO.name)
        } catch (_: Exception) {
            com.mash.deck.model.DeckBackground.DEEP_INDIGO
        }
        return DeckConfig(preset = preset, isCustom = isCustom, customColumns = customCols, customRows = customRows, background = background)
    }

    private fun saveDeckConfig(config: DeckConfig) {
        val prefs = getSharedPreferences("mash_deck_prefs", Context.MODE_PRIVATE)
        prefs.edit()
            .putString("deck_preset", config.preset.name)
            .putBoolean("is_custom", config.isCustom)
            .putInt("custom_cols", config.customColumns)
            .putInt("custom_rows", config.customRows)
            .putString("deck_background", config.background.name)
            .apply()
    }

    private fun loadFocusPreferences() {
        val prefs = getSharedPreferences("mash_deck_prefs", Context.MODE_PRIVATE)
        isMasterFocusActive = prefs.getBoolean("master_focus_active", false)
        isKeepScreenOn = prefs.getBoolean("keep_screen_on", false)
        isPureFullscreen = prefs.getBoolean("pure_fullscreen", false)
        isLockTaskActive = prefs.getBoolean("lock_task_active", false)
        isDndActive = prefs.getBoolean("dnd_active", false)
    }

    private fun saveFocusPreferences() {
        val prefs = getSharedPreferences("mash_deck_prefs", Context.MODE_PRIVATE)
        prefs.edit()
            .putBoolean("master_focus_active", isMasterFocusActive)
            .putBoolean("keep_screen_on", isKeepScreenOn)
            .putBoolean("pure_fullscreen", isPureFullscreen)
            .putBoolean("lock_task_active", isLockTaskActive)
            .putBoolean("dnd_active", isDndActive)
            .apply()
    }

    private fun getOrCreateCryptoBox(): CryptoBox {
        val prefs = getSharedPreferences("mash_deck_crypto", Context.MODE_PRIVATE)
        val privBase64 = prefs.getString("private_key", null)
        val pubBase64 = prefs.getString("public_key", null)

        if (privBase64 != null && pubBase64 != null) {
            try {
                val priv = Base64.decode(privBase64, Base64.NO_WRAP)
                val pub = Base64.decode(pubBase64, Base64.NO_WRAP)
                if (priv.size == 32 && pub.size == 32) {
                    return CryptoBox(priv, pub)
                }
            } catch (_: Exception) {
                // fall through to generate new key
            }
        }

        val newBox = CryptoBox.generateKeyPair()
        prefs.edit()
            .putString("private_key", Base64.encodeToString(newBox.privateKey, Base64.NO_WRAP))
            .putString("public_key", Base64.encodeToString(newBox.publicKey, Base64.NO_WRAP))
            .apply()
        return newBox
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus && currentScreen == AppScreen.DECK) {
            if (isPureFullscreen) {
                ImmersiveModeHelper.enableImmersiveMode(this)
            }
            if (isKeepScreenOn) {
                applyKeepScreenOn(true)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        gyroLaserManager.cleanup()
        mdnsDiscovery.stopDiscovery()
        vsdClient.disconnect()
        applyKeepScreenOn(false)
        if (isDndActive) {
            DndHelper.setDndEnabled(this, false)
        }
        if (isLockTaskActive) {
            ImmersiveModeHelper.stopLockTask(this)
        }
    }
}
