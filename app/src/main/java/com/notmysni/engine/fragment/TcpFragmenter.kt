package com.notmysni.engine.fragment

import com.notmysni.engine.forward.IpChecksum
import com.notmysni.engine.forward.TcpChecksum
import com.notmysni.engine.parser.IpHeaderParser
import com.notmysni.engine.parser.TcpHeaderParser
import com.notmysni.engine.parser.TlsClientHelloParser
import com.notmysni.model.TransportProtocol

/**
 * Splits a single IPv4/TCP packet into multiple segments with monotonically increasing
 * sequence numbers while preserving the original header fields.
 */
object TcpFragmenter {

    // Step 6.2 — TTL-based desync technique
    //
    // private fun buildFragmentPacketWithLowTtl(...): ByteArray {
    //     val packet = buildFragmentPacket(...)
    //     packet[8] = 1
    //     IpChecksum.apply(packet, packet.size)
    //     return packet
    // }

    data class TcpFragment(
        val packet: ByteArray,
        val sequenceNumber: Long,
        val payloadLength: Int
    )

    /**
     * Returns one or more TCP fragments for [packet], or null when the input is not a valid
     * IPv4/TCP packet with payload.
     */
    fun fragment(
        packet: ByteArray,
        length: Int,
        strategy: FragmentStrategy
    ): List<TcpFragment>? {
        val ipHeader = IpHeaderParser.parse(packet, length) ?: return null
        if (ipHeader.protocol != TransportProtocol.TCP) return null

        val tcpHeader = TcpHeaderParser.parse(packet, length, ipHeader.headerLength) ?: return null
        val payloadLength = tcpHeader.payloadLength(length)
        if (payloadLength <= 0) return null

        val segments = computeSegments(
            packet = packet,
            payloadOffset = tcpHeader.payloadOffset,
            payloadLength = payloadLength,
            strategy = strategy
        ) ?: return null

        if (segments.size <= 1) {
            return listOf(
                TcpFragment(
                    packet = packet.copyOf(length),
                    sequenceNumber = tcpHeader.sequenceNumber,
                    payloadLength = payloadLength
                )
            )
        }

        var sequenceNumber = tcpHeader.sequenceNumber
        return segments.mapIndexed { index, segment ->
            val isLast = index == segments.lastIndex
            val fragmentPacket = buildFragmentPacket(
                template = packet,
                ipHeaderLength = ipHeader.headerLength,
                tcpHeaderLength = tcpHeader.headerLength,
                tcpFlagsRaw = tcpHeader.flags.raw,
                sequenceNumber = sequenceNumber,
                absolutePayloadStart = tcpHeader.payloadOffset + segment.first,
                payloadLength = segment.count(),
                isLastFragment = isLast
            )
            val fragment = TcpFragment(
                packet = fragmentPacket,
                sequenceNumber = sequenceNumber,
                payloadLength = segment.count()
            )
            sequenceNumber = (sequenceNumber + segment.count()) and 0xFFFF_FFFFL
            fragment
        }
    }

    private data class SegmentRange(val first: Int, val lastExclusive: Int) {
        fun count(): Int = lastExclusive - first
    }

    private fun computeSegments(
        packet: ByteArray,
        payloadOffset: Int,
        payloadLength: Int,
        strategy: FragmentStrategy
    ): List<SegmentRange>? {
        val ranges = when (strategy) {
            FragmentStrategy.SplitAtSni -> splitAtSniRanges(packet, payloadOffset, payloadLength)
            is FragmentStrategy.TinyFirst -> tinyFirstRanges(payloadLength, strategy.firstSegmentSize)
            is FragmentStrategy.MultiSplit -> multiSplitRanges(payloadLength, strategy.chunkCount)
        }
        return ranges.filter { it.count() > 0 }.takeIf { it.isNotEmpty() }
    }

    private fun splitAtSniRanges(
        packet: ByteArray,
        payloadOffset: Int,
        payloadLength: Int
    ): List<SegmentRange> {
        val parsed = TlsClientHelloParser.parse(packet, payloadOffset, payloadLength) ?: return singleRange(payloadLength)
        if (!parsed.hasServerName) return singleRange(payloadLength)

        val splitAt = parsed.hostnameOffset - payloadOffset
        if (splitAt <= 0 || splitAt >= payloadLength) return singleRange(payloadLength)
        return listOf(
            SegmentRange(0, splitAt),
            SegmentRange(splitAt, payloadLength)
        )
    }

    private fun tinyFirstRanges(payloadLength: Int, firstSegmentSize: Int): List<SegmentRange> {
        val firstSize = firstSegmentSize.coerceIn(1, 5).coerceAtMost(payloadLength)
        if (firstSize >= payloadLength) return singleRange(payloadLength)
        return listOf(
            SegmentRange(0, firstSize),
            SegmentRange(firstSize, payloadLength)
        )
    }

    private fun multiSplitRanges(payloadLength: Int, chunkCount: Int): List<SegmentRange> {
        val chunks = chunkCount.coerceAtLeast(2)
        if (payloadLength <= 1) return singleRange(payloadLength)

        val effectiveChunks = chunks.coerceAtMost(payloadLength)
        val baseSize = payloadLength / effectiveChunks
        val remainder = payloadLength % effectiveChunks

        val ranges = ArrayList<SegmentRange>(effectiveChunks)
        var start = 0
        repeat(effectiveChunks) { index ->
            val size = baseSize + if (index < remainder) 1 else 0
            ranges += SegmentRange(start, start + size)
            start += size
        }
        return ranges
    }

    private fun singleRange(payloadLength: Int): List<SegmentRange> =
        listOf(SegmentRange(0, payloadLength))

    private fun buildFragmentPacket(
        template: ByteArray,
        ipHeaderLength: Int,
        tcpHeaderLength: Int,
        tcpFlagsRaw: Int,
        sequenceNumber: Long,
        absolutePayloadStart: Int,
        payloadLength: Int,
        isLastFragment: Boolean
    ): ByteArray {
        val tcpLength = tcpHeaderLength + payloadLength
        val totalLength = ipHeaderLength + tcpLength
        val packet = ByteArray(totalLength)

        template.copyInto(packet, 0, 0, ipHeaderLength)
        writeUInt16(packet, 2, totalLength)

        template.copyInto(packet, ipHeaderLength, ipHeaderLength, ipHeaderLength + tcpHeaderLength)
        writeUInt32(packet, ipHeaderLength + 4, sequenceNumber)

        val flags = if (isLastFragment) {
            tcpFlagsRaw or PSH_FLAG
        } else {
            tcpFlagsRaw and PSH_FLAG.inv()
        }
        packet[ipHeaderLength + 13] = flags.toByte()

        template.copyInto(
            packet,
            ipHeaderLength + tcpHeaderLength,
            absolutePayloadStart,
            absolutePayloadStart + payloadLength
        )

        IpChecksum.apply(packet, totalLength)
        TcpChecksum.apply(packet, totalLength, ipHeaderLength, tcpLength)
        return packet
    }

    private const val PSH_FLAG = 0x08

    private fun writeUInt16(buffer: ByteArray, offset: Int, value: Int) {
        buffer[offset] = ((value shr 8) and 0xFF).toByte()
        buffer[offset + 1] = (value and 0xFF).toByte()
    }

    private fun writeUInt32(buffer: ByteArray, offset: Int, value: Long) {
        buffer[offset] = ((value shr 24) and 0xFF).toByte()
        buffer[offset + 1] = ((value shr 16) and 0xFF).toByte()
        buffer[offset + 2] = ((value shr 8) and 0xFF).toByte()
        buffer[offset + 3] = (value and 0xFF).toByte()
    }
}
