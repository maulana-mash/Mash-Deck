package com.mash.deck.sensor

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import kotlin.math.abs

/**
 * High-Precision Anti-Drift Air Laser Pointer Motion Engine for Mash Deck.
 * Features:
 * 1. Background Resting Zero-Bias Tracker (pre-calibrated before touch)
 * 2. Thumb-Press Transient Blanking (skips initial kinetic press torque)
 * 3. Continuous Rescaled Deadzone (zero ghost drift when held still, smooth startup)
 * 4. Single-copy sequenced UDP packet dispatch
 */
class GyroLaserManager(
    private val context: Context,
    private val scope: CoroutineScope
) : SensorEventListener {

    private val tag = "GyroLaserManager"
    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager

    // Priority 1: Direct Hardware Gyroscope (Instant, zero-lag, no gimbal lock)
    private val gyroSensor: Sensor? = sensorManager?.getDefaultSensor(Sensor.TYPE_GYROSCOPE)

    // Priority 2: 3D Rotation Vector (Quaternion-based relative delta)
    private val rotSensor: Sensor? = sensorManager?.getDefaultSensor(Sensor.TYPE_GAME_ROTATION_VECTOR)
        ?: sensorManager?.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)

    // Priority 3: Accelerometer tilt fallback
    private val accelSensor: Sensor? = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

    private val activeSensor: Sensor? = gyroSensor ?: rotSensor ?: accelSensor

    @Volatile
    private var isTracking = false
    private var udpSocket: DatagramSocket? = null
    private var targetInetAddress: InetAddress? = null
    private var subnetBroadcastAddress: InetAddress? = null
    private var globalBroadcastAddress: InetAddress? = null

    // Non-blocking packet channel
    private val packetChannel = Channel<String>(capacity = 64, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    // Gyroscope tracking state
    private var lastGyroTimestampNs: Long = 0L
    private var seqNum: Long = 0L
    private var skipCount: Int = 0

    // Dynamic Zero-Rate Bias Compensation
    private var biasWx = 0f
    private var biasYaw = 0f

    // Quaternion tracking for rotation vector fallback: [w, x, y, z]
    private var lastQuat: FloatArray? = null

    // Accelerometer tracking
    private var lastAccelX: Float? = null
    private var lastAccelY: Float? = null

    init {
        // Pre-initialize UDP socket so it's ready immediately
        scope.launch(Dispatchers.IO) {
            try {
                udpSocket = DatagramSocket().apply {
                    broadcast = true
                }
                globalBroadcastAddress = InetAddress.getByName("255.255.255.255")
            } catch (e: Exception) {
                Log.e(tag, "Failed to initialize DatagramSocket: ${e.message}")
            }

            // Dedicated packet dispatcher: sends exactly ONE copy per event
            for (msg in packetChannel) {
                val sock = udpSocket ?: continue
                try {
                    val bytes = msg.toByteArray(Charsets.UTF_8)
                    val target = targetInetAddress

                    if (target != null) {
                        // Unicast directly to target PC (single packet!)
                        sock.send(DatagramPacket(bytes, bytes.size, target, 19842))
                    } else {
                        // Only broadcast if unicast destination is not known
                        subnetBroadcastAddress?.let { bcast ->
                            sock.send(DatagramPacket(bytes, bytes.size, bcast, 19842))
                        } ?: globalBroadcastAddress?.let { gbcast ->
                            sock.send(DatagramPacket(bytes, bytes.size, gbcast, 19842))
                        }
                    }
                } catch (e: Exception) {
                    Log.w(tag, "UDP packet send exception: ${e.message}")
                }
            }
        }

        // Start background sensor listener to pre-converge the zero-rate bias
        activeSensor?.let { sensor ->
            sensorManager?.registerListener(this, sensor, SensorManager.SENSOR_DELAY_UI)
        }
    }

    fun startTracking(host: String) {
        val cleanHost = host.trim()
            .replace(Regex("^[a-zA-Z0-9+.-]+://"), "")
            .substringBefore(":")
            .substringBefore("/")

        Log.i(tag, "startTracking -> Target: $cleanHost, Sensor: ${activeSensor?.name ?: "None"}")
        isTracking = true
        lastGyroTimestampNs = 0L
        skipCount = 0
        lastQuat = null
        lastAccelX = null
        lastAccelY = null

        scope.launch(Dispatchers.IO) {
            try {
                if (udpSocket == null || udpSocket?.isClosed == true) {
                    udpSocket = DatagramSocket().apply {
                        broadcast = true
                    }
                }
                if (cleanHost.isNotEmpty()) {
                    targetInetAddress = InetAddress.getByName(cleanHost)

                    val parts = cleanHost.split(".")
                    if (parts.size == 4) {
                        try {
                            subnetBroadcastAddress = InetAddress.getByName("${parts[0]}.${parts[1]}.${parts[2]}.255")
                        } catch (_: Exception) {}
                    }
                }
                // Send calibration ping
                packetChannel.trySend("GYRO:CALIBRATE")
            } catch (e: Exception) {
                Log.e(tag, "Socket configuration error: ${e.message}")
            }
        }

        // Promote to high-frequency sampling for smooth motion tracking
        activeSensor?.let { sensor ->
            sensorManager?.registerListener(this, sensor, SensorManager.SENSOR_DELAY_GAME)
        }
    }

    fun stopTracking() {
        if (!isTracking) return
        Log.i(tag, "stopTracking")
        isTracking = false
        lastGyroTimestampNs = 0L
        skipCount = 0
        lastQuat = null
        lastAccelX = null
        lastAccelY = null

        // Demote back to low-power UI sampling to continue resting-bias tracking quietly
        activeSensor?.let { sensor ->
            sensorManager?.registerListener(this, sensor, SensorManager.SENSOR_DELAY_UI)
        }
    }

    fun cleanup() {
        isTracking = false
        sensorManager?.unregisterListener(this)
        try {
            udpSocket?.close()
        } catch (_: Exception) {}
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null) return

        when (event.sensor.type) {
            Sensor.TYPE_GYROSCOPE -> {
                val wx = event.values[0] // Pitch rate around X-axis (tilting up/down)
                val wy = event.values[1] // Roll rate around Y-axis (wrist rotation in portrait)
                val wz = event.values[2] // Yaw rate around Z-axis (arm swing in portrait)

                // Blend yaw in portrait mode (phone held facing user)
                val rawYaw = wy * 0.70f + wz * 0.50f

                if (!isTracking) {
                    // Background idle: gently adapt zero-rate bias when hand is relatively still
                    if (abs(wx) < 0.12f && abs(rawYaw) < 0.12f) {
                        biasWx = biasWx * 0.95f + wx * 0.05f
                        biasYaw = biasYaw * 0.95f + rawYaw * 0.05f
                    }
                    return
                }

                val nowNs = event.timestamp
                if (lastGyroTimestampNs == 0L) {
                    lastGyroTimestampNs = nowNs
                    return
                }
                val dt = ((nowNs - lastGyroTimestampNs) / 1_000_000_000f).coerceIn(0.001f, 0.05f)
                lastGyroTimestampNs = nowNs

                // Blank out the initial 40ms (first 4 samples) of thumb press kinetic shock
                if (skipCount < 4) {
                    skipCount++
                    return
                }

                // Subtract the rock-solid pre-calibrated resting bias
                val unskewedWx = wx - biasWx
                val unskewedYaw = rawYaw - biasYaw

                // Rescaled smooth deadzone: tuned for ultra-light effortless feel while maintaining zero drift
                val dz = 0.026f // rad/s (~1.5 deg/s, reliably blocks resting hand tremor)
                val pitch = if (abs(unskewedWx) <= dz) 0f else Math.signum(unskewedWx) * (abs(unskewedWx) - dz)
                val yaw = if (abs(unskewedYaw) <= dz) 0f else Math.signum(unskewedYaw) * (abs(unskewedYaw) - dz)

                if (pitch != 0f || yaw != 0f) {
                    // Physical angular displacement in RADIANS
                    // Pitch: Tilting up (wx > 0) -> dy < 0 (moves UP on screen)
                    // Yaw: Turning right (wy < 0) -> yaw < 0 -> dx > 0 (moves RIGHT on screen)
                    val dxRad = -yaw * dt
                    val dyRad = -pitch * dt

                    val seq = ++seqNum
                    packetChannel.trySend("GYRO:$seq:$dxRad:$dyRad")
                }
            }

            Sensor.TYPE_GAME_ROTATION_VECTOR, Sensor.TYPE_ROTATION_VECTOR -> {
                if (!isTracking) return

                val q = FloatArray(4)
                SensorManager.getQuaternionFromVector(q, event.values)
                val prev = lastQuat
                if (prev != null) {
                    val w0 = prev[0]; val x0 = prev[1]; val y0 = prev[2]; val z0 = prev[3]
                    val w1 = q[0];    val x1 = q[1];    val y1 = q[2];    val z1 = q[3]

                    val dxQuat = w1 * (-x0) + x1 * w0 + y1 * (-z0) - z1 * (-y0)
                    val dyQuat = w1 * (-y0) - x1 * (-z0) + y1 * w0 + z1 * (-x0)
                    val dzQuat = w1 * (-z0) + x1 * (-y0) - y1 * (-x0) + z1 * w0

                    val deltaYaw = -(dyQuat * 0.70f + dzQuat * 0.50f) * 2.0f
                    val deltaPitch = -dxQuat * 2.0f

                    val dz = 0.001f
                    val cleanYaw = if (abs(deltaYaw) <= dz) 0f else Math.signum(deltaYaw) * (abs(deltaYaw) - dz)
                    val cleanPitch = if (abs(deltaPitch) <= dz) 0f else Math.signum(deltaPitch) * (abs(deltaPitch) - dz)

                    if (cleanYaw != 0f || cleanPitch != 0f) {
                        val seq = ++seqNum
                        packetChannel.trySend("GYRO:$seq:$cleanYaw:$cleanPitch")
                    }
                }
                lastQuat = q
            }

            Sensor.TYPE_ACCELEROMETER -> {
                if (!isTracking) return

                val ax = event.values[0]
                val ay = event.values[1]
                val prevX = lastAccelX
                val prevY = lastAccelY

                if (prevX != null && prevY != null) {
                    val dAx = ax - prevX
                    val dAy = ay - prevY
                    val dx = -dAx * 0.02f
                    val dy = -dAy * 0.02f
                    if (abs(dx) > 0.002f || abs(dy) > 0.002f) {
                        val seq = ++seqNum
                        packetChannel.trySend("GYRO:$seq:$dx:$dy")
                    }
                }
                lastAccelX = ax
                lastAccelY = ay
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
}
