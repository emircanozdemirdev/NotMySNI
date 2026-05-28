package com.notmysni.engine.fragment

import com.notmysni.engine.parser.IpHeaderParser
import com.notmysni.engine.parser.TcpHeaderParser

/**
 * Step 6.2 — TTL-based desync technique
 *
 * Chains [TcpFragmenter] with [TtlDesync] and exposes outbound TCP payloads for the forwarder.
 */
object TcpDesyncPipeline {

    data class OutboundSegments(
        /** First-segment decoy payload sent once with a low IP TTL before the real stream. */
        val decoyPayload: ByteArray?,
        /** Fragment payloads delivered to the remote host in order (normal TTL). */
        val payloadsForRemote: List<ByteArray>
    )

    /**
     * Builds ordered outbound payloads from a captured IPv4/TCP packet.
     * When TTL desync applies: decoy duplicates the first fragment, then all fragment payloads follow.
     */
    fun prepareOutbound(
        packet: ByteArray,
        length: Int,
        fragmentStrategy: FragmentStrategy,
        ttlConfig: TtlDesyncConfig
    ): OutboundSegments? {
        val ipHeaderLength = IpHeaderParser.parse(packet, length)?.headerLength ?: return null

        val fragments = TcpFragmenter.fragment(packet, length, fragmentStrategy)
        if (fragments == null) {
            val payload = extractTcpPayload(packet, length, ipHeaderLength) ?: return null
            return OutboundSegments(decoyPayload = null, payloadsForRemote = listOf(payload))
        }

        val payloads = fragments.mapNotNull { fragment ->
            extractTcpPayload(fragment.packet, fragment.packet.size, ipHeaderLength)
        }
        if (payloads.isEmpty()) return null

        val desyncPackets = TtlDesync.apply(fragments, ttlConfig)
        if (desyncPackets == null) {
            return OutboundSegments(decoyPayload = null, payloadsForRemote = payloads)
        }

        return OutboundSegments(
            decoyPayload = payloads.first(),
            payloadsForRemote = payloads
        )
    }

    private fun extractTcpPayload(packet: ByteArray, length: Int, ipHeaderLength: Int): ByteArray? {
        val tcpHeader = TcpHeaderParser.parse(packet, length, ipHeaderLength) ?: return null
        val payloadLength = tcpHeader.payloadLength(length)
        if (payloadLength <= 0) return null
        return packet.copyOfRange(tcpHeader.payloadOffset, tcpHeader.payloadOffset + payloadLength)
    }
}
