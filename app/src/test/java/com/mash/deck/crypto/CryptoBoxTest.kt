package com.mash.deck.crypto

import org.bouncycastle.crypto.engines.XSalsa20Engine
import org.bouncycastle.crypto.macs.Poly1305
import org.bouncycastle.crypto.params.KeyParameter
import org.bouncycastle.crypto.params.ParametersWithIV
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class CryptoBoxTest {
    @Test
    fun testSharedKey() {
        val sk = ByteArray(32) { 1 }
        val pk = ByteArray(32) { 2 }
        val cryptoBox = CryptoBox(sk, ByteArray(32))
        cryptoBox.calculateSharedKey(pk)

        val expectedSharedKeyHex = "a76807a82fc681442944355bc7943989ea81d0deef64dcba9fd73cdceda95d2c"
        val actualField = CryptoBox::class.java.getDeclaredField("sharedKey")
        actualField.isAccessible = true
        val actualSharedKey = actualField.get(cryptoBox) as ByteArray
        val actualSharedKeyHex = actualSharedKey.joinToString("") { "%02x".format(it) }

        assertEquals(expectedSharedKeyHex, actualSharedKeyHex)
    }

    @Test
    fun testEncryptMatchesPyNaCl() {
        val sk = ByteArray(32) { 1 }
        val pk = ByteArray(32) { 2 }
        val cryptoBox = CryptoBox(sk, ByteArray(32))
        cryptoBox.calculateSharedKey(pk)

        val actualField = CryptoBox::class.java.getDeclaredField("sharedKey")
        actualField.isAccessible = true
        val sharedKey = actualField.get(cryptoBox) as ByteArray

        val nonce = ByteArray(24) { 3 }
        val msg = "Hello StreamDeck".toByteArray()

        // PyNaCl produced:
        // Ciphertext (MAC 16 bytes + payload): 8bfee2a8c5ecb1ed887b350551b60f13f23e3b11255920539e49b95978314f08
        val expectedCiphertextHex = "8bfee2a8c5ecb1ed887b350551b60f13f23e3b11255920539e49b95978314f08"

        // Let's perform encryption using the exact engine in CryptoBox
        val engine = XSalsa20Engine()
        engine.init(true, ParametersWithIV(KeyParameter(sharedKey), nonce))
        val polyKey = ByteArray(32)
        engine.processBytes(ByteArray(32), 0, 32, polyKey, 0)
        val ciphertext = ByteArray(msg.size)
        engine.processBytes(msg, 0, msg.size, ciphertext, 0)

        val poly = Poly1305()
        poly.init(KeyParameter(polyKey))
        poly.update(ciphertext, 0, ciphertext.size)
        val tag = ByteArray(16)
        poly.doFinal(tag, 0)

        val fullCiphertext = tag + ciphertext
        val actualCiphertextHex = fullCiphertext.joinToString("") { "%02x".format(it) }

        println("Expected Ciphertext: $expectedCiphertextHex")
        println("Actual Ciphertext:   $actualCiphertextHex")
        assertEquals(expectedCiphertextHex, actualCiphertextHex)

        // Test decrypt
        val fullPayload = nonce + tag + ciphertext
        val decrypted = cryptoBox.decrypt(fullPayload)
        assertArrayEquals(msg, decrypted)
    }
}
