package com.notmysni.engine.fragment

import com.notmysni.testutil.PacketFixtures
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/** Step 6.2 — TTL-based desync technique */
class TcpDesyncPipelineTest {

    @Test
    fun prepareOutbound_whenDesyncEnabled_includesDecoyAndAllFragmentPayloads() {
        val payload = PacketFixtures.buildTlsClientHelloPayload("example.com")
        val packet = PacketFixtures.buildIpv4TcpPacket(
            sourceIp = PacketFixtures.ipAddress(10, 0, 0, 2),
            destinationIp = PacketFixtures.ipAddress(93, 184, 216, 34),
            sourcePort = 44124,
            destinationPort = 443,
            tcpPayload = payload
        )

        val result = TcpDesyncPipeline.prepareOutbound(
            packet = packet,
            length = packet.size,
            fragmentStrategy = FragmentStrategy.TinyFirst(2),
            ttlConfig = TtlDesyncConfig(enabled = true)
        )

        assertNotNull(result)
        assertNotNull(result!!.decoyPayload)
        assertEquals(2, result.payloadsForRemote.size)
        assertArrayEquals(result.payloadsForRemote.first(), result.decoyPayload)
    }

    @Test
    fun prepareOutbound_whenDesyncDisabled_hasNoDecoy() {
        val payload = PacketFixtures.buildTlsClientHelloPayload("example.com")
        val packet = PacketFixtures.buildIpv4TcpPacket(
            sourceIp = PacketFixtures.ipAddress(10, 0, 0, 2),
            destinationIp = PacketFixtures.ipAddress(93, 184, 216, 34),
            sourcePort = 44124,
            destinationPort = 443,
            tcpPayload = payload
        )

        val result = TcpDesyncPipeline.prepareOutbound(
            packet = packet,
            length = packet.size,
            fragmentStrategy = FragmentStrategy.TinyFirst(2),
            ttlConfig = TtlDesyncConfig(enabled = false)
        )

        assertNotNull(result)
        assertNull(result!!.decoyPayload)
        assertEquals(2, result.payloadsForRemote.size)
    }

    @Test
    fun prepareOutbound_whenSingleFragment_hasNoDecoyEvenIfEnabled() {
        val payload = byteArrayOf(0x16, 0x03, 0x01, 0x00, 0x01, 0x01)
        val packet = PacketFixtures.buildIpv4TcpPacket(
            sourceIp = PacketFixtures.ipAddress(10, 0, 0, 2),
            destinationIp = PacketFixtures.ipAddress(93, 184, 216, 34),
            sourcePort = 44124,
            destinationPort = 443,
            tcpPayload = payload
        )

        val result = TcpDesyncPipeline.prepareOutbound(
            packet = packet,
            length = packet.size,
            fragmentStrategy = FragmentStrategy.SplitAtSni,
            ttlConfig = TtlDesyncConfig(enabled = true)
        )

        assertNotNull(result)
        assertNull(result!!.decoyPayload)
        assertEquals(1, result.payloadsForRemote.size)
    }
}
