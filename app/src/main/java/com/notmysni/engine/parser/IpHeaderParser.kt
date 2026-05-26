package com.notmysni.engine.parser

import com.notmysni.model.IpAddress
import com.notmysni.model.IpHeader

object IpHeaderParser {

    fun parse(packet: ByteArray, length: Int): IpHeader? {
        if (length < 20) return null

        val version = (packet[0].toInt() ushr 4) and 0x0F
        if (version != 4) return null

        val headerLength = (packet[0].toInt() and 0x0F) * 4
        if (headerLength < 20 || length < headerLength) return null

        val totalLength = readUInt16(packet, 2)
        val ttl = packet[8].toInt() and 0xFF
        val protocol = packet[9].toInt() and 0xFF
        val sourceAddress = IpAddress(packet.copyOfRange(12, 16))
        val destinationAddress = IpAddress(packet.copyOfRange(16, 20))

        return IpHeader(
            version = version,
            headerLength = headerLength,
            totalLength = totalLength,
            protocol = protocol,
            sourceAddress = sourceAddress,
            destinationAddress = destinationAddress,
            ttl = ttl
        )
    }

    // Step 6.2 — TTL-based desync technique
    //
    // fun parseTtlOnly(packet: ByteArray): Int = packet[8].toInt() and 0xFF

    private fun readUInt16(packet: ByteArray, offset: Int): Int =
        ((packet[offset].toInt() and 0xFF) shl 8) or (packet[offset + 1].toInt() and 0xFF)
}
