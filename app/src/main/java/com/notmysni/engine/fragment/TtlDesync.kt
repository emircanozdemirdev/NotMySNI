package com.notmysni.engine.fragment

import com.notmysni.engine.forward.IpChecksum

/**
 * Step 6.2 — TTL-based desync technique
 */
object TtlDesync {

    /** IPv4 header TTL field offset. */
    const val IPV4_TTL_OFFSET = 8

    data class DesyncPair(
        /** Low-TTL decoy; not intended to reach the remote endpoint. */
        val fakePacket: ByteArray,
        /** Real segment(s) with normal TTL for forwarding. */
        val realPackets: List<ByteArray>
    )

    /**
     * Builds fake + real packet list from [fragments] produced by [TcpFragmenter].
     * Returns null when desync is disabled, [fragments] is empty, or there is only one
     * segment (step 6.1 must split the payload before TTL desync applies).
     */
    fun apply(
        fragments: List<TcpFragmenter.TcpFragment>,
        config: TtlDesyncConfig
    ): List<ByteArray>? {
        val pair = applyPair(fragments, config) ?: return null
        return listOf(pair.fakePacket) + pair.realPackets
    }

    /**
     * Same as [apply] but returns structured fake/real buckets for callers that need both.
     */
    fun applyPair(
        fragments: List<TcpFragmenter.TcpFragment>,
        config: TtlDesyncConfig
    ): DesyncPair? {
        if (!config.enabled || fragments.size < 2) return null

        return DesyncPair(
            fakePacket = withTtl(fragments.first().packet, config.fakeSegmentTtl),
            realPackets = fragments.map { withTtl(it.packet, config.realSegmentTtl) }
        )
    }

    /** Sets IPv4 TTL (byte 8) and recomputes the IP header checksum. */
    fun withTtl(packet: ByteArray, ttl: Int): ByteArray {
        require(packet.size >= 20) { "IPv4 packet must be at least 20 bytes" }
        require(ttl in 1..255) { "ttl must be 1..255, got $ttl" }

        val copy = packet.copyOf()
        copy[IPV4_TTL_OFFSET] = ttl.toByte()
        IpChecksum.apply(copy, copy.size)
        return copy
    }
}
