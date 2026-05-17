package com.notmysni.engine.parser

import com.notmysni.model.TlsClientHello
import com.notmysni.model.TransportProtocol

/**
 * Extracts TLS ClientHello from a raw IPv4 packet (TCP payload).
 */
object PacketTlsParser {

    fun parseClientHello(packet: ByteArray, length: Int): TlsClientHello? {
        val ipHeader = IpHeaderParser.parse(packet, length) ?: return null
        if (ipHeader.protocol != TransportProtocol.TCP) return null

        val tcpHeader = TcpHeaderParser.parse(packet, length, ipHeader.headerLength) ?: return null
        val payloadLength = tcpHeader.payloadLength(length)
        if (payloadLength <= 0) return null

        return TlsClientHelloParser.parse(packet, tcpHeader.payloadOffset, payloadLength)
    }
}
