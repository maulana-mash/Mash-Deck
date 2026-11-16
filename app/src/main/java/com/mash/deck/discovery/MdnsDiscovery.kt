package com.mash.deck.discovery

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.net.InetSocketAddress
import java.net.Socket

class MdnsDiscovery(
    private val context: Context,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
) {
    private val tag = "MdnsDiscovery"
    private val nsdManager = context.getSystemService(Context.NSD_SERVICE) as? NsdManager
    private val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
    private var multicastLock: WifiManager.MulticastLock? = null

    private val _servers = MutableStateFlow<List<DiscoveredServer>>(emptyList())
    val servers: StateFlow<List<DiscoveredServer>> = _servers.asStateFlow()

    private var discoveryListener: NsdManager.DiscoveryListener? = null
    private val serviceType = "_vstreamdeck2._tcp."

    fun startDiscovery(
        savedHost: String? = null,
        savedPort: Int = 28198,
        savedName: String = "MASH-OLED23"
    ) {
        stopDiscovery()

        // 1. Immediately probe known/saved host via direct unicast TCP probe
        // This solves cross-subnet Wi-Fi networks where mDNS multicast (UDP 5353) is blocked by routers
        savedHost?.let { host ->
            if (host.isNotBlank()) {
                probeDirectServer(host, savedPort, savedName)
            }
        }

        // Also probe common fallback if different
        if (savedHost != "10.214.158.2") {
            probeDirectServer("10.214.158.2", 28198, "MASH-OLED23")
        }

        // 2. Start mDNS multicast listener
        try {
            multicastLock = wifiManager?.createMulticastLock("MashDeckMdns")?.apply {
                setReferenceCounted(true)
                acquire()
            }
        } catch (e: Exception) {
            Log.w(tag, "Failed to acquire multicast lock: ${e.message}")
        }

        val listener = object : NsdManager.DiscoveryListener {
            override fun onStartDiscoveryFailed(serviceType: String?, errorCode: Int) {
                Log.w(tag, "Start discovery failed: $errorCode")
                stopDiscovery()
            }

            override fun onStopDiscoveryFailed(serviceType: String?, errorCode: Int) {
                nsdManager?.stopServiceDiscovery(this)
            }

            override fun onDiscoveryStarted(serviceType: String?) {
                Log.d(tag, "mDNS service discovery started for $serviceType")
            }

            override fun onDiscoveryStopped(serviceType: String?) {
                Log.d(tag, "mDNS service discovery stopped")
            }

            override fun onServiceFound(serviceInfo: NsdServiceInfo?) {
                if (serviceInfo == null) return
                if (serviceInfo.serviceType.contains("vstreamdeck2")) {
                    resolveService(serviceInfo)
                }
            }

            override fun onServiceLost(serviceInfo: NsdServiceInfo?) {
                if (serviceInfo == null) return
                val current = _servers.value.filter { it.name != serviceInfo.serviceName }
                _servers.value = current
            }
        }

        discoveryListener = listener
        try {
            nsdManager?.discoverServices(serviceType, NsdManager.PROTOCOL_DNS_SD, listener)
        } catch (e: Exception) {
            Log.e(tag, "Error discovering services", e)
        }
    }

    /**
     * Active Unicast TCP Socket Probe.
     * Checks if Stream Deck port 28198 is open on the target host.
     * Bypasses router multicast filters and immediately adds the server.
     */
    fun probeDirectServer(
        host: String,
        port: Int = 28198,
        name: String = "MASH-OLED23",
        onResult: ((Boolean) -> Unit)? = null
    ) {
        scope.launch(Dispatchers.IO) {
            val isReachable = try {
                Socket().use { socket ->
                    socket.connect(InetSocketAddress(host, port), 1200)
                    true
                }
            } catch (_: Exception) {
                false
            }

            if (isReachable) {
                Log.i(tag, "Direct probe succeeded: $name at $host:$port is alive!")
                addServer(DiscoveredServer(name, host, port))
            } else {
                Log.d(tag, "Direct probe failed for $host:$port")
            }

            withContext(Dispatchers.Main) {
                onResult?.invoke(isReachable)
            }
        }
    }

    fun addServer(newServer: DiscoveredServer) {
        val current = _servers.value.toMutableList()
        val index = current.indexOfFirst { it.host == newServer.host && it.port == newServer.port }
        if (index == -1) {
            current.add(0, newServer) // Add to top
            _servers.value = current
        } else {
            // Update name if changed
            if (current[index].name != newServer.name) {
                current[index] = newServer
                _servers.value = current
            }
        }
    }

    private fun resolveService(serviceInfo: NsdServiceInfo) {
        nsdManager?.resolveService(serviceInfo, object : NsdManager.ResolveListener {
            override fun onResolveFailed(serviceInfo: NsdServiceInfo?, errorCode: Int) {
                Log.w(tag, "Service resolve failed: $errorCode")
            }

            override fun onServiceResolved(resolvedInfo: NsdServiceInfo?) {
                if (resolvedInfo == null) return
                val host = resolvedInfo.host?.hostAddress ?: return
                val port = resolvedInfo.port
                val name = resolvedInfo.serviceName ?: "Stream Deck PC"

                Log.d(tag, "Service resolved via mDNS: $name @ $host:$port")
                addServer(DiscoveredServer(name, host, port))
            }
        })
    }

    fun stopDiscovery() {
        discoveryListener?.let {
            try {
                nsdManager?.stopServiceDiscovery(it)
            } catch (e: Exception) {
                // Ignore if already stopped
            }
        }
        discoveryListener = null

        try {
            if (multicastLock?.isHeld == true) {
                multicastLock?.release()
            }
        } catch (e: Exception) {
            // Ignore
        }
    }
}
