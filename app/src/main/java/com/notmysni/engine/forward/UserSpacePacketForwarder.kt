package com.notmysni.engine.forward

import com.notmysni.dns.DohResolver
import com.notmysni.engine.DpiEngineConfig
import com.notmysni.engine.fragment.TcpDesyncPipeline
import com.notmysni.engine.parser.IpHeaderParser
import com.notmysni.engine.parser.TcpHeaderParser
import com.notmysni.model.IpHeader
import com.notmysni.model.TransportProtocol
import com.notmysni.vpn.TunOutput
import com.notmysni.vpn.VpnProtector
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.channels.Channel
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.InetSocketAddress
import java.nio.ByteBuffer
import java.nio.channels.DatagramChannel
import java.nio.channels.SocketChannel
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Reads IPv4 packets from TUN, forwards TCP/UDP via protected sockets, writes replies to TUN.
 * Full routing and DPI logic will extend this in later phases.
 *
 * Step 6.2 — TTL-based desync technique
 */
class UserSpacePacketForwarder(
    private val scope: CoroutineScope,
    private val protector: VpnProtector,
    private val mtu: Int,
    private val dpiEngineConfig: DpiEngineConfig = DpiEngineConfig.Default,
    @Suppress("unused") private val dohResolver: DohResolver? = null
) {
    // Step 6.2 — TTL-based desync technique
    //
    // private fun sendWholePacketWithLowTtl(packet: ByteArray, length: Int, tunOut: TunOutput) {
    //     val copy = packet.copyOf(length)
    //     copy[8] = 1
    //     tunOut.write(copy, length)
    // }

    private val connectionTable = ConnectionTable()
    private val tcpSessions = ConcurrentHashMap<ConnectionKey, TcpSession>()
    private val udpChannels = ConcurrentHashMap<ConnectionKey, DatagramChannel>()

    private var readJob: Job? = null

    fun start(tunInput: FileInputStream, tunOutputStream: FileOutputStream) {
        val tunOut = TunOutput(tunOutputStream)
        readJob = scope.launch(Dispatchers.IO) {
            val packetBuffer = ByteBuffer.allocate(mtu)
            val packet = ByteArray(mtu)
            while (isActive) {
                packetBuffer.clear()
                val read = tunInput.read(packet)
                if (read <= 0) continue
                packetBuffer.put(packet, 0, read)
                packetBuffer.flip()
                handlePacket(packet, read, tunOut)
            }
        }
    }

    fun stop() {
        readJob?.cancel()
        readJob = null
        tcpSessions.values.forEach { it.close() }
        tcpSessions.clear()
        connectionTable.values().forEach { conn ->
            runCatching { conn.channel.close() }
        }
        connectionTable.clear()
        udpChannels.values.forEach { channel ->
            runCatching { channel.close() }
        }
        udpChannels.clear()
    }

    private fun handlePacket(packet: ByteArray, length: Int, tunOut: TunOutput) {
        val ipHeader = IpHeaderParser.parse(packet, length) ?: return
        when (ipHeader.protocol) {
            TransportProtocol.TCP -> handleTcp(packet, length, ipHeader, tunOut)
            TransportProtocol.UDP -> handleUdp(packet, length, ipHeader, tunOut)
        }
    }

    private fun handleTcp(
        packet: ByteArray,
        length: Int,
        ipHeader: IpHeader,
        tunOut: TunOutput
    ) {
        val tcpHeader = TcpHeaderParser.parse(packet, length, ipHeader.headerLength) ?: return

        val srcIp = ipHeader.sourceAddress.bytes
        val dstIp = ipHeader.destinationAddress.bytes
        val key = ConnectionKey(
            srcIp,
            tcpHeader.sourcePort,
            dstIp,
            tcpHeader.destinationPort
        )

        if (tcpHeader.flags.fin || tcpHeader.flags.rst) {
            tcpSessions.remove(key)?.close(sendFinToRemote = tcpHeader.flags.fin)
            connectionTable.remove(key)?.let { runCatching { it.channel.close() } }
            return
        }

        var session = tcpSessions[key]
        if (session == null) {
            if (!tcpHeader.flags.syn) return
            val destinationHost = ipHeader.destinationAddress.toDisplayString()
            val connection = createConnection(key, destinationHost, tcpHeader.destinationPort) ?: return
            session = TcpSession(
                scope = scope,
                connection = connection,
                tunOut = tunOut,
                onClosed = {
                    tcpSessions.remove(key)
                    connectionTable.remove(key)?.let { runCatching { it.channel.close() } }
                }
            ).also { newSession ->
                tcpSessions[key] = newSession
                newSession.startRemoteToTun()
            }
        }

        val payloadLength = tcpHeader.payloadLength(length)
        if (payloadLength > 0) {
            session.enqueueToRemote(packet, length)
        }
    }

    private fun handleUdp(
        packet: ByteArray,
        length: Int,
        ipHeader: IpHeader,
        tunOut: TunOutput
    ) {
        val udpHeaderLength = 8
        val payloadOffset = ipHeader.headerLength + udpHeaderLength
        if (payloadOffset >= length) return

        val payloadLength = length - payloadOffset
        if (payloadLength <= 0) return

        val srcIp = ipHeader.sourceAddress.bytes
        val dstIp = ipHeader.destinationAddress.bytes
        val srcPort = readUInt16(packet, ipHeader.headerLength)
        val dstPort = readUInt16(packet, ipHeader.headerLength + 2)

        val key = ConnectionKey(srcIp, srcPort, dstIp, dstPort)
        scope.launch(Dispatchers.IO) {
            try {
                val channel = udpChannels.getOrPut(key) {
                    DatagramChannel.open().also { ch ->
                        ch.configureBlocking(true)
                        protector.protect(ch)
                    }
                }
                val dstAddress = InetSocketAddress(
                    ipHeader.destinationAddress.toDisplayString(),
                    dstPort
                )
                val sendBuffer = ByteBuffer.wrap(packet, payloadOffset, payloadLength)
                channel.send(sendBuffer, dstAddress)

                val receiveBuffer = ByteBuffer.allocate(mtu)
                channel.receive(receiveBuffer) ?: return@launch
                receiveBuffer.flip()
                val responseLength = receiveBuffer.remaining()
                if (responseLength <= 0) return@launch
                val responsePayload = ByteArray(responseLength)
                receiveBuffer.get(responsePayload)
                val responsePacket = UdpResponseBuilder.build(
                    sourceIp = dstIp,
                    sourcePort = dstPort,
                    destinationIp = srcIp,
                    destinationPort = srcPort,
                    payload = responsePayload
                )
                tunOut.write(responsePacket, responsePacket.size)
            } catch (_: Exception) {
                udpChannels.remove(key)
            }
        }
    }

    private fun readUInt16(packet: ByteArray, offset: Int): Int =
        ((packet[offset].toInt() and 0xFF) shl 8) or (packet[offset + 1].toInt() and 0xFF)

    private fun createConnection(
        key: ConnectionKey,
        destinationHost: String,
        destinationPort: Int
    ): TcpConnection? {
        return try {
            val channel = SocketChannel.open()
            protector.protect(channel)
            channel.configureBlocking(true)
            channel.connect(InetSocketAddress(destinationHost, destinationPort))

            val newConnection = TcpConnection(
                key = key,
                destinationHost = destinationHost,
                destinationPort = destinationPort,
                channel = channel
            )
            val existing = connectionTable.putIfAbsent(newConnection)
            if (existing != null) {
                runCatching { channel.close() }
                existing
            } else {
                newConnection
            }
        } catch (_: Exception) {
            null
        }
    }

    private inner class TcpSession(
        private val scope: CoroutineScope,
        private val connection: TcpConnection,
        private val tunOut: TunOutput,
        private val onClosed: () -> Unit
    ) {
        private val closed = AtomicBoolean(false)
        private val outboundQueue = Channel<OutboundTcpWrite>(capacity = Channel.UNLIMITED)
        private val sessionScope = CoroutineScope(
            scope.coroutineContext +
                SupervisorJob() +
                Dispatchers.IO +
                CoroutineExceptionHandler { _, _ -> close() }
        )
        private var remoteToTunJob: Job? = null
        private var tunToRemoteJob: Job? = null

        fun startRemoteToTun() {
            val socketChannel = connection.channel

            remoteToTunJob = sessionScope.launch {
                val buffer = ByteArray(32768)
                val socket = socketChannel.socket().getInputStream()
                while (isActive) {
                    val read = socket.read(buffer)
                    if (read <= 0) break
                    val responsePacket = TcpResponseBuilder.build(
                        sourceIp = connection.key.destinationIp,
                        sourcePort = connection.key.destinationPort,
                        destinationIp = connection.key.sourceIp,
                        destinationPort = connection.key.sourcePort,
                        payload = buffer,
                        payloadLength = read
                    )
                    tunOut.write(responsePacket, responsePacket.size)
                }
                close()
            }

            tunToRemoteJob = sessionScope.launch {
                val ttlConfig = dpiEngineConfig.ttlDesync
                for (outbound in outboundQueue) {
                    outbound.decoyPayload?.let { decoy ->
                        IpTtl.setSocketTtl(socketChannel.socket(), ttlConfig.fakeSegmentTtl)
                        socketChannel.write(ByteBuffer.wrap(decoy))
                    }

                    IpTtl.setSocketTtl(socketChannel.socket(), ttlConfig.realSegmentTtl)
                    for (payload in outbound.payloads) {
                        socketChannel.write(ByteBuffer.wrap(payload))
                    }
                }
            }
        }

        fun enqueueToRemote(packet: ByteArray, length: Int) {
            if (closed.get()) return

            val segments = TcpDesyncPipeline.prepareOutbound(
                packet = packet,
                length = length,
                fragmentStrategy = dpiEngineConfig.fragmentStrategy,
                ttlConfig = dpiEngineConfig.ttlDesync
            ) ?: return

            val queued = outboundQueue.trySend(
                OutboundTcpWrite(
                    decoyPayload = segments.decoyPayload,
                    payloads = segments.payloadsForRemote
                )
            )
            if (queued.isFailure) {
                close()
            }
        }

        fun close(sendFinToRemote: Boolean = false) {
            if (!closed.compareAndSet(false, true)) return

            if (sendFinToRemote) {
                runCatching { connection.channel.socket().shutdownOutput() }
            }
            outboundQueue.close()
            tunToRemoteJob?.cancel()
            remoteToTunJob?.cancel()
            sessionScope.cancel()
            runCatching { connection.channel.close() }
            onClosed()
        }
    }

    private data class OutboundTcpWrite(
        val decoyPayload: ByteArray?,
        val payloads: List<ByteArray>
    )
}
