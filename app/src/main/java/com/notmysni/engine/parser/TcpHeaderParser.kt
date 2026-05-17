package com.notmysni.engine.parser

import com.notmysni.model.TcpFlags
import com.notmysni.model.TcpHeader

object TcpHeaderParser {

    fun parse(packet: ByteArray, length: Int, ipHeaderLength: Int): TcpHeader? {
        val tcpOffset = ipHeaderLength
        if (length < tcpOffset + 20) return null

        val sourcePort = readUInt16(packet, tcpOffset)
        val destinationPort = readUInt16(packet, tcpOffset + 2)
        val sequenceNumber = readUInt32(packet, tcpOffset + 4)
        val acknowledgmentNumber = readUInt32(packet, tcpOffset + 8)
        val headerLength = ((packet[tcpOffset + 12].toInt() ushr 4) and 0x0F) * 4
        if (headerLength < 20) return null

        val payloadOffset = tcpOffset + headerLength
        if (length < payloadOffset) return null

        val flags = TcpFlags(packet[tcpOffset + 13].toInt() and 0xFF)

        return TcpHeader(
            sourcePort = sourcePort,
            destinationPort = destinationPort,
            sequenceNumber = sequenceNumber,
            acknowledgmentNumber = acknowledgmentNumber,
            headerLength = headerLength,
            flags = flags,
            payloadOffset = payloadOffset
        )
    }

    private fun readUInt16(packet: ByteArray, offset: Int): Int =
        ((packet[offset].toInt() and 0xFF) shl 8) or (packet[offset + 1].toInt() and 0xFF)

    private fun readUInt32(packet: ByteArray, offset: Int): Long =
        ((packet[offset].toLong() and 0xFF) shl 24) or
            ((packet[offset + 1].toLong() and 0xFF) shl 16) or
            ((packet[offset + 2].toLong() and 0xFF) shl 8) or
            (packet[offset + 3].toLong() and 0xFF)
}
