package com.nxkeyboard.ai

import android.util.Base64

internal object ApiKeyVault {

    private val fragmentAlpha: ByteArray = byteArrayOf(
        90, 85, 85, 84, 111, 97, 122, 98, 56, 75, 54, 77, 48, 83, 100, 109, 66, 69, 104, 105, 101, 85, 112, 102, 47
    )

    private val fragmentBeta: ByteArray = byteArrayOf(
        55, 55, 89, 55, 75, 84, 72, 103, 105, 81, 51, 85, 82, 120, 111, 75, 82, 108, 98, 47, 43, 122, 86, 110, 75
    )

    private val fragmentGamma: ByteArray = byteArrayOf(
        97, 82, 48, 72, 90, 114, 88, 82, 66, 109, 76, 120, 119, 78, 47, 79, 72, 101, 122, 114, 51, 72, 48, 105, 66
    )

    private val fragmentDelta: ByteArray = byteArrayOf(
        110, 66, 69, 73, 47, 99, 107, 115, 77, 56, 55, 114, 90, 122, 79, 121, 114, 49, 110, 49, 104, 65, 81, 61, 61
    )

    private fun maskByte(index: Int): Int {
        val seed = (index * 17 + 91)
        val rotor = (index % 7) + 23
        return (seed xor rotor xor 0x5A) and 0xFF
    }

    private fun assembleEncoded(): String {
        val total = fragmentAlpha.size + fragmentBeta.size + fragmentGamma.size + fragmentDelta.size
        val combined = ByteArray(total)
        var cursor = 0
        for (b in fragmentAlpha) { combined[cursor++] = b }
        for (b in fragmentBeta)  { combined[cursor++] = b }
        for (b in fragmentGamma) { combined[cursor++] = b }
        for (b in fragmentDelta) { combined[cursor++] = b }
        return String(combined, Charsets.US_ASCII)
    }

    fun resolve(): String {
        val encoded = assembleEncoded()
        val cipher = Base64.decode(encoded, Base64.NO_WRAP)
        val plain = ByteArray(cipher.size)
        for (i in cipher.indices) {
            val mask = maskByte(i).toByte()
            plain[i] = (cipher[i].toInt() xor mask.toInt()).toByte()
        }
        return String(plain, Charsets.US_ASCII)
    }

    fun isAvailable(): Boolean {
        return try {
            val key = resolve()
            key.startsWith("sk-") && key.length > 32
        } catch (t: Throwable) {
            false
        }
    }
}
