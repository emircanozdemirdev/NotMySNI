package com.notmysni.dns

import com.notmysni.model.IpAddress
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Step 7.1 — DNS-over-HTTPS resolver */
class DnsLruCacheTest {

    @Test
    fun get_returnsCachedAddressesUntilTtlExpires() {
        var now = 1_000L
        val cache = DnsLruCache(clock = { now })
        val answers = listOf(DnsAnswer(byteArrayOf(1, 2, 3, 4), ttl = 60))

        cache.put("Example.COM.", answers)

        assertEquals(listOf(IpAddress(byteArrayOf(1, 2, 3, 4))), cache.get("example.com"))

        now += 59_999L
        assertEquals(listOf(IpAddress(byteArrayOf(1, 2, 3, 4))), cache.get("example.com"))

        now += 2L
        assertNull(cache.get("example.com"))
    }

    @Test
    fun put_usesMinimumTtlAcrossAnswers() {
        var now = 0L
        val cache = DnsLruCache(clock = { now })
        cache.put(
            "host.test",
            listOf(
                DnsAnswer(byteArrayOf(10, 0, 0, 1), ttl = 120),
                DnsAnswer(byteArrayOf(10, 0, 0, 2), ttl = 30)
            )
        )

        now += 30_000L
        assertNull(cache.get("host.test"))
    }

    @Test
    fun cache_evictsLeastRecentlyUsedWhenFull() {
        val cache = DnsLruCache(maxEntries = 2)
        cache.put("a.test", listOf(DnsAnswer(byteArrayOf(1, 1, 1, 1), ttl = 300)))
        cache.put("b.test", listOf(DnsAnswer(byteArrayOf(2, 2, 2, 2), ttl = 300)))

        cache.get("a.test")

        cache.put("c.test", listOf(DnsAnswer(byteArrayOf(3, 3, 3, 3), ttl = 300)))

        assertEquals(listOf(IpAddress(byteArrayOf(1, 1, 1, 1))), cache.get("a.test"))
        assertNull(cache.get("b.test"))
        assertEquals(listOf(IpAddress(byteArrayOf(3, 3, 3, 3))), cache.get("c.test"))
    }
}
