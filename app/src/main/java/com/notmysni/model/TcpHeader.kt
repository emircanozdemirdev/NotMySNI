package com.notmysni.model

data class TcpHeader(
    val sourcePort: Int,
    val destinationPort: Int,
    val sequenceNumber: Long,
    val acknowledgmentNumber: Long,
    val headerLength: Int,
    val flags: TcpFlags,
    val payloadOffset: Int
) {
    fun payloadLength(packetLength: Int): Int =
        (packetLength - payloadOffset).coerceAtLeast(0)
}
