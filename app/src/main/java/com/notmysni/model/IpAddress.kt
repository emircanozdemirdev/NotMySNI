package com.notmysni.model

data class IpAddress(val bytes: ByteArray) {
    fun toDisplayString(): String =
        "${bytes[0].toInt() and 0xFF}.${bytes[1].toInt() and 0xFF}." +
            "${bytes[2].toInt() and 0xFF}.${bytes[3].toInt() and 0xFF}"

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is IpAddress) return false
        return bytes.contentEquals(other.bytes)
    }

    override fun hashCode(): Int = bytes.contentHashCode()
}
