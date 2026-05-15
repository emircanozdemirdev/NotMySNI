package com.notmysni.engine.forward

internal object TcpResponseBuilder {

    fun build(
        sourceIp: ByteArray,
        sourcePort: Int,
        destinationIp: ByteArray,
        destinationPort: Int,
        payload: ByteArray,
        payloadLength: Int
    ): ByteArray {
        val tcpHeaderLength = 20
        val tcpLength = tcpHeaderLength + payloadLength
        val totalLength = 20 + tcpLength
        val packet = ByteArray(totalLength)

        packet[0] = 0x45
        packet[1] = 0x00
        packet[2] = ((totalLength shr 8) and 0xFF).toByte()
        packet[3] = (totalLength and 0xFF).toByte()
        packet[4] = 0x00
        packet[5] = 0x00
        packet[6] = 0x40
        packet[7] = 0x00
        packet[8] = 64
        packet[9] = Ipv4Packet.PROTOCOL_TCP.toByte()
        System.arraycopy(sourceIp, 0, packet, 12, 4)
        System.arraycopy(destinationIp, 0, packet, 16, 4)

        val tcpOffset = 20
        packet[tcpOffset] = ((sourcePort shr 8) and 0xFF).toByte()
        packet[tcpOffset + 1] = (sourcePort and 0xFF).toByte()
        packet[tcpOffset + 2] = ((destinationPort shr 8) and 0xFF).toByte()
        packet[tcpOffset + 3] = (destinationPort and 0xFF).toByte()
        packet[tcpOffset + 4] = 0x00
        packet[tcpOffset + 5] = 0x00
        packet[tcpOffset + 6] = 0x00
        packet[tcpOffset + 7] = 0x00
        packet[tcpOffset + 8] = 0x50
        packet[tcpOffset + 9] = 0x10
        packet[tcpOffset + 10] = 0x00
        packet[tcpOffset + 11] = 0x00
        packet[tcpOffset + 12] = 0x50
        packet[tcpOffset + 13] = 0x18.toByte()
        packet[tcpOffset + 14] = 0xFF.toByte()
        packet[tcpOffset + 15] = 0xFF.toByte()
        packet[tcpOffset + 16] = 0x00
        packet[tcpOffset + 17] = 0x00
        packet[tcpOffset + 18] = 0x00
        packet[tcpOffset + 19] = 0x00

        System.arraycopy(payload, 0, packet, tcpOffset + tcpHeaderLength, payloadLength)

        IpChecksum.apply(packet, totalLength)
        TcpChecksum.apply(packet, totalLength, tcpOffset, tcpLength)
        return packet
    }
}
