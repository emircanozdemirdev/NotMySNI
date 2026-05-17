package com.notmysni.engine.parser

import com.notmysni.testutil.PacketFixtures
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TcpHeaderParserTest {

    @Test
    fun parse_extractsTcpFields() {
        val payload = PacketFixtures.buildTlsClientHelloPayload("example.com")
        val packet = PacketFixtures.buildIpv4TcpPacket(
            sourceIp = PacketFixtures.ipAddress(10, 0, 0, 2),
            destinationIp = PacketFixtures.ipAddress(93, 184, 216, 34),
            sourcePort = 44124,
            destinationPort = 443,
            tcpPayload = payload
        )

        val ipHeader = IpHeaderParser.parse(packet, packet.size)!!
        val tcpHeader = TcpHeaderParser.parse(packet, packet.size, ipHeader.headerLength)

        assertNotNull(tcpHeader)
        assertEquals(44124, tcpHeader!!.sourcePort)
        assertEquals(443, tcpHeader.destinationPort)
        assertEquals(20, tcpHeader.headerLength)
        assertEquals(20, tcpHeader.payloadOffset)
        assertTrue(tcpHeader.payloadLength(packet.size) > 0)
    }
}
