package com.mash.deck.crypto

import org.bouncycastle.crypto.agreement.X25519Agreement
import org.bouncycastle.crypto.digests.Blake2bDigest
import org.bouncycastle.crypto.engines.XSalsa20Engine
import org.bouncycastle.crypto.generators.X25519KeyPairGenerator
import org.bouncycastle.crypto.macs.Poly1305
import org.bouncycastle.crypto.params.KeyParameter
import org.bouncycastle.crypto.params.ParametersWithIV
import org.bouncycastle.crypto.params.X25519KeyGenerationParameters
import org.bouncycastle.crypto.params.X25519PrivateKeyParameters
import org.bouncycastle.crypto.params.X25519PublicKeyParameters
import java.security.SecureRandom

/**
 * Pure Kotlin/Java implementation of Libsodium CryptoBox
 * (Curve25519 ECDH + HSalsa20 KDF + XSalsa20-Poly1305 authenticated encryption + BLAKE2b fingerprint).
 * 100% compatible with libsodium and Elgato VSD2 protocol.
 */
class CryptoBox(
    val privateKey: ByteArray,
    val publicKey: ByteArray
) {
    private var sharedKey: ByteArray? = null
    private val random = SecureRandom()

    /**
     * Calculates the 6-digit hex fingerprint shown on PC Stream Deck Desktop
     * for user authorization.
     */
    fun getFingerprint(): String {
        val digest = Blake2bDigest(256)
        digest.update(publicKey, 0, publicKey.size)
        val hash = ByteArray(32)
        digest.doFinal(hash, 0)
        return hash.joinToString("") { "%02x".format(it) }.take(6)
    }

    /**
     * Precomputes the 32-byte shared key from client private key and server public key.
     * Equivalent to libsodium crypto_box_beforenm.
     */
    fun calculateSharedKey(serverPublicKeyBytes: ByteArray) {
        val agreement = X25519Agreement()
        val privParam = X25519PrivateKeyParameters(privateKey, 0)
        agreement.init(privParam)
        
        val pubParam = X25519PublicKeyParameters(serverPublicKeyBytes, 0)
        val ecdhSecret = ByteArray(32)
        agreement.calculateAgreement(pubParam, ecdhSecret, 0)

        // Libsodium crypto_box_beforenm passes the 32-byte ECDH secret through HSalsa20
        // with an all-zero 16-byte input and the standard 16-byte constant.
        sharedKey = hsalsa20(ecdhSecret, ByteArray(16))
    }

    /**
     * Encrypts plaintext using XSalsa20-Poly1305 with a 24-byte nonce.
     * Equivalent to libsodium crypto_box_easy_afternm.
     * Output format: [24 bytes nonce] + [16 bytes Poly1305 tag] + [ciphertext]
     */
    fun encrypt(plaintext: ByteArray): ByteArray {
        val key = sharedKey ?: throw IllegalStateException("Shared key not calculated yet")
        val nonce = ByteArray(24)
        random.nextBytes(nonce)

        // Subkey and Poly1305 key generation via XSalsa20
        val engine = XSalsa20Engine()
        engine.init(true, ParametersWithIV(KeyParameter(key), nonce))

        // First 32 bytes of XSalsa20 stream are used as the one-time Poly1305 key
        val polyKey = ByteArray(32)
        engine.processBytes(ByteArray(32), 0, 32, polyKey, 0)

        val ciphertext = ByteArray(plaintext.size)
        engine.processBytes(plaintext, 0, plaintext.size, ciphertext, 0)

        // Poly1305 tag over ciphertext
        val poly = Poly1305()
        poly.init(KeyParameter(polyKey))
        poly.update(ciphertext, 0, ciphertext.size)
        val tag = ByteArray(16)
        poly.doFinal(tag, 0)

        return nonce + tag + ciphertext
    }

    /**
     * Decrypts payload encrypted with XSalsa20-Poly1305.
     * Input format: [24 bytes nonce] + [16 bytes Poly1305 tag] + [ciphertext]
     */
    fun decrypt(encryptedPayload: ByteArray): ByteArray {
        val key = sharedKey ?: throw IllegalStateException("Shared key not calculated yet")
        if (encryptedPayload.size < 40) {
            throw IllegalArgumentException("Payload too short for nonce + tag")
        }

        val nonce = encryptedPayload.copyOfRange(0, 24)
        val tag = encryptedPayload.copyOfRange(24, 40)
        val ciphertext = encryptedPayload.copyOfRange(40, encryptedPayload.size)

        val engine = XSalsa20Engine()
        engine.init(true, ParametersWithIV(KeyParameter(key), nonce))

        val polyKey = ByteArray(32)
        engine.processBytes(ByteArray(32), 0, 32, polyKey, 0)

        val poly = Poly1305()
        poly.init(KeyParameter(polyKey))
        poly.update(ciphertext, 0, ciphertext.size)
        val calculatedTag = ByteArray(16)
        poly.doFinal(calculatedTag, 0)

        if (!tag.contentEquals(calculatedTag)) {
            throw SecurityException("Poly1305 tag verification failed")
        }

        val plaintext = ByteArray(ciphertext.size)
        engine.processBytes(ciphertext, 0, ciphertext.size, plaintext, 0)
        return plaintext
    }

    companion object {
        fun generateKeyPair(): CryptoBox {
            val gen = X25519KeyPairGenerator()
            gen.init(X25519KeyGenerationParameters(SecureRandom()))
            val keyPair = gen.generateKeyPair()
            val priv = (keyPair.private as X25519PrivateKeyParameters).encoded
            val pub = (keyPair.public as X25519PublicKeyParameters).encoded
            return CryptoBox(priv, pub)
        }

        private val SIGMA = "expand 32-byte k".toByteArray(Charsets.US_ASCII)

        /**
         * HSalsa20 core function for Libsodium key derivation.
         */
        private fun hsalsa20(key: ByteArray, input: ByteArray): ByteArray {
            val x = IntArray(16)

            fun load32(b: ByteArray, offset: Int): Int {
                return (b[offset].toInt() and 0xFF) or
                        ((b[offset + 1].toInt() and 0xFF) shl 8) or
                        ((b[offset + 2].toInt() and 0xFF) shl 16) or
                        ((b[offset + 3].toInt() and 0xFF) shl 24)
            }

            fun store32(b: ByteArray, offset: Int, v: Int) {
                b[offset] = (v and 0xFF).toByte()
                b[offset + 1] = ((v ushr 8) and 0xFF).toByte()
                b[offset + 2] = ((v ushr 16) and 0xFF).toByte()
                b[offset + 3] = ((v ushr 24) and 0xFF).toByte()
            }

            fun rotl(v: Int, c: Int): Int = (v shl c) or (v ushr (32 - c))

            x[0] = load32(SIGMA, 0)
            x[1] = load32(key, 0)
            x[2] = load32(key, 4)
            x[3] = load32(key, 8)
            x[4] = load32(key, 12)
            x[5] = load32(SIGMA, 4)
            x[6] = load32(input, 0)
            x[7] = load32(input, 4)
            x[8] = load32(input, 8)
            x[9] = load32(input, 12)
            x[10] = load32(SIGMA, 8)
            x[11] = load32(key, 16)
            x[12] = load32(key, 20)
            x[13] = load32(key, 24)
            x[14] = load32(key, 28)
            x[15] = load32(SIGMA, 12)

            for (i in 0 until 10) {
                x[4] = x[4] xor rotl(x[0] + x[12], 7);   x[8] = x[8] xor rotl(x[4] + x[0], 9)
                x[12] = x[12] xor rotl(x[8] + x[4], 13);  x[0] = x[0] xor rotl(x[12] + x[8], 18)
                x[9] = x[9] xor rotl(x[5] + x[1], 7);    x[13] = x[13] xor rotl(x[9] + x[5], 9)
                x[1] = x[1] xor rotl(x[13] + x[9], 13);   x[5] = x[5] xor rotl(x[1] + x[13], 18)
                x[14] = x[14] xor rotl(x[10] + x[6], 7);  x[2] = x[2] xor rotl(x[14] + x[10], 9)
                x[6] = x[6] xor rotl(x[2] + x[14], 13);   x[10] = x[10] xor rotl(x[6] + x[2], 18)
                x[3] = x[3] xor rotl(x[15] + x[11], 7);  x[7] = x[7] xor rotl(x[3] + x[15], 9)
                x[11] = x[11] xor rotl(x[7] + x[3], 13);  x[15] = x[15] xor rotl(x[11] + x[7], 18)

                x[1] = x[1] xor rotl(x[0] + x[3], 7);    x[2] = x[2] xor rotl(x[1] + x[0], 9)
                x[3] = x[3] xor rotl(x[2] + x[1], 13);   x[0] = x[0] xor rotl(x[3] + x[2], 18)
                x[6] = x[6] xor rotl(x[5] + x[4], 7);    x[7] = x[7] xor rotl(x[6] + x[5], 9)
                x[4] = x[4] xor rotl(x[7] + x[6], 13);   x[5] = x[5] xor rotl(x[4] + x[7], 18)
                x[11] = x[11] xor rotl(x[10] + x[9], 7);  x[8] = x[8] xor rotl(x[11] + x[10], 9)
                x[9] = x[9] xor rotl(x[8] + x[11], 13);   x[10] = x[10] xor rotl(x[9] + x[8], 18)
                x[12] = x[12] xor rotl(x[15] + x[14], 7); x[13] = x[13] xor rotl(x[12] + x[15], 9)
                x[14] = x[14] xor rotl(x[13] + x[12], 13); x[15] = x[15] xor rotl(x[14] + x[13], 18)
            }

            val out = ByteArray(32)
            store32(out, 0, x[0])
            store32(out, 4, x[5])
            store32(out, 8, x[10])
            store32(out, 12, x[15])
            store32(out, 16, x[6])
            store32(out, 20, x[7])
            store32(out, 24, x[8])
            store32(out, 28, x[9])
            return out
        }
    }
}
