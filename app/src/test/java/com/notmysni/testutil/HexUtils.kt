package com.notmysni.testutil

object HexUtils {

    fun hexToBytes(hex: String): ByteArray {
        val normalized = hex.replace("\\s+".toRegex(), "")
        require(normalized.length % 2 == 0) { "Hex string must have even length" }
        return ByteArray(normalized.length / 2) { index ->
            val start = index * 2
            normalized.substring(start, start + 2).toInt(16).toByte()
        }
    }
}
