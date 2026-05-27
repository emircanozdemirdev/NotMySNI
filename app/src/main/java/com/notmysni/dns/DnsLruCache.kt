package com.notmysni.dns

import com.notmysni.model.IpAddress

/**
 * Step 7.1 — DNS-over-HTTPS resolver
 *
 * TTL-aware LRU cache keyed by normalized hostname.
 */
class DnsLruCache(
    private val maxEntries: Int = 256,
    private val clock: () -> Long = { System.currentTimeMillis() }
) {

    private data class CacheEntry(
        val addresses: List<IpAddress>,
        val expiresAtMs: Long
    )

    private val entries = object : LinkedHashMap<String, CacheEntry>(maxEntries, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, CacheEntry>?): Boolean {
            return size > maxEntries
        }
    }

    @Synchronized
    fun get(hostname: String): List<IpAddress>? {
        val key = normalizeKey(hostname)
        val entry = entries[key] ?: return null
        if (clock() >= entry.expiresAtMs) {
            entries.remove(key)
            return null
        }
        return entry.addresses
    }

    @Synchronized
    fun put(hostname: String, answers: List<DnsAnswer>) {
        if (answers.isEmpty()) return

        val ttlSeconds = answers.minOf { it.ttl.coerceAtLeast(0) }
        val expiresAtMs = if (ttlSeconds == 0) {
            clock()
        } else {
            clock() + ttlSeconds * 1_000L
        }

        entries[normalizeKey(hostname)] = CacheEntry(
            addresses = answers.map { IpAddress(it.ip) },
            expiresAtMs = expiresAtMs
        )
    }

    @Synchronized
    fun clear() {
        entries.clear()
    }

    @Synchronized
    fun size(): Int = entries.size

    private fun normalizeKey(hostname: String): String =
        hostname.trim().trimEnd('.').lowercase()
}
