package com.mash.deck.network

import android.graphics.Bitmap
import android.os.Build
import android.util.Log
import com.elgato.vsd2.Messages
import com.google.protobuf.ByteString
import com.mash.deck.codec.ImageDecoder
import com.mash.deck.crypto.CryptoBox
import com.mash.deck.model.DeckConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okhttp3.*
import okio.ByteString.Companion.toByteString
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

enum class ConnectionState {
    DISCONNECTED,
    CONNECTING,
    WAITING_FOR_PAIRING, // Fingerprint verification shown on PC
    CONNECTED,
    ERROR
}

class Vsd2Client(
    private val scope: CoroutineScope,
    val cryptoBox: CryptoBox
) {
    private val tag = "Vsd2Client"

    private val client = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS) // infinite for WebSocket
        .pingInterval(10, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    private var webSocket: WebSocket? = null

    private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val _fingerprint = MutableStateFlow<String?>(null)
    val fingerprint: StateFlow<String?> = _fingerprint.asStateFlow()

    private val _buttonBitmaps = MutableStateFlow<Map<Int, Bitmap>>(emptyMap())
    val buttonBitmaps: StateFlow<Map<Int, Bitmap>> = _buttonBitmaps.asStateFlow()

    private val _currentPage = MutableStateFlow(1)
    val currentPage: StateFlow<Int> = _currentPage.asStateFlow()

    private val _totalPages = MutableStateFlow(1)
    val totalPages: StateFlow<Int> = _totalPages.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    var targetDeviceName: String = "Mash Stream Deck"

    private val _discoveredDevices = MutableStateFlow<List<String>>(emptyList())
    val discoveredDevices: StateFlow<List<String>> = _discoveredDevices.asStateFlow()

    private var lastHost: String? = null
    val connectedHost: String? get() = lastHost
    private var lastPort: Int? = null
    private var lastConfig: DeckConfig? = null

    private var isAuthenticated = false
    private var isEncryptedSession = false
    private var activeDeviceID: String? = null
    private var isDeviceSetupDone = false

    private val activeActions = ConcurrentHashMap<Int, Messages.Action>()
    private val stashedBitmaps = ConcurrentHashMap<Int, Pair<Messages.Action?, Bitmap>>()
    private var refreshJob: Job? = null

    fun switchTargetDevice(newDeviceName: String, config: DeckConfig? = null) {
        val clean = newDeviceName.trim().ifEmpty { "Mash Stream Deck" }
        val changed = !targetDeviceName.equals(clean, ignoreCase = true)
        targetDeviceName = clean
        if (changed && _connectionState.value == ConnectionState.CONNECTED) {
            val h = lastHost
            val p = lastPort
            val c = config ?: lastConfig
            if (h != null && p != null && c != null) {
                disconnect()
                connect(h, p, c)
            }
        }
    }

    fun connect(host: String, port: Int, config: DeckConfig) {
        disconnect()
        _connectionState.value = ConnectionState.CONNECTING
        _errorMessage.value = null
        isAuthenticated = false
        isEncryptedSession = false
        isDeviceSetupDone = false

        // Defensive sanitization: extract pure IP/host even if full URL or scheme is passed
        val trimmed = host.trim()
        val withoutScheme = trimmed.replace(Regex("^[a-zA-Z0-9+.-]+://"), "")
        val cleanHost = withoutScheme.substringBefore(":").substringBefore("/")
        val portFromHost = withoutScheme.substringAfter(":", "").substringBefore("/").toIntOrNull()
        val targetPort = portFromHost ?: if (port in 1..65535) port else 28198

        lastHost = cleanHost
        lastPort = targetPort
        lastConfig = config

        val url = "ws://$cleanHost:$targetPort"
        Log.d(tag, "Connecting to Stream Deck WebSocket at $url")

        val request = Request.Builder().url(url).build()

        var currentWs: WebSocket? = null
        currentWs = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(ws: WebSocket, response: Response) {
                if (ws != webSocket) return
                Log.d(tag, "WebSocket handshake succeeded! Sending HelloFromClient...")
                sendHello()
            }

            override fun onMessage(ws: WebSocket, bytes: okio.ByteString) {
                if (ws != webSocket) return
                handleIncomingBytes(bytes.toByteArray(), config)
            }

            override fun onMessage(ws: WebSocket, text: String) {
                if (ws != webSocket) return
                Log.d(tag, "Incoming text message: $text")
            }

            override fun onFailure(ws: WebSocket, t: Throwable, response: Response?) {
                if (ws != webSocket) {
                    Log.d(tag, "Ignoring onFailure from stale WebSocket: ${t.message}")
                    return
                }
                val msg = t.localizedMessage ?: t.message ?: "Gagal terhubung ke host"
                Log.e(tag, "WebSocket connection failure: $msg", t)
                _connectionState.value = ConnectionState.ERROR
                _errorMessage.value = msg
            }

            override fun onClosed(ws: WebSocket, code: Int, reason: String) {
                if (ws != webSocket) {
                    Log.d(tag, "Ignoring onClosed from stale WebSocket")
                    return
                }
                Log.w(tag, "WebSocket closed by peer: code=$code, reason=$reason")
                _connectionState.value = ConnectionState.DISCONNECTED
                if (code != 1000) {
                    _errorMessage.value = "Koneksi terputus: $reason (code $code)"
                }
            }
        })
        webSocket = currentWs
    }

    private fun sendHello() {
        val clientInfo = Messages.ClientInformation.newBuilder()
            .setName("Mash Deck")
            .setUserAgent("Stream Deck Mobile/2.1.2")
            .setProtocolVersion(2)
            .setDeviceModel(Build.MODEL)
            .setOsName("Android")
            .setOsVersion(Build.VERSION.RELEASE)
            .build()

        val hello = Messages.HelloFromClient.newBuilder()
            .setPublicKey(ByteString.copyFrom(cryptoBox.publicKey))
            .setClientInformation(clientInfo)
            .build()

        val msg = Messages.ClientMessage.newBuilder()
            .setHello(hello)
            .build()

        val rawBytes = msg.toByteArray()
        Log.d(tag, "Sending unencrypted HelloFromClient (${rawBytes.size} bytes)")
        webSocket?.send(rawBytes.toByteString())
    }

    private fun handleIncomingBytes(rawBytes: ByteArray, config: DeckConfig) {
        val payloadBytes = if (isEncryptedSession) {
            try {
                cryptoBox.decrypt(rawBytes)
            } catch (e: Exception) {
                Log.e(tag, "Decryption error for ${rawBytes.size} bytes payload", e)
                return
            }
        } else {
            rawBytes
        }

        try {
            val serverMessage = Messages.ServerMessage.parseFrom(payloadBytes)
            Log.d(tag, "Received ServerMessage payload: ${serverMessage.payloadCase}")

            when (serverMessage.payloadCase) {
                Messages.ServerMessage.PayloadCase.HELLO -> {
                    val hello = serverMessage.hello
                    val serverPubKey = hello.publicKey.toByteArray()
                    Log.d(tag, "Received HelloFromServer: host=${hello.hostname}, needsAuth=${hello.clientNeedsAuthentication}")

                    // Compute shared key immediately
                    cryptoBox.calculateSharedKey(serverPubKey)
                    // All subsequent messages are encrypted!
                    isEncryptedSession = true

                    if (hello.clientNeedsAuthentication) {
                        _connectionState.value = ConnectionState.WAITING_FOR_PAIRING
                        val fp = cryptoBox.getFingerprint()
                        _fingerprint.value = fp
                        Log.d(tag, "Pairing required! Fingerprint code: $fp")

                        // Send Authenticate request ENCRYPTED
                        val authReq = Messages.Authenticate.newBuilder()
                            .setRequestID(1)
                            .build()
                        val authMsg = Messages.ClientMessage.newBuilder()
                            .setAuthenticate(authReq)
                            .build()
                        send(authMsg)
                    } else {
                        // Already trusted workstation
                        Log.d(tag, "Client is already trusted by workstation. Awaiting device list...")
                        isAuthenticated = true
                        _connectionState.value = ConnectionState.CONNECTED
                        scheduleDeviceFallback(config)
                    }
                }

                Messages.ServerMessage.PayloadCase.OK -> {
                    Log.d(tag, "Authentication OK received from PC! Awaiting device list...")
                    isAuthenticated = true
                    _connectionState.value = ConnectionState.CONNECTED
                    scheduleDeviceFallback(config)
                }

                Messages.ServerMessage.PayloadCase.VIRTUALDEVICELIST -> {
                    val list = serverMessage.virtualDeviceList
                    Log.d(tag, "Received VirtualDeviceList with ${list.devicesCount} devices: ${list.devicesList.map { "${it.name} (${it.id})" }}")

                    // Extract all names from PC to sync with Android UI
                    val pcNames = list.devicesList.mapNotNull { it.name?.trim()?.takeIf(String::isNotEmpty) }
                    _discoveredDevices.value = pcNames

                    if (!isDeviceSetupDone) {
                        isDeviceSetupDone = true
                        val desired = targetDeviceName.trim().ifEmpty { "Mash Stream Deck" }
                        val matchedDevice = list.devicesList.firstOrNull { it.name.equals(desired, ignoreCase = true) }
                            ?: list.devicesList.firstOrNull { it.id == activeDeviceID }

                        if (matchedDevice != null) {
                            Log.d(tag, "Found matching device on PC: ${matchedDevice.name} (id=${matchedDevice.id}). Reusing it...")
                            openExistingVirtualDevice(matchedDevice.id, config)
                        } else {
                            Log.d(tag, "Target device '$desired' not found on PC. Creating new virtual device...")
                            createSingleVirtualDevice(config, desired)
                        }
                    }
                }

                Messages.ServerMessage.PayloadCase.ERROR -> {
                    val err = serverMessage.error
                    val errorDesc = "Error ${err.code}: ${err.reason}"
                    Log.e(tag, "Server sent error: $errorDesc")
                    _errorMessage.value = errorDesc
                    _connectionState.value = ConnectionState.ERROR
                }

                Messages.ServerMessage.PayloadCase.VIRTUALDEVICEOPENED -> {
                    val dev = serverMessage.virtualDeviceOpened
                    activeDeviceID = dev.device.id
                    isDeviceSetupDone = true
                    Log.d(tag, "Virtual device opened successfully! Name=${dev.device.name}, ID=${activeDeviceID}")
                    _connectionState.value = ConnectionState.CONNECTED
                    // Synchronize the keypad configuration immediately
                    updateGridConfig(config)
                }

                Messages.ServerMessage.PayloadCase.ICON -> {
                    val icon = serverMessage.icon
                    val bitmap = ImageDecoder.decodeImage(icon.image)
                    if (bitmap != null) {
                        scope.launch(Dispatchers.Main) {
                            val map = _buttonBitmaps.value.toMutableMap()
                            map[icon.index] = bitmap
                            _buttonBitmaps.value = map
                        }
                    }
                }

                Messages.ServerMessage.PayloadCase.CONTEXT -> {
                    val ctx = serverMessage.context
                    val rawPage = ctx.currentPage
                    val tot = if (ctx.totalPages > 0) ctx.totalPages else 1
                    val cur = (rawPage + 1).coerceIn(1, tot)
                    Log.d(tag, "Received Context profile=${ctx.profileID}: page $cur of $tot (actions=${ctx.actionsCount})")
                    _currentPage.value = cur
                    _totalPages.value = tot

                    activeActions.clear()
                    stashedBitmaps.clear()

                    if (ctx.actionsCount > 0) {
                        for (act in ctx.actionsList) {
                            activeActions[act.index] = act
                        }
                        val activeIndices = ctx.actionsList.map { it.index }.toSet()
                        scope.launch(Dispatchers.Main) {
                            val map = _buttonBitmaps.value.toMutableMap()
                            map.keys.retainAll(activeIndices)
                            _buttonBitmaps.value = map
                        }
                    } else {
                        scope.launch(Dispatchers.Main) {
                            _buttonBitmaps.value = emptyMap()
                        }
                    }
                }

                Messages.ServerMessage.PayloadCase.CONTEXTUPDATE -> {
                    val update = serverMessage.contextUpdate
                    Log.d(tag, "Received ContextUpdate profile=${update.profileID}: removeCount=${update.removeCount}, replaceCount=${update.replaceCount}")

                    scope.launch(Dispatchers.Main) {
                        val map = _buttonBitmaps.value.toMutableMap()

                        // 1. Stash removed items to preserve bitmaps for move/swap
                        if (update.removeCount > 0) {
                            for (idx in update.removeList) {
                                val oldAction = activeActions.remove(idx)
                                val oldBitmap = map.remove(idx)
                                if (oldBitmap != null) {
                                    stashedBitmaps[idx] = Pair(oldAction, oldBitmap)
                                }
                            }
                        }

                        // 2. Transfer bitmap to new slot immediately (0 ms local update)
                        if (update.replaceCount > 0) {
                            for (action in update.replaceList) {
                                val newIdx = action.index
                                activeActions[newIdx] = action

                                if (!map.containsKey(newIdx) && stashedBitmaps.isNotEmpty()) {
                                    // Match by title & name if available
                                    val matchedKey = stashedBitmaps.entries.firstOrNull { (_, entry) ->
                                        val (stashedAction, _) = entry
                                        if (stashedAction != null) {
                                            if (action.title.isNotEmpty() && action.title == stashedAction.title) true
                                            else if (action.name.isNotEmpty() && action.name == stashedAction.name && action.title.isEmpty() && stashedAction.title.isEmpty()) true
                                            else false
                                        } else false
                                    }?.key ?: if (stashedBitmaps.size == 1) {
                                        stashedBitmaps.keys.first()
                                    } else {
                                        stashedBitmaps.keys.firstOrNull { it != newIdx } ?: stashedBitmaps.keys.firstOrNull()
                                    }

                                    if (matchedKey != null) {
                                        stashedBitmaps.remove(matchedKey)?.let { (_, bitmap) ->
                                            map[newIdx] = bitmap
                                        }
                                    }
                                }
                            }
                        }

                        _buttonBitmaps.value = map

                        // 3. Request authoritative background refresh from PC (debounced 250ms)
                        scheduleKeypadRefresh()
                    }
                }

                else -> {
                    Log.d(tag, "Unhandled ServerMessage payload: ${serverMessage.payloadCase}")
                }
            }
        } catch (e: Exception) {
            Log.e(tag, "Failed to parse ServerMessage from ${payloadBytes.size} bytes", e)
        }
    }

    private fun scheduleDeviceFallback(config: DeckConfig) {
        scope.launch {
            kotlinx.coroutines.delay(1200)
            if (!isDeviceSetupDone && isAuthenticated) {
                Log.d(tag, "VirtualDeviceList timeout fallback: Opening or creating '$targetDeviceName'...")
                isDeviceSetupDone = true
                if (activeDeviceID != null) {
                    openExistingVirtualDevice(activeDeviceID!!, config)
                } else {
                    createSingleVirtualDevice(config, targetDeviceName)
                }
            }
        }
    }

    private fun buildKeypadConfig(config: DeckConfig): Messages.KeypadConfig {
        return Messages.KeypadConfig.newBuilder()
            .addSupportedIconImageFormats(Messages.ImageFormat.LZF)
            .addSupportedIconImageFormats(Messages.ImageFormat.LZ4)
            .addSupportedIconImageFormats(Messages.ImageFormat.PNG)
            .addSupportedIconImageFormats(Messages.ImageFormat.JPG)
            .setIconSize(Messages.Size.newBuilder().setWidth(144).setHeight(144).build())
            .setHighDPI(true)
            .setOverscale(true)
            .setEnableContextAPI(true)
            .setLayoutCrop(
                Messages.Size.newBuilder()
                    .setWidth(config.activeColumns)
                    .setHeight(config.activeRows)
                    .build()
            )
            .build()
    }

    private fun openExistingVirtualDevice(deviceId: String, config: DeckConfig) {
        activeDeviceID = deviceId
        val keypadConfig = buildKeypadConfig(config)
        val openDevice = Messages.OpenVirtualDevice.newBuilder()
            .setRequestID(10)
            .setDeviceID(deviceId)
            .setKeypadConfig(keypadConfig)
            .build()

        val msg = Messages.ClientMessage.newBuilder()
            .setOpenVirtualDevice(openDevice)
            .build()

        Log.d(tag, "Sending OpenVirtualDevice for existing ID=$deviceId (${config.activeColumns}x${config.activeRows})")
        send(msg)
    }

    private fun createSingleVirtualDevice(config: DeckConfig, name: String = targetDeviceName) {
        val keypadConfig = buildKeypadConfig(config)
        val devName = name.trim().ifEmpty { "Mash Stream Deck" }
        val createDevice = Messages.CreateVirtualDevice.newBuilder()
            .setRequestID(10)
            .setName(devName)
            .setKeypadConfig(keypadConfig)
            .setSkipUniquenessOfNameValidation(true)
            .build()

        val msg = Messages.ClientMessage.newBuilder()
            .setCreateVirtualDevice(createDevice)
            .build()

        Log.d(tag, "Sending CreateVirtualDevice for '$devName'")
        send(msg)
    }

    fun updateGridConfig(config: DeckConfig) {
        if (!isAuthenticated) return
        val update = Messages.UpdateKeypadConfig.newBuilder()
            .setLayoutCrop(
                Messages.Size.newBuilder()
                    .setWidth(config.activeColumns)
                    .setHeight(config.activeRows)
                    .build()
            )
            .setIconSize(Messages.Size.newBuilder().setWidth(144).setHeight(144).build())
            .setHighDPI(true)
            .build()

        val msg = Messages.ClientMessage.newBuilder()
            .setUpdateKeypadConfig(update)
            .build()

        send(msg)
    }

    fun sendKeyPress(index: Int, pressed: Boolean) {
        val keyPress = Messages.KeyPress.newBuilder()
            .setIndex(index)
            .setPressed(pressed)
            .build()

        val msg = Messages.ClientMessage.newBuilder()
            .setKeyPress(keyPress)
            .build()

        send(msg)
    }

    fun nextPage() {
        Log.d(tag, "Sending NextPage request to workstation")
        val msg = Messages.ClientMessage.newBuilder()
            .setNextPage(Messages.NextPage.getDefaultInstance())
            .build()
        send(msg)
    }

    fun previousPage() {
        Log.d(tag, "Sending PreviousPage request to workstation")
        val msg = Messages.ClientMessage.newBuilder()
            .setPreviousPage(Messages.PreviousPage.getDefaultInstance())
            .build()
        send(msg)
    }

    private fun send(msg: Messages.ClientMessage) {
        val bytes = msg.toByteArray()
        if (isEncryptedSession) {
            val encrypted = cryptoBox.encrypt(bytes)
            webSocket?.send(encrypted.toByteString())
        } else {
            webSocket?.send(bytes.toByteString())
        }
    }

    private fun scheduleKeypadRefresh() {
        refreshJob?.cancel()
        refreshJob = scope.launch(Dispatchers.Default) {
            delay(250)
            val cfg = lastConfig
            if (cfg != null && isAuthenticated) {
                Log.d(tag, "Triggering background keypad refresh to sync with PC")
                updateGridConfig(cfg)
            }
            delay(1250)
            stashedBitmaps.clear()
        }
    }

    fun disconnect() {
        refreshJob?.cancel()
        refreshJob = null
        activeActions.clear()
        stashedBitmaps.clear()
        val oldWs = webSocket
        webSocket = null
        try {
            oldWs?.cancel()
            oldWs?.close(1000, "User disconnected")
        } catch (_: Exception) {}
        _connectionState.value = ConnectionState.DISCONNECTED
        _buttonBitmaps.value = emptyMap()
        _fingerprint.value = null
        _currentPage.value = 1
        _totalPages.value = 1
        isAuthenticated = false
        isEncryptedSession = false
        isDeviceSetupDone = false
    }
}
