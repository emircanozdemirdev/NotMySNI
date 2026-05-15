package com.notmysni.engine.forward

internal object Ipv4Packet {
    const val PROTOCOL_TCP = 6
    const val PROTOCOL_UDP = 17

    fun version(packet: ByteArray, length: Int): Int {
        if (length < 1) return -1
        return (packet[0].toInt() ushr 4) and 0x0F
    }

    fun headerLength(packet: ByteArray, length: Int): Int {
        if (length < 1) return -1
        return (packet[0].toInt() and 0x0F) * 4
    }

    fun totalLength(packet: ByteArray, length: Int): Int {
        if (length < 4) return -1
        return ((packet[2].toInt() and 0xFF) shl 8) or (packet[3].toInt() and 0xFF)
    }

    fun protocol(packet: ByteArray, length: Int): Int {
        if (length < 10) return -1
        return packet[9].toInt() and 0xFF
    }

    fun sourceAddress(packet: ByteArray, length: Int): ByteArray? {
        if (length < 20) return null
        return packet.copyOfRange(12, 16)
    }

    fun destinationAddress(packet: ByteArray, length: Int): ByteArray? {
        if (length < 20) return null
        return packet.copyOfRange(16, 20)
    }

    fun transportHeaderOffset(packet: ByteArray, length: Int): Int = headerLength(packet, length)

    fun tcpFlags(packet: ByteArray, length: Int): Int {
        val ipHeaderLength = headerLength(packet, length)
        if (ipHeaderLength < 20 || length < ipHeaderLength + 14) return 0
        return packet[ipHeaderLength + 13].toInt() and 0xFF
    }

    fun tcpHeaderLength(packet: ByteArray, length: Int): Int {
        val ipHeaderLength = headerLength(packet, length)
        if (ipHeaderLength < 20 || length < ipHeaderLength + 13) return -1
        return ((packet[ipHeaderLength + 12].toInt() ushr 4) and 0x0F) * 4
    }

    fun sourcePort(packet: ByteArray, length: Int): Int {
        val offset = transportHeaderOffset(packet, length)
        if (offset < 0 || length < offset + 2) return -1
        return ((packet[offset].toInt() and 0xFF) shl 8) or (packet[offset + 1].toInt() and 0xFF)
    }

    fun destinationPort(packet: ByteArray, length: Int): Int {
        val offset = transportHeaderOffset(packet, length)
        if (offset < 0 || length < offset + 4) return -1
        return ((packet[offset + 2].toInt() and 0xFF) shl 8) or (packet[offset + 3].toInt() and 0xFF)
    }

    fun tcpPayloadOffset(packet: ByteArray, length: Int): Int {
        val ipHeaderLength = headerLength(packet, length)
        val tcpHeaderLength = tcpHeaderLength(packet, length)
        if (ipHeaderLength < 0 || tcpHeaderLength < 0) return -1
        return ipHeaderLength + tcpHeaderLength
    }

    fun udpPayloadOffset(packet: ByteArray, length: Int): Int {
        val ipHeaderLength = headerLength(packet, length)
        if (ipHeaderLength < 0 || length < ipHeaderLength + 8) return -1
        return ipHeaderLength + 8
    }

    fun addressToString(address: ByteArray): String =
        "${address[0].toInt() and 0xFF}.${address[1].toInt() and 0xFF}." +
            "${address[2].toInt() and 0xFF}.${address[3].toInt() and 0xFF}"
}
