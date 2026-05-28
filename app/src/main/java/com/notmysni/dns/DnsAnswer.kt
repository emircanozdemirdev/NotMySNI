package com.notmysni.dns

/**
 * Step 7.1 — DNS-over-HTTPS resolver
 */
data class DnsAnswer(
    val ip: ByteArray,
    val ttl: Int
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is DnsAnswer) return false
        return ip.contentEquals(other.ip) && ttl == other.ttl
    }

    override fun hashCode(): Int = 31 * ip.contentHashCode() + ttl
}
