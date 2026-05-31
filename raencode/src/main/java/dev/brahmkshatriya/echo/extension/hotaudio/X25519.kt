package dev.brahmkshatriya.echo.extension.hotaudio

import java.math.BigInteger
import java.security.SecureRandom

object X25519 {
    private val P = BigInteger.valueOf(2).pow(255).subtract(BigInteger.valueOf(19))
    private val A24 = BigInteger.valueOf(121665)

    fun generatePrivateKey(): ByteArray {
        val bytes = ByteArray(32)
        SecureRandom().nextBytes(bytes)
        bytes[0] = (bytes[0].toInt() and 248).toByte()
        bytes[31] = (bytes[31].toInt() and 127).toByte()
        bytes[31] = (bytes[31].toInt() or 64).toByte()
        return bytes
    }

    fun getPublicKey(privateKey: ByteArray): ByteArray {
        val basePoint = BigInteger.valueOf(9)
        return scalarMult(privateKey, basePoint)
    }

    fun calculateSharedSecret(privateKey: ByteArray, publicKey: ByteArray): ByteArray {
        // BigInteger constructor expects big-endian bytes. 
        // X25519 uses little-endian, so we reverse the byte array.
        // We prepend a 0 byte to ensure it is interpreted as a positive number.
        val paddedBytes = ByteArray(33)
        System.arraycopy(publicKey.reversedArray(), 0, paddedBytes, 1, 32)
        val u = BigInteger(paddedBytes)
        return scalarMult(privateKey, u)
    }

    private fun scalarMult(scalarBytes: ByteArray, u: BigInteger): ByteArray {
        var x1 = u
        var x2 = BigInteger.ONE
        var z2 = BigInteger.ZERO
        var x3 = u
        var z3 = BigInteger.ONE

        var swap = 0
        for (t in 254 downTo 0) {
            val k_t = (scalarBytes[t ushr 3].toInt() ushr (t and 7)) and 1
            swap = swap xor k_t
            if (swap == 1) {
                var temp = x2; x2 = x3; x3 = temp
                temp = z2; z2 = z3; z3 = temp
            }
            swap = k_t

            val a = x2.add(z2).mod(P)
            val aa = a.multiply(a).mod(P)
            val b = x2.subtract(z2).mod(P)
            val bb = b.multiply(b).mod(P)
            val e = aa.subtract(bb).mod(P)
            val c = x3.add(z3).mod(P)
            val d = x3.subtract(z3).mod(P)
            val da = d.multiply(a).mod(P)
            val cb = c.multiply(b).mod(P)
            
            x3 = da.add(cb).pow(2).mod(P)
            z3 = x1.multiply(da.subtract(cb).pow(2)).mod(P)
            x2 = aa.multiply(bb).mod(P)
            z2 = e.multiply(aa.add(A24.multiply(e))).mod(P)
        }

        if (swap == 1) {
            var temp = x2; x2 = x3; x3 = temp
            var temp2 = z2; z2 = z3; z3 = temp2
        }

        val secret = x2.multiply(z2.modInverse(P)).mod(P)
        
        // Convert to 32-byte little-endian array
        val secretBytes = secret.toByteArray()
        val out = ByteArray(32)
        
        // Java's toByteArray might contain a leading sign byte (0) or be shorter than 32 bytes
        var srcPos = 0
        var len = secretBytes.size
        if (secretBytes.isNotEmpty() && secretBytes[0] == 0.toByte()) {
            srcPos = 1
            len--
        }
        val bigEndian = ByteArray(32)
        System.arraycopy(secretBytes, srcPos, bigEndian, 32 - len, len)
        
        // Reverse for little-endian output
        val littleEndian = bigEndian.reversedArray()
        return littleEndian
    }
}
