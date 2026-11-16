package com.mash.deck.ui.screens

import android.content.Context
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.zxing.*
import com.google.zxing.common.HybridBinarizer
import com.mash.deck.ui.theme.BackgroundDark
import com.mash.deck.ui.theme.TextPrimary
import com.mash.deck.ui.theme.TextSecondary
import java.nio.ByteBuffer

@Composable
fun QrScanScreen(
    onQrCodeDetected: (host: String, port: Int) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundDark)
    ) {
        AndroidView(
            factory = { ctx ->
                val previewView = PreviewView(ctx)
                val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)

                cameraProviderFuture.addListener({
                    val cameraProvider = cameraProviderFuture.get()
                    val preview = Preview.Builder().build().also {
                        it.setSurfaceProvider(previewView.surfaceProvider)
                    }

                    val imageAnalysis = ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build()

                    val reader = MultiFormatReader().apply {
                        setHints(mapOf(DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE)))
                    }

                    imageAnalysis.setAnalyzer(ContextCompat.getMainExecutor(ctx)) { imageProxy ->
                        processImageProxy(reader, imageProxy) { qrText ->
                            parseQrAndCallback(qrText, onQrCodeDetected)
                        }
                    }

                    try {
                        cameraProvider.unbindAll()
                        cameraProvider.bindToLifecycle(
                            lifecycleOwner,
                            CameraSelector.DEFAULT_BACK_CAMERA,
                            preview,
                            imageAnalysis
                        )
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }, ContextCompat.getMainExecutor(ctx))

                previewView
            },
            modifier = Modifier.fillMaxSize()
        )

        // Overlay with Back button and instructions
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onBack,
                    colors = IconButtonDefaults.iconButtonColors(containerColor = Color(0x88000000))
                ) {
                    Icon(
                        imageVector = Icons.Default.ArrowBack,
                        contentDescription = "Back",
                        tint = TextPrimary
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = "Pindai QR Stream Deck PC",
                    color = TextPrimary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

            // QR Reticle Frame Box
            Box(
                modifier = Modifier
                    .size(240.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0x33007AFF))
            )

            // Instruction Card
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xCC141414)),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.padding(bottom = 24.dp)
            ) {
                Text(
                    text = "Arahkan kamera ke QR Code yang tampil pada aplikasi Stream Deck Desktop di komputer Anda.",
                    color = TextSecondary,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(16.dp)
                )
            }
        }
    }
}

private fun processImageProxy(
    reader: MultiFormatReader,
    imageProxy: ImageProxy,
    onSuccess: (String) -> Unit
) {
    val buffer: ByteBuffer = imageProxy.planes[0].buffer
    val bytes = ByteArray(buffer.remaining())
    buffer.get(bytes)

    val width = imageProxy.width
    val height = imageProxy.height

    val source = PlanarYUVLuminanceSource(
        bytes, width, height, 0, 0, width, height, false
    )
    val binaryBitmap = BinaryBitmap(HybridBinarizer(source))

    try {
        val result = reader.decodeWithState(binaryBitmap)
        onSuccess(result.text)
    } catch (e: NotFoundException) {
        // No QR detected in this frame, continue
    } catch (e: Exception) {
        e.printStackTrace()
    } finally {
        reader.reset()
        imageProxy.close()
    }
}

private fun parseQrAndCallback(
    text: String,
    callback: (host: String, port: Int) -> Unit
) {
    try {
        val trimmed = text.trim()

        // 1. If it's a URL query param: host=...&port=...
        if (trimmed.contains("host=") && trimmed.contains("port=")) {
            val host = trimmed.substringAfter("host=").substringBefore("&").substringBefore("?")
            val port = trimmed.substringAfter("port=").substringBefore("&").substringBefore("?").toIntOrNull() ?: 28198
            if (host.isNotBlank()) {
                callback(host, port)
                return
            }
        }

        // 2. Strip any URI scheme (http://, https://, ws://, wss://, streamdeck://, vstreamdeck://)
        var clean = trimmed
        val schemeRegex = Regex("^[a-zA-Z0-9+.-]+://")
        clean = schemeRegex.replace(clean, "")

        // Remove path and query
        val authority = clean.substringBefore("/").substringBefore("?")

        // Check if there is a port at the end (e.g. 192.168.1.5:28198)
        val colonIdx = authority.lastIndexOf(':')
        if (colonIdx != -1) {
            val host = authority.substring(0, colonIdx)
            val port = authority.substring(colonIdx + 1).toIntOrNull() ?: 28198
            if (host.isNotBlank()) {
                callback(host, port)
                return
            }
        } else if (authority.isNotBlank()) {
            callback(authority, 28198)
            return
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }
}
