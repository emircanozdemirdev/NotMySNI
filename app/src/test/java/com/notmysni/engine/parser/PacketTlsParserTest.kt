package com.notmysni.engine.parser

import com.notmysni.testutil.PacketFixtures
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class PacketTlsParserTest {

    @Test
    fun parseClientHello_fromFullIpv4TcpPacket() {
        val tlsPayload = PacketFixtures.buildTlsClientHelloPayload("www.google.com")
        val packet = PacketFixtures.buildIpv4TcpPacket(
            sourceIp = PacketFixtures.ipAddress(10, 0, 0, 2),
            destinationIp = PacketFixtures.ipAddress(142, 250, 185, 78),
            sourcePort = 51234,
            destinationPort = 443,
            tcpPayload = tlsPayload
        )

        val result = PacketTlsParser.parseClientHello(packet, packet.size)

        assertNotNull(result)
        assertEquals("www.google.com", result!!.serverName)
    }
}
