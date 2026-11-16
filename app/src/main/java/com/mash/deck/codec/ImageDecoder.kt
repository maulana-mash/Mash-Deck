package com.mash.deck.codec

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.elgato.vsd2.Messages
import net.jpountz.lz4.LZ4Factory
import java.nio.ByteBuffer

object ImageDecoder {
    private val lz4Factory = LZ4Factory.fastestInstance()

    fun decodeImage(image: Messages.Image): Bitmap? {
        val rawBytes = image.image.toByteArray()
        if (rawBytes.isEmpty()) return null

        val width = image.size.width
        val height = image.size.height

        return try {
            when (image.format) {
                Messages.ImageFormat.PNG,
                Messages.ImageFormat.JPG,
                Messages.ImageFormat.BMP -> {
                    BitmapFactory.decodeByteArray(rawBytes, 0, rawBytes.size)
                }

                Messages.ImageFormat.LZF -> {
                    val expectedBytes = if (width > 0 && height > 0) width * height * 4 else 0
                    val decompressed = LzfDecompressor.decompress(rawBytes, expectedBytes)
                    if (width > 0 && height > 0 && decompressed.size >= width * height * 4) {
                        createBitmapFromRgba(decompressed, width, height)
                    } else {
                        BitmapFactory.decodeByteArray(decompressed, 0, decompressed.size)
                    }
                }

                Messages.ImageFormat.LZ4 -> {
                    if (width > 0 && height > 0) {
                        val expectedBytes = width * height * 4
                        val decompressed = ByteArray(expectedBytes)
                        lz4Factory.fastDecompressor().decompress(rawBytes, 0, decompressed, 0, expectedBytes)
                        createBitmapFromRgba(decompressed, width, height)
                    } else {
                        BitmapFactory.decodeByteArray(rawBytes, 0, rawBytes.size)
                    }
                }

                else -> {
                    // Fallback to standard bitmap factory
                    BitmapFactory.decodeByteArray(rawBytes, 0, rawBytes.size)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun createBitmapFromRgba(rgbaBytes: ByteArray, width: Int, height: Int): Bitmap {
        // Windows Stream Deck Desktop sends raw pixel buffers in BGRA byte order (Blue=0, Green=1, Red=2, Alpha=3).
        // Android's ARGB_8888 bitmap expects native RGBA byte order (Red=0, Green=1, Blue=2, Alpha=3).
        // Swap Blue (byte 0) and Red (byte 2) so colors render with 100% fidelity as seen on PC:
        var i = 0
        val limit = minOf(rgbaBytes.size - 3, width * height * 4)
        while (i < limit) {
            val b = rgbaBytes[i]
            rgbaBytes[i] = rgbaBytes[i + 2]
            rgbaBytes[i + 2] = b
            i += 4
        }

        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        bitmap.copyPixelsFromBuffer(ByteBuffer.wrap(rgbaBytes))
        return bitmap
    }
}
