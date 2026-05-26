package com.notmysni.testutil

import com.notmysni.engine.forward.IpChecksum
import com.notmysni.engine.forward.TcpChecksum
import com.notmysni.model.TransportProtocol

/**
 * Builds synthetic IPv4 packets for parser unit tests.
 */
object PacketFixtures {

    /**
     * Realistic TLS 1.2 ClientHello (record 0x16, handshake 0x01) with SNI [serverName].
     * Structure matches RFC 5246 / RFC 6066 layout used by [com.notmysni.engine.parser.TlsClientHelloParser].
     */
    fun buildTlsClientHelloPayload(serverName: String): ByteArray {
        val hostBytes = serverName.toByteArray(Charsets.US_ASCII)
        val sniListLength = 1 + 2 + hostBytes.size
        val sniExtensionDataLength = 2 + sniListLength
        val sniExtensionLength = 4 + sniExtensionDataLength
        val extensionsLength = sniExtensionLength

        val cipherSuitesLength = 2
        val compressionMethodsLength = 1
        val sessionIdLength = 0

        val clientHelloBodyLength =
            2 + 32 + 1 + sessionIdLength + 2 + cipherSuitesLength + 1 + compressionMethodsLength +
                2 + extensionsLength

        val handshakeMessageLength = 1 + 3 + clientHelloBodyLength
        val recordLength = handshakeMessageLength

        val packet = ByteArray(5 + handshakeMessageLength)
        var offset = 0

        packet[offset++] = 0x16
        packet[offset++] = 0x03
        packet[offset++] = 0x01
        writeUInt16(packet, offset, recordLength)
        offset += 2

        packet[offset++] = 0x01
        writeUInt24(packet, offset, clientHelloBodyLength)
        offset += 3

        packet[offset++] = 0x03
        packet[offset++] = 0x03
        repeat(32) { packet[offset++] = 0x00 }
        packet[offset++] = sessionIdLength.toByte()

        writeUInt16(packet, offset, cipherSuitesLength)
        offset += 2
        packet[offset++] = 0x00
        packet[offset++] = 0x2F

        packet[offset++] = compressionMethodsLength.toByte()
        packet[offset++] = 0x00

        writeUInt16(packet, offset, extensionsLength)
        offset += 2

        writeUInt16(packet, offset, 0x0000)
        offset += 2
        writeUInt16(packet, offset, sniExtensionDataLength)
        offset += 2
        writeUInt16(packet, offset, sniListLength)
        offset += 2
        packet[offset++] = 0x00
        writeUInt16(packet, offset, hostBytes.size)
        offset += 2
        hostBytes.copyInto(packet, offset)
        offset += hostBytes.size

        return packet
    }

    fun buildIpv4TcpPacket(
        sourceIp: ByteArray,
        destinationIp: ByteArray,
        sourcePort: Int,
        destinationPort: Int,
        tcpPayload: ByteArray,
        sequenceNumber: Long = 0L,
        acknowledgmentNumber: Long = 0L
    ): ByteArray {
        val ipHeaderLength = 20
        val tcpHeaderLength = 20
        val totalLength = ipHeaderLength + tcpHeaderLength + tcpPayload.size
        val packet = ByteArray(totalLength)

        packet[0] = 0x45
        writeUInt16(packet, 2, totalLength)
        packet[8] = 64
        packet[9] = TransportProtocol.TCP.toByte()
        sourceIp.copyInto(packet, 12)
        destinationIp.copyInto(packet, 16)

        val tcpOffset = ipHeaderLength
        writeUInt16(packet, tcpOffset, sourcePort)
        writeUInt16(packet, tcpOffset + 2, destinationPort)
        writeUInt32(packet, tcpOffset + 4, sequenceNumber)
        writeUInt32(packet, tcpOffset + 8, acknowledgmentNumber)
        packet[tcpOffset + 12] = 0x50
        packet[tcpOffset + 13] = 0x18

        tcpPayload.copyInto(packet, ipHeaderLength + tcpHeaderLength)

        val tcpLength = tcpHeaderLength + tcpPayload.size
        IpChecksum.apply(packet, totalLength)
        TcpChecksum.apply(packet, totalLength, ipHeaderLength, tcpLength)
        return packet
    }

    fun ipAddress(a: Int, b: Int, c: Int, d: Int): ByteArray =
        byteArrayOf(a.toByte(), b.toByte(), c.toByte(), d.toByte())

    private fun writeUInt16(buffer: ByteArray, offset: Int, value: Int) {
        buffer[offset] = ((value shr 8) and 0xFF).toByte()
        buffer[offset + 1] = (value and 0xFF).toByte()
    }

    private fun writeUInt24(buffer: ByteArray, offset: Int, value: Int) {
        buffer[offset] = ((value shr 16) and 0xFF).toByte()
        buffer[offset + 1] = ((value shr 8) and 0xFF).toByte()
        buffer[offset + 2] = (value and 0xFF).toByte()
    }

    private fun writeUInt32(buffer: ByteArray, offset: Int, value: Long) {
        buffer[offset] = ((value shr 24) and 0xFF).toByte()
        buffer[offset + 1] = ((value shr 16) and 0xFF).toByte()
        buffer[offset + 2] = ((value shr 8) and 0xFF).toByte()
        buffer[offset + 3] = (value and 0xFF).toByte()
    }
}
