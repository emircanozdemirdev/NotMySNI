package com.notmysni.engine.forward

import com.notmysni.model.TransportProtocol

internal object UdpResponseBuilder {

    fun build(
        sourceIp: ByteArray,
        sourcePort: Int,
        destinationIp: ByteArray,
        destinationPort: Int,
        payload: ByteArray
    ): ByteArray {
        val udpLength = 8 + payload.size
        val totalLength = 20 + udpLength
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
        packet[9] = TransportProtocol.UDP.toByte()
        System.arraycopy(sourceIp, 0, packet, 12, 4)
        System.arraycopy(destinationIp, 0, packet, 16, 4)

        val udpOffset = 20
        packet[udpOffset] = ((sourcePort shr 8) and 0xFF).toByte()
        packet[udpOffset + 1] = (sourcePort and 0xFF).toByte()
        packet[udpOffset + 2] = ((destinationPort shr 8) and 0xFF).toByte()
        packet[udpOffset + 3] = (destinationPort and 0xFF).toByte()
        packet[udpOffset + 4] = ((udpLength shr 8) and 0xFF).toByte()
        packet[udpOffset + 5] = (udpLength and 0xFF).toByte()
        packet[udpOffset + 6] = 0x00
        packet[udpOffset + 7] = 0x00
        System.arraycopy(payload, 0, packet, udpOffset + 8, payload.size)

        IpChecksum.apply(packet, totalLength)
        return packet
    }
}
