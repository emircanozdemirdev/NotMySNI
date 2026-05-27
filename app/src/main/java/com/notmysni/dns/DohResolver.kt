package com.notmysni.dns

import com.notmysni.model.IpAddress
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.net.InetAddress
import java.net.Socket
import javax.net.SocketFactory

/**
 * Step 7.1 — DNS-over-HTTPS resolver
 *
 * Builds RFC 1035 wire-format queries, POSTs to a DoH provider, parses A records,
 * and caches results with TTL-based LRU eviction.
 */
class DohResolver(
    private val httpClient: OkHttpClient = OkHttpClient(),
    provider: DohProvider = DohProvider.CLOUDFLARE,
    private val endpointUrl: String = provider.endpointUrl,
    socketProtector: ((Socket) -> Boolean)? = null,
    private val cache: DnsLruCache = DnsLruCache()
) {
    private val protectedClient: OkHttpClient = socketProtector?.let { protector ->
        httpClient.newBuilder()
            .socketFactory(ProtectingSocketFactory(SocketFactory.getDefault(), protector))
            .build()
    } ?: httpClient


    suspend fun resolve(hostname: String): Result<List<IpAddress>> = withContext(Dispatchers.IO) {
        val normalized = normalizeHostname(hostname)
        if (normalized.isEmpty()) {
            return@withContext Result.failure(DohException("Empty hostname"))
        }

        cache.get(normalized)?.let { cached ->
            return@withContext Result.success(cached)
        }

        runCatching {
            val query = DnsWireFormat.encodeAQuery(normalized)
            val request = Request.Builder()
                .url(endpointUrl)
                .post(query.toRequestBody(DNS_MESSAGE_MEDIA_TYPE))
                .header("Accept", DNS_MESSAGE_MEDIA_TYPE.toString())
                .build()

            protectedClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw DohException("DoH request failed with HTTP ${response.code}")
                }

                val body = response.body?.bytes()
                    ?: throw DohException("DoH response body was empty")

                val answers = DnsWireFormat.parseARecords(body)
                if (answers.isEmpty()) {
                    throw DohException("No A records in DoH response for $normalized")
                }

                cache.put(normalized, answers)
                answers.map { IpAddress(it.ip) }
            }
        }
    }

    fun clearCache() {
        cache.clear()
    }

    private fun normalizeHostname(hostname: String): String =
        hostname.trim().trimEnd('.')

    companion object {
        private val DNS_MESSAGE_MEDIA_TYPE = "application/dns-message".toMediaType()
    }
}

private class ProtectingSocketFactory(
    private val delegate: SocketFactory,
    private val protector: (Socket) -> Boolean
) : SocketFactory() {

    override fun createSocket(): Socket = protect(delegate.createSocket())

    override fun createSocket(host: String?, port: Int): Socket =
        protect(delegate.createSocket(host, port))

    override fun createSocket(
        host: String?,
        port: Int,
        localHost: InetAddress?,
        localPort: Int
    ): Socket = protect(delegate.createSocket(host, port, localHost, localPort))

    override fun createSocket(host: InetAddress?, port: Int): Socket =
        protect(delegate.createSocket(host, port))

    override fun createSocket(
        address: InetAddress?,
        port: Int,
        localAddress: InetAddress?,
        localPort: Int
    ): Socket = protect(delegate.createSocket(address, port, localAddress, localPort))

    private fun protect(socket: Socket): Socket {
        protector(socket)
        return socket
    }
}
