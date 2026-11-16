package com.mash.deck.codec

import java.io.ByteArrayOutputStream

/**
 * High-performance pure Kotlin LZF decompression.
 * Fully compatible with LibLZF used by Elgato Stream Deck Desktop.
 */
object LzfDecompressor {
    fun decompress(input: ByteArray, expectedLength: Int = 0): ByteArray {
        var inPos = 0
        val inLen = input.size
        val out = ByteArray(if (expectedLength > 0) expectedLength else inLen * 4)
        var outPos = 0

        var dynamicOut: ByteArrayOutputStream? = null
        if (expectedLength == 0) {
            dynamicOut = ByteArrayOutputStream()
        }

        while (inPos < inLen) {
            val ctrl = input[inPos++].toInt() and 0xFF
            if (ctrl < 32) {
                // Literal run: copy (ctrl + 1) bytes
                val len = ctrl + 1
                if (inPos + len > inLen) break
                for (i in 0 until len) {
                    val b = input[inPos++]
                    if (dynamicOut != null) {
                        dynamicOut.write(b.toInt())
                    } else if (outPos < out.size) {
                        out[outPos++] = b
                    }
                }
            } else {
                // Back-reference: match copy
                var len = ctrl shr 5
                val refHigh = (ctrl and 0x1F) shl 8
                if (inPos >= inLen) break
                val refLow = input[inPos++].toInt() and 0xFF
                val refOffset = refHigh or refLow

                if (len == 7) {
                    if (inPos >= inLen) break
                    len += input[inPos++].toInt() and 0xFF
                }
                len += 2

                if (dynamicOut != null) {
                    val currentBytes = dynamicOut.toByteArray()
                    var srcPos = currentBytes.size - refOffset - 1
                    for (i in 0 until len) {
                        if (srcPos in currentBytes.indices) {
                            dynamicOut.write(currentBytes[srcPos].toInt())
                        }
                        srcPos++
                    }
                } else {
                    var srcPos = outPos - refOffset - 1
                    for (i in 0 until len) {
                        if (srcPos in 0 until outPos && outPos < out.size) {
                            out[outPos++] = out[srcPos]
                        }
                        srcPos++
                    }
                }
            }
        }

        return dynamicOut?.toByteArray() ?: out.copyOf(outPos)
    }
}
