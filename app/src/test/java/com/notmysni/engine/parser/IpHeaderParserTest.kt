package com.notmysni.engine.parser

import com.notmysni.model.TransportProtocol
import com.notmysni.testutil.PacketFixtures
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class IpHeaderParserTest {

    @Test
    fun parse_extractsIpv4Fields() {
        val payload = PacketFixtures.buildTlsClientHelloPayload("example.com")
        val packet = PacketFixtures.buildIpv4TcpPacket(
            sourceIp = PacketFixtures.ipAddress(10, 0, 0, 2),
            destinationIp = PacketFixtures.ipAddress(93, 184, 216, 34),
            sourcePort = 44124,
            destinationPort = 443,
            tcpPayload = payload
        )

        val header = IpHeaderParser.parse(packet, packet.size)

        assertNotNull(header)
        assertEquals(4, header!!.version)
        assertEquals(20, header.headerLength)
        assertEquals(packet.size, header.totalLength)
        assertEquals(TransportProtocol.TCP, header.protocol)
        assertEquals("10.0.0.2", header.sourceAddress.toDisplayString())
        assertEquals("93.184.216.34", header.destinationAddress.toDisplayString())
    }

    @Test
    fun parse_returnsNullForNonIpv4() {
        val packet = byteArrayOf(0x60, 0x00, 0x00, 0x00)
        assertNull(IpHeaderParser.parse(packet, packet.size))
    }
}
