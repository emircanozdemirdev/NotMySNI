package com.notmysni.dns

import com.notmysni.model.IpAddress
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.net.Socket
import java.util.concurrent.atomic.AtomicReference

/** Step 7.1 — DNS-over-HTTPS resolver */
class DohResolverTest {

    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun resolve_postsWireFormatQueryAndParsesIp() = runTest {
        val dnsResponse = buildARecordResponse(
            hostname = "example.com",
            ttl = 300,
            ip = byteArrayOf(93, -72, -40, 34)
        )
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .addHeader("Content-Type", "application/dns-message")
                .setBody(okio.Buffer().write(dnsResponse))
        )

        val resolver = DohResolver(
            httpClient = OkHttpClient(),
            endpointUrl = server.url("/dns-query").toString(),
            cache = DnsLruCache()
        )

        val result = resolver.resolve("example.com")

        assertTrue(result.isSuccess)
        assertEquals(listOf(IpAddress(byteArrayOf(93, -72, -40, 34))), result.getOrNull())

        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("application/dns-message", request.getHeader("Accept"))
        assertEquals("application/dns-message", request.headers["Content-Type"]?.substringBefore(";"))
        assertTrue(request.body.readByteArray().isNotEmpty())
    }

    @Test
    fun resolve_usesCacheOnSecondLookup() = runTest {
        val dnsResponse = buildARecordResponse(
            hostname = "cached.test",
            ttl = 300,
            ip = byteArrayOf(10, 0, 0, 1)
        )
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .addHeader("Content-Type", "application/dns-message")
                .setBody(okio.Buffer().write(dnsResponse))
        )

        val resolver = DohResolver(
            httpClient = OkHttpClient(),
            endpointUrl = server.url("/dns-query").toString(),
            cache = DnsLruCache()
        )

        val first = resolver.resolve("cached.test")
        val second = resolver.resolve("cached.test")

        assertEquals(first.getOrNull(), second.getOrNull())
        assertEquals(1, server.requestCount)
    }

    @Test
    fun resolve_httpError_returnsFailure() = runTest {
        server.enqueue(MockResponse().setResponseCode(503))

        val resolver = DohResolver(
            httpClient = OkHttpClient(),
            endpointUrl = server.url("/dns-query").toString()
        )

        val result = resolver.resolve("fail.test")

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is DohException)
    }

    @Test
    fun resolve_withSocketProtector_invokesProtector() = runTest {
        val dnsResponse = buildARecordResponse(
            hostname = "protect.test",
            ttl = 300,
            ip = byteArrayOf(1, 1, 1, 1)
        )
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .addHeader("Content-Type", "application/dns-message")
                .setBody(okio.Buffer().write(dnsResponse))
        )

        val protectedSocket = AtomicReference<Socket?>(null)
        val resolver = DohResolver(
            httpClient = OkHttpClient(),
            endpointUrl = server.url("/dns-query").toString(),
            socketProtector = { socket ->
                protectedSocket.set(socket)
                true
            }
        )

        val result = resolver.resolve("protect.test")

        assertTrue(result.isSuccess)
        assertNotNull(protectedSocket.get())
    }

    private fun buildARecordResponse(
        hostname: String,
        ttl: Int,
        ip: ByteArray
    ): ByteArray {
        val query = DnsWireFormat.encodeAQuery(hostname)
        val answer = ByteArray(16)
        answer[0] = 0xC0.toByte()
        answer[1] = 0x0C
        answer[2] = 0x00
        answer[3] = 0x01
        answer[4] = 0x00
        answer[5] = 0x01
        answer[6] = ((ttl shr 24) and 0xFF).toByte()
        answer[7] = ((ttl shr 16) and 0xFF).toByte()
        answer[8] = ((ttl shr 8) and 0xFF).toByte()
        answer[9] = (ttl and 0xFF).toByte()
        answer[10] = 0x00
        answer[11] = 0x04
        ip.copyInto(answer, 12)

        val header = query.copyOfRange(0, 12)
        header[2] = 0x81.toByte()
        header[3] = 0x80.toByte()
        header[6] = 0x00
        header[7] = 0x01

        return header + query.copyOfRange(12, query.size) + answer
    }
}
