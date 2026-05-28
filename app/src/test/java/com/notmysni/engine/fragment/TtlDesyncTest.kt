package com.notmysni.engine.fragment

import com.notmysni.testutil.PacketFixtures
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/** Step 6.2 — TTL-based desync technique */
class TtlDesyncTest {

    @Test
    fun apply_whenDisabled_returnsNull() {
        val payload = PacketFixtures.buildTlsClientHelloPayload("example.com")
        val packet = PacketFixtures.buildIpv4TcpPacket(
            sourceIp = PacketFixtures.ipAddress(10, 0, 0, 2),
            destinationIp = PacketFixtures.ipAddress(93, 184, 216, 34),
            sourcePort = 44124,
            destinationPort = 443,
            tcpPayload = payload
        )
        val fragments = TcpFragmenter.fragment(packet, packet.size, FragmentStrategy.TinyFirst(2))!!

        assertNull(TtlDesync.apply(fragments, TtlDesyncConfig(enabled = false)))
    }

    @Test
    fun withTtl_updatesByteAndChecksum() {
        val packet = PacketFixtures.buildIpv4TcpPacket(
            sourceIp = PacketFixtures.ipAddress(10, 0, 0, 2),
            destinationIp = PacketFixtures.ipAddress(93, 184, 216, 34),
            sourcePort = 44124,
            destinationPort = 443,
            tcpPayload = byteArrayOf(0x01, 0x02)
        )

        val ttl1 = TtlDesync.withTtl(packet, 1)
        assertEquals(1, ttl1[TtlDesync.IPV4_TTL_OFFSET].toInt() and 0xFF)
        assertIpv4HeaderChecksumValid(ttl1)

        val ttl64 = TtlDesync.withTtl(packet, 64)
        assertEquals(64, ttl64[TtlDesync.IPV4_TTL_OFFSET].toInt() and 0xFF)
        assertIpv4HeaderChecksumValid(ttl64)
    }

    @Test
    fun apply_whenEnabledAndSingleFragment_returnsNull() {
        val payload = byteArrayOf(0x16, 0x03, 0x01, 0x00, 0x01, 0x01)
        val packet = PacketFixtures.buildIpv4TcpPacket(
            sourceIp = PacketFixtures.ipAddress(10, 0, 0, 2),
            destinationIp = PacketFixtures.ipAddress(93, 184, 216, 34),
            sourcePort = 44124,
            destinationPort = 443,
            tcpPayload = payload
        )
        val fragments = TcpFragmenter.fragment(packet, packet.size, FragmentStrategy.SplitAtSni)!!
        val config = TtlDesyncConfig(enabled = true)

        assertEquals(1, fragments.size)
        assertNull(TtlDesync.apply(fragments, config))
    }

    @Test
    fun apply_whenEnabled_returnsFakeThenRealPackets() {
        val payload = PacketFixtures.buildTlsClientHelloPayload("example.com")
        val packet = PacketFixtures.buildIpv4TcpPacket(
            sourceIp = PacketFixtures.ipAddress(10, 0, 0, 2),
            destinationIp = PacketFixtures.ipAddress(93, 184, 216, 34),
            sourcePort = 44124,
            destinationPort = 443,
            tcpPayload = payload
        )
        val fragments = TcpFragmenter.fragment(packet, packet.size, FragmentStrategy.TinyFirst(2))!!
        val config = TtlDesyncConfig(enabled = true, fakeSegmentTtl = 1, realSegmentTtl = 64)

        val result = TtlDesync.apply(fragments, config)
        assertNotNull(result)
        assertEquals(fragments.size + 1, result!!.size)
        assertEquals(1, result[0][TtlDesync.IPV4_TTL_OFFSET].toInt() and 0xFF)
        result.drop(1).forEach { segment ->
            assertEquals(64, segment[TtlDesync.IPV4_TTL_OFFSET].toInt() and 0xFF)
        }
    }

    private fun assertIpv4HeaderChecksumValid(packet: ByteArray) {
        val stored = readUInt16(packet, 10)
        val computed = computeIpv4HeaderChecksum(packet)
        assertEquals(computed, stored)
    }

    private fun computeIpv4HeaderChecksum(packet: ByteArray): Int {
        val savedHigh = packet[10]
        val savedLow = packet[11]
        packet[10] = 0
        packet[11] = 0

        var sum = 0
        var i = 0
        while (i < 20) {
            sum += ((packet[i].toInt() and 0xFF) shl 8) or (packet[i + 1].toInt() and 0xFF)
            i += 2
        }
        while (sum shr 16 != 0) {
            sum = (sum and 0xFFFF) + (sum shr 16)
        }
        val checksum = sum.inv() and 0xFFFF

        packet[10] = savedHigh
        packet[11] = savedLow
        return checksum
    }

    private fun readUInt16(packet: ByteArray, offset: Int): Int =
        ((packet[offset].toInt() and 0xFF) shl 8) or (packet[offset + 1].toInt() and 0xFF)
}
