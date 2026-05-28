package com.notmysni.engine.fragment

import com.notmysni.engine.parser.IpHeaderParser
import com.notmysni.engine.parser.PacketTlsParser
import com.notmysni.engine.parser.TcpHeaderParser
import com.notmysni.testutil.PacketFixtures
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TcpFragmenterTest {

    @Test
    fun fragment_splitAtSni_splitsBeforeHostname() {
        val payload = PacketFixtures.buildTlsClientHelloPayload("example.com")
        val parsed = PacketTlsParser.parseClientHello(
            buildPacket(payload, sequenceNumber = 1_000L),
            buildPacket(payload, sequenceNumber = 1_000L).size
        )!!

        val packet = buildPacket(payload, sequenceNumber = 1_000L)
        val fragments = TcpFragmenter.fragment(packet, packet.size, FragmentStrategy.SplitAtSni)!!

        assertEquals(2, fragments.size)
        assertEquals(1_000L, fragments[0].sequenceNumber)
        assertEquals(
            1_000L + fragments[0].payloadLength,
            fragments[1].sequenceNumber
        )

        val splitAt = parsed.hostnameOffset - 20
        assertEquals(splitAt, fragments[0].payloadLength)
        assertEquals(payload.size - splitAt, fragments[1].payloadLength)
        assertReassembledPayloadEquals(payload, fragments)
    }

    @Test
    fun fragment_tinyFirst_splitsIntoTinyHeadAndRemainder() {
        val payload = PacketFixtures.buildTlsClientHelloPayload("example.com")
        val packet = buildPacket(payload, sequenceNumber = 2_000L)

        val fragments = TcpFragmenter.fragment(
            packet,
            packet.size,
            FragmentStrategy.TinyFirst(firstSegmentSize = 3)
        )!!

        assertEquals(2, fragments.size)
        assertEquals(2_000L, fragments[0].sequenceNumber)
        assertEquals(2_003L, fragments[1].sequenceNumber)
        assertEquals(3, fragments[0].payloadLength)
        assertEquals(payload.size - 3, fragments[1].payloadLength)
        assertReassembledPayloadEquals(payload, fragments)
    }

    @Test
    fun fragment_multiSplit_dividesIntoRequestedChunks() {
        val payload = PacketFixtures.buildTlsClientHelloPayload("example.com")
        val packet = buildPacket(payload, sequenceNumber = 5_000L)
        val chunkCount = 4

        val fragments = TcpFragmenter.fragment(
            packet,
            packet.size,
            FragmentStrategy.MultiSplit(chunkCount)
        )!!

        assertEquals(chunkCount, fragments.size)
        assertEquals(5_000L, fragments[0].sequenceNumber)

        var expectedSeq = 5_000L
        fragments.forEach { fragment ->
            assertEquals(expectedSeq, fragment.sequenceNumber)
            expectedSeq += fragment.payloadLength
        }

        assertEquals(payload.size, fragments.sumOf { it.payloadLength })
        assertReassembledPayloadEquals(payload, fragments)
    }

    @Test
    fun fragment_returnsSinglePacketWhenSplitPointIsInvalid() {
        val payload = byteArrayOf(0x16, 0x03, 0x01, 0x00, 0x01, 0x01)
        val packet = buildPacket(payload, sequenceNumber = 100L)

        val fragments = TcpFragmenter.fragment(packet, packet.size, FragmentStrategy.SplitAtSni)!!

        assertEquals(1, fragments.size)
        assertEquals(100L, fragments[0].sequenceNumber)
        assertEquals(payload.size, fragments[0].payloadLength)
    }

    @Test
    fun fragment_returnsNullForNonTcpPacket() {
        val packet = ByteArray(20)
        packet[0] = 0x45
        packet[9] = 17

        assertNull(TcpFragmenter.fragment(packet, packet.size, FragmentStrategy.TinyFirst()))
    }

    @Test
    fun fragment_setsPshOnlyOnLastSegment() {
        val payload = PacketFixtures.buildTlsClientHelloPayload("example.com")
        val packet = buildPacket(payload, sequenceNumber = 9_000L)
        packet[33] = 0x18

        val fragments = TcpFragmenter.fragment(
            packet,
            packet.size,
            FragmentStrategy.TinyFirst(firstSegmentSize = 2)
        )!!

        assertEquals(2, fragments.size)
        assertEquals(0x10, fragments[0].packet[33].toInt() and 0xFF)
        assertEquals(0x18, fragments[1].packet[33].toInt() and 0xFF)
    }

    private fun buildPacket(payload: ByteArray, sequenceNumber: Long): ByteArray =
        PacketFixtures.buildIpv4TcpPacket(
            sourceIp = PacketFixtures.ipAddress(10, 0, 0, 2),
            destinationIp = PacketFixtures.ipAddress(93, 184, 216, 34),
            sourcePort = 44124,
            destinationPort = 443,
            tcpPayload = payload,
            sequenceNumber = sequenceNumber
        )

    private fun assertReassembledPayloadEquals(
        originalPayload: ByteArray,
        fragments: List<TcpFragmenter.TcpFragment>
    ) {
        val reassembled = ByteArray(originalPayload.size)
        var offset = 0
        fragments.forEach { fragment ->
            val ipHeader = IpHeaderParser.parse(fragment.packet, fragment.packet.size)!!
            val tcpHeader = TcpHeaderParser.parse(
                fragment.packet,
                fragment.packet.size,
                ipHeader.headerLength
            )!!
            val payloadLength = tcpHeader.payloadLength(fragment.packet.size)
            fragment.packet.copyInto(
                reassembled,
                offset,
                tcpHeader.payloadOffset,
                tcpHeader.payloadOffset + payloadLength
            )
            offset += payloadLength
        }
        assertArrayEquals(originalPayload, reassembled)
    }
}
