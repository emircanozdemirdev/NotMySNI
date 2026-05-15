package com.notmysni.engine.forward

internal data class ConnectionKey(
    val sourceIp: ByteArray,
    val sourcePort: Int,
    val destinationIp: ByteArray,
    val destinationPort: Int
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ConnectionKey) return false
        return sourcePort == other.sourcePort &&
            destinationPort == other.destinationPort &&
            sourceIp.contentEquals(other.sourceIp) &&
            destinationIp.contentEquals(other.destinationIp)
    }

    override fun hashCode(): Int {
        var result = sourcePort
        result = 31 * result + destinationPort
        result = 31 * result + sourceIp.contentHashCode()
        result = 31 * result + destinationIp.contentHashCode()
        return result
    }
}
